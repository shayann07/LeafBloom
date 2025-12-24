package com.devsphere.leafbloom.ui.scanner

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ContentUris
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
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Bitmap
import android.util.Log

class ScannerFragment : Fragment() {

    private var _binding: FragmentScannerBinding? = null
    private val binding get() = _binding!!

    private lateinit var cameraExecutor: ExecutorService
    private var scanAnimator: ObjectAnimator? = null
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK

    private var currentImageUri: Uri? = null

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
                Toast.makeText(requireContext(), getString(R.string.camera_permission_required_to_scan), Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            }

            // Check storage grants for gallery thumbnail
            val readImagesGranted = permissions[Manifest.permission.READ_MEDIA_IMAGES] == true
            val readExternalGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
            
            if (readImagesGranted || readExternalGranted) {
                loadLatestGalleryImage()
            }
        }

    // Photo Picker Launcher
    private val pickMediaLauncher =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                showPreviewUI(uri)
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

        cameraExecutor = Executors.newSingleThreadExecutor()

        checkPermissions() // Replaces individual checks
        
        // ViewModel is already initialized by delegate
        
        observeViewModel()
        
        observeViewModel()

        setupUI()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun checkPermissions() {
        val permissions = mutableListOf(Manifest.permission.CAMERA)
        
        // For Gallery Thumbnail (Read)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        
        // For Save Image (Write) - Only needed for API <= 28 (Android 9)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isEmpty()) {
            startCamera()
            loadLatestGalleryImage()
        } else {
            // If any critical permission (Camera) needs rationale, show it.
            // We can be simpler: if ANY need rationale, show it.
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
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
        
        binding.btnCapture.setOnClickListener {
            takePhoto()
        }

        binding.btnGallery.setOnClickListener {
            pickMediaLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        binding.btnFlip.setOnClickListener {
            flipCamera()
        }

        binding.btnRetake.setOnClickListener {
            showCameraUI()
        }

        binding.btnDiagnose.setOnClickListener {
            val uri = currentImageUri
            if (uri != null) {
                diagnoseImage(uri)
            } else {
                Toast.makeText(requireContext(), getString(R.string.no_image_to_diagnose), Toast.LENGTH_SHORT).show()
            }
        }
    }



    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
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
                        handleSuccess(state.result)
                        viewModel.resetState() 
                    }
                    is ScannerUiState.Error -> {
                        binding.btnDiagnose.isEnabled = true
                        binding.btnDiagnose.text = "Diagnose Now"
                        Toast.makeText(requireContext(), "Error: ${state.message}", Toast.LENGTH_SHORT).show()
                        viewModel.resetState()
                    }
                }
            }
        }
    }

    private fun handleSuccess(result: com.devsphere.leafbloom.data.model.PredictionResult) {
        // block navigation if Unknown
        if (result.predictedClass.equals("Unknown", ignoreCase = true)) {
             Toast.makeText(requireContext(), "Cannot identify leaf. Please try closer or better lighting.", Toast.LENGTH_LONG).show()
             return
        }

        if (result.confidence < 0.50f) {
            val msg = "Result Unsure. Best: ${result.predictedClass} (${(result.confidence * 100).toInt()}%)"
            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
            return
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

    private fun diagnoseImage(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 0. Load Bitmap & Fix Rotation
                var bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = android.graphics.ImageDecoder.createSource(requireContext().contentResolver, uri)
                    android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                    }
                } else {
                    MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
                }
                
                // Fix Rotation (Legacy)
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

                // 1. Resize to 224x224 (SQUASHING logic)
                val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true)

                // 2. Show Preview
                withContext(Dispatchers.Main) {
                    binding.imgCapturedPreview.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                    binding.imgCapturedPreview.setBackgroundColor(android.graphics.Color.BLACK) 
                    binding.imgCapturedPreview.setImageBitmap(resizedBitmap)
                    
                     // 3. Ensure ARGB_8888 & Pass to ViewModel
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
            // Get dynamic heights
            val containerHeight = binding.scannerFrameContainer.height.toFloat()
            val lineHeight = binding.scannerLine.height.toFloat()
            
            // If view is not laid out yet, default to a safe estimation or simple 0-translation
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
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            // Check if fragment is attached before using context
            if (!isAdded) return@addListener
            
            if (!isAdded) return@addListener
            
            cameraProvider = cameraProviderFuture.get()
            val cameraProvider = cameraProvider ?: return@addListener

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
                
                showCameraUI()

            } catch (exc: Exception) {
               // Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun flipCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        startCamera()
    }
    
    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        // 1. Check Write Permission again for Safety on Legacy Devices?
        // Our 'checkPermissions' runs at start, but user might have denied it partially?
        // If critical permission missing, do not proceed.
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && 
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(requireContext(), getString(R.string.storage_permission_needed_to_save_photo), Toast.LENGTH_SHORT).show()
            // Could re-request here, but simple toast is safer loop wise
            return
        }

        // Save to cache first
        val photoFile = File(requireContext().cacheDir, "temp_capture_${System.currentTimeMillis()}.jpg")
        
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.capture_failed, exc.message ?: ""),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    if (!isAdded) return 

                    // Save to Gallery via our Helper
                    val bitmap = android.graphics.BitmapFactory.decodeFile(photoFile.absolutePath)
                    
                    // This might throw if permission not actually granted on legacy device
                     val savedUri = try {
                         MediaHelper.saveImageToGallery(requireContext(), bitmap)
                     } catch (e: Exception) {
                         null
                     }
                    
                    if (savedUri != null) {
                        showPreviewUI(savedUri)
                    } else {
                        // Fallback if gallery save fails
                        showPreviewUI(Uri.fromFile(photoFile))
                    }
                }
            }
        )
    }

    private fun showPreviewUI(uri: Uri) {
        currentImageUri = uri
        stopScanningAnimation()

        binding.scannerFrameContainer.visibility = View.GONE
        binding.txtInstruction.visibility = View.GONE
        binding.layoutCameraControls.visibility = View.GONE

        binding.previewView.visibility = View.GONE

        binding.imgCapturedPreview.visibility = View.VISIBLE
        binding.layoutPreviewActions.visibility = View.VISIBLE

        Glide.with(this)
            .load(uri)
            .into(binding.imgCapturedPreview)
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

    private fun loadLatestGalleryImage() {
        // Double check read permission before querying
        val readPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) 
            Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
            
        if (ContextCompat.checkSelfPermission(requireContext(), readPermission) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val projection = arrayOf(
            MediaStore.Images.ImageColumns._ID,
            MediaStore.Images.ImageColumns.DATE_TAKEN
        )
        
        val sortOrder = "${MediaStore.Images.ImageColumns.DATE_TAKEN} DESC"

        try {
            requireContext().contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.ImageColumns._ID)
                    val id = cursor.getLong(idColumn)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                    binding.imgGalleryThumbnail.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                    Glide.with(this)
                        .load(contentUri)
                        .placeholder(R.color.text_hint)
                        .into(binding.imgGalleryThumbnail)
                }
                }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scanAnimator?.cancel()
        cameraExecutor.shutdown()
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            // Ignore unbind errors
        }
        _binding = null
    }

    private fun rotateBitmap(source: Bitmap, angle: Float): Bitmap {
        android.util.Log.d("ScannerFragment", "Rotating bitmap by angle: $angle")
        val matrix = android.graphics.Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }
}
