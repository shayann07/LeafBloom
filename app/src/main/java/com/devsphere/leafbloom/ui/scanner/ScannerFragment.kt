package com.devsphere.leafbloom.ui.scanner

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.fragment.app.activityViewModels
import com.bumptech.glide.Glide
import com.devsphere.leafbloom.databinding.FragmentScannerBinding
import com.devsphere.leafbloom.ui.dialog.RationaleDialog
import com.devsphere.leafbloom.R
import com.devsphere.leafbloom.util.MediaHelper
import com.devsphere.leafbloom.util.PermissionManager

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Bitmap
import android.util.Log
// Ensure these imports are present
import com.devsphere.leafbloom.data.repository.IdentifyRepository
import com.devsphere.leafbloom.data.model.IdentifyResponse

class ScannerFragment : Fragment() {

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
                Toast.makeText(
                    requireContext(),
                    getString(R.string.camera_permission_required_to_scan),
                    Toast.LENGTH_SHORT
                ).show()
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
                                requireContext().contentResolver,
                                uri
                            )
                            android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                                decoder.isMutableRequired = true
                            }
                        } else {
                            MediaStore.Images.Media.getBitmap(
                                requireContext().contentResolver,
                                uri
                            )
                        }

                        // Fix Rotation
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                            requireContext().contentResolver.openInputStream(uri)
                                ?.use { input ->
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
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScannerBinding.inflate(inflater, container, false)
        return binding.root
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Get arguments
        scanMode = arguments?.getString("scan_mode") ?: "DIAGNOSE"

        // 2. Adjust UI Text based on Mode
        if (scanMode == "IDENTIFY") {
            binding.txtInstruction.text = "Snap a photo to Identify"
            binding.btnDiagnose.text = "Identify Plant"
        } else {
            binding.txtInstruction.text = getString(R.string.point_camera_at_plant)
            binding.btnDiagnose.text = "Diagnose Now"
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
        checkPermissions()
        observeViewModel()
        setupUI()
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
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
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
            iconResId = R.drawable.scan_icon, 
            onPositive = { requestPermissionLauncher.launch(permissionsToRequest) },
            onNegative = { findNavController().popBackStack() }
        ).show(childFragmentManager, RationaleDialog.TAG)
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.btnCapture.setOnClickListener { takePhoto() }
        binding.btnGallery.setOnClickListener {
            pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.btnFlip.setOnClickListener { flipCamera() }
        binding.btnRetake.setOnClickListener { showCameraUI() }

        binding.btnDiagnose.setOnClickListener {
            val uri = currentImageUri
            if (uri != null) {
                if (scanMode == "IDENTIFY") {
                    // Navigate to Result screen with URI
                    val bundle = Bundle().apply {
                        putString("image_uri", uri.toString())
                    }
                    findNavController().navigate(R.id.action_scannerFragment_to_identifyResultFragment, bundle)
                } else {
                    diagnoseImage(uri)
                }
            } else {
                Toast.makeText(requireContext(), getString(R.string.no_image_to_diagnose), Toast.LENGTH_SHORT).show()
            }
        }
        setupCameraGestures()
    }

    private fun setupCameraGestures() {
        val scaleGestureDetector = android.view.ScaleGestureDetector(requireContext(), object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
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
                    val action = androidx.camera.core.FocusMeteringAction.Builder(point, androidx.camera.core.FocusMeteringAction.FLAG_AF)
                        .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
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
             Toast.makeText(requireContext(), "Cannot identify leaf. Please try closer or better lighting.", Toast.LENGTH_LONG).show()
             return
        }
        if (result.confidence < 0.70f) {
            val msg = "Unsure (${(result.confidence * 100).toInt()}%). Please retry with a clearer plant image."
            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
            return
        }
        val sortedScores = result.scores.values.sortedDescending()
        if (sortedScores.size >= 2) {
            val top1 = sortedScores[0]
            val top2 = sortedScores[1]
            if ((top1 - top2) < 0.10f) {
                 val msg = "Ambiguous result. Too close to call. Please retry."
                 Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
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

    private fun diagnoseImage(uri: Uri?) {
        val targetBitmap = currentBitmap
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                var bitmap: Bitmap
                if (targetBitmap != null && uri == currentImageUri) {
                    bitmap = targetBitmap
                } else if (uri != null) {
                    bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val source = android.graphics.ImageDecoder.createSource(requireContext().contentResolver, uri)
                        android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ -> decoder.isMutableRequired = true }
                    } else {
                        MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
                    }
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                         val input = requireContext().contentResolver.openInputStream(uri)
                         val exif = input?.let { androidx.exifinterface.media.ExifInterface(it) }
                         val orientation = exif?.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL)
                         input?.close()
                         when (orientation) {
                             androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> bitmap = rotateBitmap(bitmap, 90f)
                             androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> bitmap = rotateBitmap(bitmap, 180f)
                             androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> bitmap = rotateBitmap(bitmap, 270f)
                         }
                    }
                } else {
                    return@launch
                }
        // ... (Diagnose logic - truncated for brevity but full code in real write)
                
                val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
                withContext(Dispatchers.Main) {
                   if (targetBitmap == null) {
                        binding.imgCapturedPreview.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                        binding.imgCapturedPreview.setBackgroundColor(android.graphics.Color.BLACK) 
                        binding.imgCapturedPreview.setImageBitmap(resizedBitmap)
                   }
                    val inputBitmap = resizedBitmap.copy(Bitmap.Config.ARGB_8888, true)
                    viewModel.analyzeImage(inputBitmap)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                   Toast.makeText(requireContext(), "Error process image: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun startScanningAnimation() {
        if (scanAnimator?.isRunning == true) return
        binding.scannerLine.visibility = View.VISIBLE
        binding.scannerLine.post {
            val containerHeight = binding.scannerFrameContainer.height.toFloat()
            val lineHeight = binding.scannerLine.height.toFloat()
            val endY = if (containerHeight > 0) containerHeight else 500f
            scanAnimator = ObjectAnimator.ofFloat(binding.scannerLine, "translationY", -lineHeight, endY).apply {
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
            } catch (e: Exception) {}
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(binding.previewView.surfaceProvider) }
        imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            showCameraUI()
        } catch (exc: Exception) {}
    }

    private fun flipCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        bindCameraUseCases()
    }
    
    private var currentBitmap: Bitmap? = null 

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        binding.previewView.post {
            binding.previewView.alpha = 0.5f
            binding.previewView.animate().alpha(1.0f).setDuration(100).start()
        }
        imageCapture.takePicture(
            cameraExecutor, 
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onError(exc: ImageCaptureException) {
                    activity?.runOnUiThread {
                         Toast.makeText(requireContext(), "Capture failed: ${exc.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class) 
                override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                    try {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        var bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        val rotationDegrees = image.imageInfo.rotationDegrees
                        if (rotationDegrees != 0) {
                            val matrix = android.graphics.Matrix()
                            matrix.postRotate(rotationDegrees.toFloat())
                            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                        }
                        image.close()
                        currentBitmap = bitmap
                        activity?.runOnUiThread {
                            showPreviewUI(null)
                            binding.imgCapturedPreview.setImageBitmap(bitmap)
                        }
                        try {
                             val savedUri = MediaHelper.saveImageToGallery(requireContext(), bitmap)
                             activity?.runOnUiThread { currentImageUri = savedUri }
                        } catch (e: Exception) {}
                    } catch (e: Exception) {
                         image.close()
                    }
                }
            }
        )
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
        startScanningAnimation()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                if (scanMode == "DIAGNOSE") {
                    when (state) {
                        is ScannerUiState.Idle -> {
                            binding.btnDiagnose.isEnabled = true
                            binding.btnDiagnose.text = "Diagnose Now"
                        }
                        is ScannerUiState.Loading -> {
                            binding.btnDiagnose.isEnabled = false
                            binding.btnDiagnose.text = "Diagnosing..."
                        }
                        is ScannerUiState.Success -> {
                            binding.btnDiagnose.isEnabled = true
                            binding.btnDiagnose.text = "Diagnose Now"
                            triggerDiagnoseResult(state.result)
                            viewModel.resetState() 
                        }
                        is ScannerUiState.Error -> {
                            binding.btnDiagnose.isEnabled = true
                            binding.btnDiagnose.text = "Diagnose Now"
                            Toast.makeText(requireContext(), "Error: ${state.message}", Toast.LENGTH_SHORT).show()
                            viewModel.resetState()
                            stopScanningAnimation()
                        }
                    }
                }
            }
        }
        lifecycleScope.launch {
            viewModel.latestGalleryUri.collect { uri ->
                 if (uri != null) {
                      binding.imgGalleryThumbnail.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                      Glide.with(this@ScannerFragment)
                          .load(uri)
                          .placeholder(R.color.text_hint)
                          .into(binding.imgGalleryThumbnail)
                 }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scanAnimator?.cancel()
        cameraExecutor.shutdown()
        try { cameraProvider?.unbindAll() } catch (e: Exception) {}
        _binding = null
    }

    private fun rotateBitmap(source: Bitmap, angle: Float): Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }
}
