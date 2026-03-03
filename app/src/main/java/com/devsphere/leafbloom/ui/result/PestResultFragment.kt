package com.devsphere.leafbloom.ui.result

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.devsphere.leafbloom.databinding.FragmentPestResultBinding
import com.devsphere.leafbloom.ui.common.BaseFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior

class PestResultFragment : BaseFragment() {
    private var _binding: FragmentPestResultBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPestResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Adaptive Header
        setupAdaptiveHeader(binding.headerContainer, binding.ivHeader)
        
        setupUI()
        
        val uriString = arguments?.getString("image_uri")
        val predictedName = arguments?.getString("predicted_class_name", getString(com.devsphere.leafbloom.R.string.unknown_pest))
        val confidenceVal = arguments?.getFloat("confidence", 0f) ?: 0f

        if (uriString != null) {
            val uri = Uri.parse(uriString)
            Glide.with(this).load(uri).into(binding.ivHeader)
        }
        
        displayResult(predictedName!!, confidenceVal)
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        // Setup Sticky Bottom Sheet (Rubber Band Effect)
        val bottomSheet = binding.sheetIncluded.bottomSheet
        val behavior = BottomSheetBehavior.from(bottomSheet)
        
        // 1. Allow sheet to stretch (Match Parent)
        val params = bottomSheet.layoutParams
        params.height = ViewGroup.LayoutParams.MATCH_PARENT
        bottomSheet.layoutParams = params

        // 2. Configure 'Resting' State at 75%
        behavior.isFitToContents = false
        behavior.halfExpandedRatio = 0.75f
        behavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
        
        // 3. Configure 'Bounce' Limits
        behavior.isHideable = false
        behavior.peekHeight = (resources.displayMetrics.heightPixels * 0.20).toInt()
        
        // 4. Rubber Band Logic
        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_EXPANDED || 
                    newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    behavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
                }
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })
    }

    private fun displayResult(name: String, confidence: Float) {
        val pestInfo = com.devsphere.leafbloom.data.model.PestInfo.get(name)

        with(binding.sheetIncluded) {
            tvCommonName.text = name
            tvScientificName.text = getString(pestInfo.speciesRes)
            statusChip.text = getString(pestInfo.threatLevelRes)

            val percent = (confidence * 100).toInt()
            progressConfidence.setProgress(percent, true)
            tvConfidenceValue.text = "$percent%"

            tvCareBody.text = getString(pestInfo.adviceRes)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
