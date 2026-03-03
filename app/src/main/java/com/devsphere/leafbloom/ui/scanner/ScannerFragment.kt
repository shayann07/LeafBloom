package com.devsphere.leafbloom.ui.scanner

// Ensure these imports are present
import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.devsphere.leafbloom.R
import com.devsphere.leafbloom.databinding.FragmentScannerBinding
import com.devsphere.leafbloom.ui.common.BaseFragment
import com.devsphere.leafbloom.ui.dialog.RationaleDialog
import com.devsphere.leafbloom.util.MediaHelper
import com.devsphere.leafbloom.util.PermissionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri

class ScannerFragment : BaseFragment() {

    private var _binding: FragmentScannerBinding? = null
    private val binding get() = _binding!!

    private lateinit var cameraExecutor: ExecutorService
    private var scanAnimator: ObjectAnimator? = null
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: androidx.camera.core.Camera? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK

    private var currentImageUri: Uri? = null

    // Determine Mode ("DIAGNOSE" vs "IDENTIFY")
    private var scanMode: String = "DIAGNOSE"

    private val viewModel: ScannerViewModel by activityViewModels {
        ScannerViewModel.Factory(requireActivity().application)
    }

    // Consolidated Permission Launcher
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false

            if (cameraGranted) {
                startCamera()
            } else {
                view?.let {
                    com.devsphere.leafbloom.util.SnackbarUtils.showSnackbar(
                        it,
                        getString(R.string.camera_permission_required_to_scan),
                        com.google.android.material.snackbar.Snackbar.LENGTH_SHORT,
                        com.devsphere.leafbloom.util.SnackbarUtils.Type.ERROR
                    )
                }
                findNavController().popBackStack()
            }

            // Check storage grants for gallery thumbnail
            val readImagesGranted = permissions[Manifest.permission.READ_MEDIA_IMAGES] == true
            val readExternalGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true

            if (readImagesGranted || readExternalGranted) {
                // MVVM Pattern: Ask ViewModel to fetch data
                viewModel.loadLatestGalleryImage(requireContext().contentResolver)
            }
        }

    // Photo Picker Launcher
    private val pickMediaLauncher =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                showPreviewUI(uri)
                // PRE-LOAD OPTIMIZATION: Decode immediately so "Diagnose" is instant
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        var bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            val source = android.graphics.ImageDecoder.createSource(
                                requireContext().contentResolver, uri
                            )
                            android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                                decoder.isMutableRequired = true
                            }
                        } else {
                            MediaStore.Images.Media.getBitmap(
                                requireContext().contentResolver, uri
                            )
                        }

                        // Fix Rotation
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                                val exif = androidx.exifinterface.media.ExifInterface(input)
                                val orientation = exif.getAttributeInt(
                                    androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                                    androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                                )
                                when (orientation) {
                                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> bitmap =
                                        rotateBitmap(bitmap, 90f)

                                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> bitmap =
                                        rotateBitmap(bitmap, 180f)

                                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> bitmap =
                                        rotateBitmap(bitmap, 270f)
                                }
                            }
                        }
                        currentBitmap = bitmap
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScannerBinding.inflate(inflater, container, false)
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Get arguments
        scanMode = arguments?.getString("scan_mode") ?: "DIAGNOSE"

        // 2. Adjust UI Text based on Mode (lightweight, runs immediately)
        when (scanMode) {
            "IDENTIFY" -> {
                binding.txtInstruction.text = "Snap a photo to Identify"
                binding.btnDiagnose.text = "Identify Plant"
            }
            "PEST" -> {
                binding.txtInstruction.text = getString(R.string.point_camera_at_pest)
                binding.btnDiagnose.text = getString(R.string.identify_pest)
            }
            "RIPENESS" -> {
                binding.txtInstruction.text = getString(R.string.point_camera_at_tomato)
                binding.btnDiagnose.text = getString(R.string.check_ripeness)
            }
            else -> {
                binding.txtInstruction.text = getString(R.string.point_camera_at_plant)
                binding.btnDiagnose.text = "Diagnose Now"
            }
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Lightweight: click listeners, observers — run immediately so buttons respond instantly
        setupUI()
        observeViewModel()
        observeCropResult()

        // Defer ONLY the heavy work (permissions check → camera init) until after first frame
        // so the navigation transition renders smoothly
        view.post {
            if (!isAdded) return@post
            checkPermissions()
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun checkPermissions() {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(
                requireContext(), it
            ) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isEmpty()) {
            startCamera()
            viewModel.loadLatestGalleryImage(requireContext().contentResolver)
        } else {
            val showRationale = notGranted.any {
                PermissionManager.shouldShowRationale(requireActivity(), it)
            }
            if (showRationale) {
                showPermissionRationale(notGranted.toTypedArray())
            } else {
                requestPermissionLauncher.launch(notGranted.toTypedArray())
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun showPermissionRationale(permissionsToRequest: Array<String>) {
        RationaleDialog(
            titleStr = getString(R.string.permissions_required),
            descriptionStr = getString(R.string.permissions_required_scanner_desc),
            iconResId = R.drawable.privacy_policy_icon,
            onPositive = { requestPermissionLauncher.launch(permissionsToRequest) },
            onNegative = { findNavController().popBackStack() }).show(
            childFragmentManager, RationaleDialog.TAG
        )
    }

    private fun observeCropResult() {
        findNavController().currentBackStackEntry?.savedStateHandle?.getLiveData<String>("cropped_uri")?.observe(viewLifecycleOwner) { uriString ->
            if (uriString != null) {
                val croppedUri = uriString.toUri()
                showPreviewUI(croppedUri)
                
                // Pre-load the cropped bitmap immediately
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            val source = android.graphics.ImageDecoder.createSource(requireContext().contentResolver, croppedUri)
                            android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ -> decoder.isMutableRequired = true }
                        } else {
                            MediaStore.Images.Media.getBitmap(requireContext().contentResolver, croppedUri)
                        }
                        withContext(Dispatchers.Main) {
                            currentBitmap = bitmap
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                findNavController().currentBackStackEntry?.savedStateHandle?.remove<String>("cropped_uri")
            }
        }
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.btnCapture.setOnClickListener { takePhoto() }
        binding.btnGallery.setOnClickListener {
            pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.btnFlip.setOnClickListener { flipCamera() }
        binding.btnRetake.setOnClickListener { showCameraUI() }
        
        binding.btnCrop.setOnClickListener {
            val uri = currentImageUri
            if (uri != null) {
                val bundle = Bundle().apply {
                    putString("image_uri", uri.toString())
                }
                findNavController().navigate(R.id.action_scannerFragment_to_cropFragment, bundle)
            }
        }

        binding.btnDiagnose.setOnClickListener {
            val uri = currentImageUri
            if (uri != null) {
                when (scanMode) {
                    "IDENTIFY" -> viewModel.identifyPlant(uri)
                    else -> processAndAnalyzeImage(uri, scanMode)
                }
            } else {
                view?.let {
                    com.devsphere.leafbloom.util.SnackbarUtils.showSnackbar(
                        it, 
                        getString(R.string.no_image_to_diagnose), 
                        com.google.android.material.snackbar.Snackbar.LENGTH_SHORT,
                        com.devsphere.leafbloom.util.SnackbarUtils.Type.WARNING
                    )
                }
            }
        }
        setupCameraGestures()
    }

    private fun setupCameraGestures() {
        val scaleGestureDetector = android.view.ScaleGestureDetector(
            requireContext(),
            object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
                    val cam = camera ?: return false
                    val currentZoomRatio = cam.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                    val delta = detector.scaleFactor
                    cam.cameraControl.setZoomRatio(currentZoomRatio * delta)
                    return true
                }
            })

        binding.previewView.setOnTouchListener { view, event ->
            scaleGestureDetector.onTouchEvent(event)
            if (event.action == android.view.MotionEvent.ACTION_UP && !scaleGestureDetector.isInProgress) {
                val cam = camera
                if (cam != null) {
                    val factory = binding.previewView.meteringPointFactory
                    val point = factory.createPoint(event.x, event.y)
                    val action = androidx.camera.core.FocusMeteringAction.Builder(
                        point, androidx.camera.core.FocusMeteringAction.FLAG_AF
                    ).setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS).build()
                    cam.cameraControl.startFocusAndMetering(action)
                    view.performClick()
                }
            }
            true
        }
    }

    // --- IDENTIFY MODE LOGIC HANDLED VIA NAVIGATION ---

    // --- DIAGNOSE MODE LOGIC (EXISTING) ---
    private fun triggerDiagnoseResult(result: com.devsphere.leafbloom.data.model.PredictionResult) {
        if (result.predictedClass.equals("Unknown", ignoreCase = true)) {
            view?.let {
                com.devsphere.leafbloom.util.SnackbarUtils.showSnackbar(
                    it, 
                    "Cannot identify leaf. Please try closer or better lighting.", 
                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG,
                    com.devsphere.leafbloom.util.SnackbarUtils.Type.WARNING
                )
            }
            return
        }
        if (result.confidence < 0.70f) {
            val msg =
                "Unsure (${(result.confidence * 100).toInt()}%). Please retry with a clearer plant image."
            view?.let {
                com.devsphere.leafbloom.util.SnackbarUtils.showSnackbar(
                    it, 
                    msg, 
                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG,
                    com.devsphere.leafbloom.util.SnackbarUtils.Type.WARNING
                )
            }
            return
        }
        val sortedScores = result.scores.values.sortedDescending()
        if (sortedScores.size >= 2) {
            val top1 = sortedScores[0]
            val top2 = sortedScores[1]
            if ((top1 - top2) < 0.10f) {
                val msg = "Ambiguous result. Too close to call. Please retry."
                view?.let {
                    com.devsphere.leafbloom.util.SnackbarUtils.showSnackbar(
                        it, 
                        msg, 
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG,
                        com.devsphere.leafbloom.util.SnackbarUtils.Type.WARNING
                    )
                }
                return
            }
        }

        val bundle = Bundle().apply {
            putFloat("score_unknown", result.scores["Unknown"] ?: 0f)
            putFloat("score_early_blight", result.scores["Early Blight"] ?: 0f)
            putFloat("score_healthy", result.scores["Healthy"] ?: 0f)
            putFloat("score_late_blight", result.scores["Late Blight"] ?: 0f)
            putFloat("score_septoria", result.scores["Septoria"] ?: 0f)
            putString("predicted_class_name", result.predictedClass)
        }
        findNavController().navigate(R.id.action_scannerFragment_to_diagnoseResultFragment, bundle)
    }

    private fun triggerPestResult(result: com.devsphere.leafbloom.data.model.PredictionResult) {
        val bundle = Bundle().apply {
            putString("image_uri", currentImageUri?.toString())
            putString("predicted_class_name", result.predictedClass)
            putFloat("confidence", result.confidence)
        }
        findNavController().navigate(R.id.action_scannerFragment_to_pestResultFragment, bundle)
    }

    private fun triggerRipenessResult(result: com.devsphere.leafbloom.data.model.PredictionResult) {
        val bundle = Bundle().apply {
            putString("image_uri", currentImageUri?.toString())
            putFloat("score_ripe", result.scores["Ripe"] ?: 0f)
            putFloat("score_unknown", result.scores["Unknown"] ?: 0f)
            putFloat("score_unripe", result.scores["Unripe"] ?: 0f)
        }
        findNavController().navigate(R.id.action_scannerFragment_to_ripenessResultFragment, bundle)
    }

    private fun processAndAnalyzeImage(uri: Uri?, mode: String) {
        val targetBitmap = currentBitmap
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                var bitmap: Bitmap
                if (targetBitmap != null && uri == currentImageUri) {
                    bitmap = targetBitmap
                } else if (uri != null) {
                    bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val source = android.graphics.ImageDecoder.createSource(
                            requireContext().contentResolver, uri
                        )
                        android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                            decoder.isMutableRequired = true
                        }
                    } else {
                        MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
                    }
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                        val input = requireContext().contentResolver.openInputStream(uri)
                        val exif = input?.let { androidx.exifinterface.media.ExifInterface(it) }
                        val orientation = exif?.getAttributeInt(
                            androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                            androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                        )
                        input?.close()
                        when (orientation) {
                            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> bitmap =
                                rotateBitmap(bitmap, 90f)

                            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> bitmap =
                                rotateBitmap(bitmap, 180f)

                            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> bitmap =
                                rotateBitmap(bitmap, 270f)
                        }
                    }
                } else {
                    return@launch
                }
                // ... (Diagnose logic - truncated for brevity but full code in real write)

                val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
                withContext(Dispatchers.Main) {
                    if (targetBitmap == null) {
                        binding.imgCapturedPreview.scaleType =
                            android.widget.ImageView.ScaleType.FIT_CENTER
                        binding.imgCapturedPreview.setBackgroundColor(android.graphics.Color.BLACK)
                        binding.imgCapturedPreview.setImageBitmap(resizedBitmap)
                    }
                    val inputBitmap = resizedBitmap.copy(Bitmap.Config.ARGB_8888, true)
                    when (mode) {
                        "PEST" -> viewModel.analyzePest(inputBitmap)
                        "RIPENESS" -> viewModel.analyzeRipeness(inputBitmap)
                        else -> viewModel.analyzeImage(inputBitmap) // DIAGNOSE
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    view?.let {
                        com.devsphere.leafbloom.util.SnackbarUtils.showSnackbar(
                            it, 
                            "Error process image: ${e.message}", 
                            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT,
                            com.devsphere.leafbloom.util.SnackbarUtils.Type.ERROR
                        )
                    }
                }
            }
        }
    }

    private fun startScanningAnimation() {
        if (scanAnimator?.isRunning == true) return
        binding.scannerLine.visibility = View.VISIBLE
        // Enable hardware layer for smoother animation on low-end GPUs
        binding.scannerLine.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        binding.scannerLine.post {
            val containerHeight = binding.scannerFrameContainer.height.toFloat()
            val lineHeight = binding.scannerLine.height.toFloat()
            val endY = if (containerHeight > 0) containerHeight else 500f
            scanAnimator =
                ObjectAnimator.ofFloat(binding.scannerLine, "translationY", -lineHeight, endY)
                    .apply {
                        duration = 2000
                        repeatCount = ValueAnimator.INFINITE
                        repeatMode = ValueAnimator.REVERSE
                        start()
                    }
        }
    }

    private fun stopScanningAnimation() {
        scanAnimator?.cancel()
        binding.scannerLine.visibility = View.INVISIBLE
        // Revert to software layer when not animating to free GPU memory
        binding.scannerLine.setLayerType(View.LAYER_TYPE_NONE, null)
    }

    private fun startCamera() {
        if (cameraProvider != null) {
            bindCameraUseCases()
            return
        }
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            if (!isAdded) return@addListener
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (e: Exception) {
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return
        val preview = Preview.Builder().build()
            .also { it.setSurfaceProvider(binding.previewView.surfaceProvider) }
        imageCapture =
            ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            if (currentImageUri == null) {
                showCameraUI()
            }
        } catch (exc: Exception) {
        }
    }

    private fun flipCamera() {
        // Disable button immediately for visual feedback during camera rebind
        binding.btnFlip.isEnabled = false
        binding.btnFlip.alpha = 0.4f
        lensFacing =
            if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        bindCameraUseCases()
        // Re-enable after rebind completes
        binding.btnFlip.post {
            binding.btnFlip.isEnabled = true
            binding.btnFlip.alpha = 1.0f
        }
    }

    private var currentBitmap: Bitmap? = null

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        // Disable capture button to prevent double-taps
        binding.btnCapture.isEnabled = false
        binding.previewView.post {
            binding.previewView.alpha = 0.5f
            binding.previewView.animate().alpha(1.0f).setDuration(100).start()
        }
        imageCapture.takePicture(
            cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
                override fun onError(exc: ImageCaptureException) {
                    activity?.runOnUiThread {
                        binding.btnCapture.isEnabled = true
                        view?.let {
                            com.devsphere.leafbloom.util.SnackbarUtils.showSnackbar(
                                it, 
                                "Capture failed: ${exc.message}", 
                                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT,
                                com.devsphere.leafbloom.util.SnackbarUtils.Type.ERROR
                            )
                        }
                    }
                }

                @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
                override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                    try {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)

                        // Fast decode: get dimensions first, then decode at reduced resolution
                        val maxDim = 1024
                        val opts = android.graphics.BitmapFactory.Options()
                        opts.inJustDecodeBounds = true
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)

                        // Calculate inSampleSize to decode directly at ~1024px
                        val rawW = opts.outWidth
                        val rawH = opts.outHeight
                        var sampleSize = 1
                        while (rawW / (sampleSize * 2) >= maxDim && rawH / (sampleSize * 2) >= maxDim) {
                            sampleSize *= 2
                        }

                        opts.inJustDecodeBounds = false
                        opts.inSampleSize = sampleSize
                        var bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)!!

                        val rotationDegrees = image.imageInfo.rotationDegrees
                        if (rotationDegrees != 0) {
                            val matrix = android.graphics.Matrix()
                            matrix.postRotate(rotationDegrees.toFloat())
                            bitmap = Bitmap.createBitmap(
                                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                            )
                        }
                        image.close()

                        currentBitmap = bitmap

                        // Show preview immediately
                        activity?.runOnUiThread {
                            binding.btnCapture.isEnabled = true
                            showPreviewUI(null)
                            binding.imgCapturedPreview.setImageBitmap(bitmap)
                        }

                        // Offload gallery save: re-decode full-res in background for quality save
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                var fullBitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)!!
                                if (rotationDegrees != 0) {
                                    val m = android.graphics.Matrix()
                                    m.postRotate(rotationDegrees.toFloat())
                                    fullBitmap = Bitmap.createBitmap(fullBitmap, 0, 0, fullBitmap.width, fullBitmap.height, m, true)
                                }
                                val savedUri = MediaHelper.saveImageToGallery(requireContext(), fullBitmap)
                                fullBitmap.recycle()
                                withContext(Dispatchers.Main) { currentImageUri = savedUri }
                            } catch (_: Exception) { }
                        }
                    } catch (e: Exception) {
                        image.close()
                        activity?.runOnUiThread { binding.btnCapture.isEnabled = true }
                    }
                }
            })
    }

    private fun showPreviewUI(uri: Uri?) {
        currentImageUri = uri
        stopScanningAnimation()
        binding.scannerFrameContainer.visibility = View.GONE
        binding.txtInstruction.visibility = View.GONE
        binding.layoutCameraControls.visibility = View.GONE
        binding.previewView.visibility = View.GONE
        binding.imgCapturedPreview.visibility = View.VISIBLE
        binding.layoutPreviewActions.visibility = View.VISIBLE
        binding.btnCrop.visibility = View.VISIBLE
        if (uri != null) {
            Glide.with(this).load(uri).into(binding.imgCapturedPreview)
        }
    }

    private fun showCameraUI() {
        currentImageUri = null
        binding.scannerFrameContainer.visibility = View.VISIBLE
        binding.txtInstruction.visibility = View.VISIBLE
        binding.layoutCameraControls.visibility = View.VISIBLE
        binding.previewView.visibility = View.VISIBLE
        binding.imgCapturedPreview.visibility = View.GONE
        binding.layoutPreviewActions.visibility = View.GONE
        binding.btnCrop.visibility = View.GONE
        startScanningAnimation()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is ScannerUiState.Idle -> {
                        binding.btnDiagnose.isEnabled = true
                        binding.btnDiagnose.text = if (scanMode == "IDENTIFY") "Identify Plant" else "Diagnose Now"
                    }

                    is ScannerUiState.Loading -> {
                        binding.btnDiagnose.isEnabled = false
                        binding.btnDiagnose.text = if (scanMode == "IDENTIFY") "Validating..." else "Diagnosing..."
                    }

                    is ScannerUiState.SuccessDiagnosis -> {
                        if (scanMode == "DIAGNOSE") {
                            binding.btnDiagnose.isEnabled = true
                            binding.btnDiagnose.text = "Diagnose Now"
                            triggerDiagnoseResult(state.result)
                            viewModel.resetState()
                        }
                    }

                    is ScannerUiState.SuccessPest -> {
                        if (scanMode == "PEST") {
                            binding.btnDiagnose.isEnabled = true
                            binding.btnDiagnose.text = getString(R.string.identify_pest)
                            triggerPestResult(state.result)
                            viewModel.resetState()
                        }
                    }

                    is ScannerUiState.SuccessRipeness -> {
                        if (scanMode == "RIPENESS") {
                            binding.btnDiagnose.isEnabled = true
                            binding.btnDiagnose.text = getString(R.string.check_ripeness)
                            triggerRipenessResult(state.result)
                            viewModel.resetState()
                        }
                    }

                    is ScannerUiState.SuccessIdentify -> {
                        if (scanMode == "IDENTIFY") {
                            binding.btnDiagnose.isEnabled = true
                            binding.btnDiagnose.text = "Identify Plant"
                            val bundle = Bundle().apply {
                                putString("image_uri", state.imageUri.toString())
                                putParcelable("identify_response", state.response)
                            }
                            findNavController().navigate(
                                R.id.action_scannerFragment_to_identifyResultFragment, bundle
                            )
                            viewModel.resetState()
                        }
                    }

                    is ScannerUiState.Error -> {
                        binding.btnDiagnose.isEnabled = true
                        binding.btnDiagnose.text = if (scanMode == "IDENTIFY") "Identify Plant" else "Diagnose Now"
                        
                        if (state.message == "NOT_A_PLANT") {
                            view?.let {
                                com.devsphere.leafbloom.util.SnackbarUtils.showSnackbar(
                                    it, 
                                    "Cannot identify leaf. Please try closer or better lighting.", 
                                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG,
                                    com.devsphere.leafbloom.util.SnackbarUtils.Type.WARNING
                                )
                            }
                        } else {
                            view?.let {
                                com.devsphere.leafbloom.util.SnackbarUtils.showSnackbar(
                                    it, 
                                    "Error: ${state.message}", 
                                    com.google.android.material.snackbar.Snackbar.LENGTH_SHORT,
                                    com.devsphere.leafbloom.util.SnackbarUtils.Type.ERROR
                                )
                            }
                        }
                        
                        viewModel.resetState()
                        stopScanningAnimation()
                    }
                }
            }
        }
        
        lifecycleScope.launch {
            viewModel.latestGalleryUri.collect { uri ->
                if (uri != null) {
                    binding.imgGalleryThumbnail.scaleType =
                        android.widget.ImageView.ScaleType.CENTER_CROP
                    Glide.with(this@ScannerFragment).load(uri).placeholder(R.color.text_hint)
                        .into(binding.imgGalleryThumbnail)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scanAnimator?.cancel()
        cameraExecutor.shutdown()
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
        }
        _binding = null
    }

    private fun rotateBitmap(source: Bitmap, angle: Float): Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }
}
