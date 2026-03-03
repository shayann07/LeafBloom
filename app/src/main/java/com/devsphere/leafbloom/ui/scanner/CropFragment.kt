package com.devsphere.leafbloom.ui.scanner

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import com.canhub.cropper.CropImageView
import com.devsphere.leafbloom.R
import com.devsphere.leafbloom.ui.common.BaseFragment
import com.devsphere.leafbloom.databinding.FragmentCropBinding
import java.io.File
import java.io.FileOutputStream

class CropFragment : BaseFragment() {

    private var _binding: FragmentCropBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCropBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val imageUriString = arguments?.getString("image_uri")
        if (imageUriString == null) {
            com.devsphere.leafbloom.util.SnackbarUtils.showSnackbar(
                requireView(), 
                "No image provided for cropping", 
                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT,
                com.devsphere.leafbloom.util.SnackbarUtils.Type.ERROR
            )
            findNavController().popBackStack()
            return
        }

        val uri = Uri.parse(imageUriString)

        // Setup CropImageView config programmatically
        binding.cropImageView.apply {
            setImageUriAsync(uri)
            
            // Fix purple progress bar by finding it internally and applying tint
            val progressBar = findViewById<android.widget.ProgressBar>(com.canhub.cropper.R.id.CropProgressBar)
            progressBar?.indeterminateTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.brand_green_primary)
            )
        }

        binding.cropImageView.setOnCropImageCompleteListener { _, result ->
            if (result.isSuccessful) {
                val croppedUri = result.uriContent
                if (croppedUri != null) {
                    findNavController().previousBackStackEntry?.savedStateHandle?.set("cropped_uri", croppedUri.toString())
                    findNavController().popBackStack()
                } else {
                    val bitmap = result.bitmap
                    if (bitmap != null) {
                        try {
                            val file = File(requireContext().cacheDir, "cropped_image_${System.currentTimeMillis()}.jpg")
                            val out = FileOutputStream(file)
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                            out.flush()
                            out.close()
                            
                            val savedUri = Uri.fromFile(file)
                            findNavController().previousBackStackEntry?.savedStateHandle?.set("cropped_uri", savedUri.toString())
                            findNavController().popBackStack()
                        } catch (e: Exception) {
                            e.printStackTrace()
                            com.devsphere.leafbloom.util.SnackbarUtils.showSnackbar(
                                requireView(), 
                                "Failed to save cropped image", 
                                com.google.android.material.snackbar.Snackbar.LENGTH_SHORT,
                                com.devsphere.leafbloom.util.SnackbarUtils.Type.ERROR
                            )
                        }
                    }
                }
            } else {
                com.devsphere.leafbloom.util.SnackbarUtils.showSnackbar(
                    requireView(), 
                    "Crop failed: ${result.error?.message}", 
                    com.google.android.material.snackbar.Snackbar.LENGTH_SHORT,
                    com.devsphere.leafbloom.util.SnackbarUtils.Type.ERROR
                )
            }
        }

        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnCrop.setOnClickListener {
            binding.cropImageView.croppedImageAsync()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
