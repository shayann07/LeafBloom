package com.devsphere.leafbloom.ui.result

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.core.view.isVisible
import com.devsphere.leafbloom.data.model.IdentifyResponse
import android.os.Build
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.devsphere.leafbloom.databinding.FragmentIdentifyResultBinding
import com.devsphere.leafbloom.ui.common.BaseFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.devsphere.leafbloom.R

class IdentifyResultFragment : BaseFragment() {
    private var _binding: FragmentIdentifyResultBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentIdentifyResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Adaptive Header
        setupAdaptiveHeader(binding.headerContainer, binding.ivHeader)

        setupUI()

        // Hide overlay safely since there is no background work happening
        binding.loadingOverlay.isVisible = false

        // Get URI and Identity Response
        val uriString = arguments?.getString("image_uri")
        val identifyResponse = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arguments?.getParcelable("identify_response", IdentifyResponse::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments?.getParcelable("identify_response") as? IdentifyResponse
        }

        if (uriString != null && identifyResponse != null) {
            val imageSource: Any = if (uriString.startsWith("/")) {
                java.io.File(uriString)
            } else {
                Uri.parse(uriString)
            }
            
            // Allow Glide to load image into header
            Glide.with(this)
                .load(imageSource)
                .into(binding.ivHeader)

            // Instantly display data
            displayResult(identifyResponse)

        } else {
             com.devsphere.leafbloom.util.SnackbarUtils.showSnackbar(
                 requireView(), 
                 "Missing image or response data!", 
                 com.google.android.material.snackbar.Snackbar.LENGTH_SHORT,
                 com.devsphere.leafbloom.util.SnackbarUtils.Type.ERROR
             )
             findNavController().popBackStack()
        }
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
        behavior.peekHeight = (resources.displayMetrics.heightPixels * 0.20).toInt() // Small peek height for downward drag range

        // 4. Rubber Band Logic
        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                // If user drags too high (Expanded) or too low (Collapsed), 
                // bounce back to the 75% resting point (Half Expanded)
                if (newState == BottomSheetBehavior.STATE_EXPANDED || 
                    newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    behavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // Optional: visual feedback
            }
        })
    }



    private fun displayResult(response: com.devsphere.leafbloom.data.model.IdentifyResponse) {
        val data = response.data ?: return
        val bestMatch = data.results?.firstOrNull() ?: return

        // Access via included binding
        with(binding.sheetIncluded) {
            val commonName = bestMatch.commonNames?.firstOrNull()
            
            if (!commonName.isNullOrBlank()) {
                // Capitalize first letter of common name for better UI
                tvCommonName.text = commonName.replaceFirstChar { 
                    if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() 
                }
                tvScientificName.text = bestMatch.scientificName ?: data.bestMatch
                tvScientificName.isVisible = true
            } else {
                // Fallback to scientific name as title, hide subtitle
                tvCommonName.text = data.bestMatch ?: "Unknown Plant"
                tvScientificName.isVisible = false
            }
            
            // Update Confidence Gauge
            val confidence = ((bestMatch.score ?: 0.0) * 100).toInt()
            progressConfidence.setProgress(confidence, true)
            tvConfidenceValue.text = "$confidence%"
            
            familyChip.isVisible = false
            
            // Sheet is already "expanded" to its fixed 75% peek height, no need to change state
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}