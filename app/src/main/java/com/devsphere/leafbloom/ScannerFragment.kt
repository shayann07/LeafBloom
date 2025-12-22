package com.devsphere.leafbloom

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
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.devsphere.leafbloom.databinding.FragmentScannerBinding
import com.devsphere.leafbloom.ui.dialog.RationaleDialog
import com.devsphere.leafbloom.util.MediaHelper
import com.devsphere.leafbloom.util.PermissionManager
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScannerFragment : Fragment() {

    private var _binding: FragmentScannerBinding? = null
    private val binding get() = _binding!!

    private lateinit var cameraExecutor: ExecutorService
    private var scanAnimator: ObjectAnimator? = null
    private var imageCapture: ImageCapture? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK

    private var currentImageUri: Uri? = null

    // Consolidated Permission Launcher
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
            
            if (cameraGranted) {
                startCamera()
            } else {
                Toast.makeText(requireContext(), "Camera permission required to scan", Toast.LENGTH_SHORT).show()
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cameraExecutor = Executors.newSingleThreadExecutor()

        checkPermissions() // Replaces individual checks

        setupUI()
    }

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

    private fun showPermissionRationale(permissionsToRequest: Array<String>) {
        RationaleDialog(
            titleStr = "Permissions Required",
            descriptionStr = "LeafBloom needs Camera access to scan, and Storage access to save/load images.",
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
            if (currentImageUri != null) {
                Toast.makeText(requireContext(), "Proceeding to diagnosis...", Toast.LENGTH_SHORT).show()
                // TODO: Navigate to Analysis fragment
            } else {
                Toast.makeText(requireContext(), "No image to diagnose", Toast.LENGTH_SHORT).show()
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
            
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

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
            Toast.makeText(requireContext(), "Storage permission needed to save photo", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(requireContext(), "Capture failed: ${exc.message}", Toast.LENGTH_SHORT).show()
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
        
        binding.groupCameraUI.visibility = View.GONE
        binding.previewView.visibility = View.GONE 
        
        binding.imgCapturedPreview.visibility = View.VISIBLE
        binding.layoutPreviewActions.visibility = View.VISIBLE

        Glide.with(this)
            .load(uri)
            .into(binding.imgCapturedPreview)
    }

    private fun showCameraUI() {
        currentImageUri = null
        
        binding.groupCameraUI.visibility = View.VISIBLE
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
        _binding = null
    }
}
