package com.devsphere.leafbloom.ui.scanner

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
            Toast.makeText(requireContext(), "No image provided for cropping", Toast.LENGTH_SHORT).show()
            findNavController().popBackStack()
            return
        }

        val uri = Uri.parse(imageUriString)

        // Setup CropImageView config programmatically
        binding.cropImageView.apply {
            setImageUriAsync(uri)
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
                            Toast.makeText(requireContext(), "Failed to save cropped image", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } else {
                Toast.makeText(requireContext(), "Crop failed: ${result.error?.message}", Toast.LENGTH_SHORT).show()
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
