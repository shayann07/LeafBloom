package com.devsphere.leafbloom.ui.result

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.devsphere.leafbloom.databinding.FragmentIdentifyResultBinding
import com.devsphere.leafbloom.ui.common.BaseFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.launch
import com.devsphere.leafbloom.R

class IdentifyResultFragment : BaseFragment() {
    private var _binding: FragmentIdentifyResultBinding? = null
    private val binding get() = _binding!!

    private val viewModel: IdentifyViewModel by viewModels {
        IdentifyViewModel.Factory(requireActivity().application)
    }

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
        observeViewModel()

        // Get URI and trigger identification
        val uriString = arguments?.getString("image_uri")
        if (uriString != null) {
            val uri = Uri.parse(uriString)
            
            // Allow Glide to load image into header
            Glide.with(this)
                .load(uri)
                .into(binding.ivHeader)

            // Trigger ID
            viewModel.identifyPlant(uri)

        } else {
             Toast.makeText(requireContext(), "No image received", Toast.LENGTH_SHORT).show()
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

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is IdentifyUiState.Idle -> {
                        binding.loadingOverlay.isVisible = false
                    }
                    is IdentifyUiState.Loading -> {
                        binding.loadingOverlay.isVisible = true
                        binding.loadingOverlay.animate().alpha(1f).duration = 300
                    }
                    is IdentifyUiState.Success -> {
                        binding.loadingOverlay.animate().alpha(0f).withEndAction {
                            binding.loadingOverlay.isVisible = false
                        }
                        displayResult(state.response)
                    }
                    is IdentifyUiState.Error -> {
                        binding.loadingOverlay.isVisible = false
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun displayResult(response: com.devsphere.leafbloom.data.model.IdentifyResponse) {
        val data = response.data ?: return
        val bestMatch = data.results?.firstOrNull() ?: return

        // Access via included binding
        with(binding.sheetIncluded) {
            tvCommonName.text = data.bestMatch ?: "Unknown Plant"
            tvScientificName.text = bestMatch.scientificName
            
            // Update Confidence Gauge
            val confidence = ((bestMatch.score ?: 0.0) * 100).toInt()
            progressConfidence.setProgress(confidence, true)
            tvConfidenceValue.text = "$confidence%"
            
            familyChip.text = bestMatch.family ?: "Unknown Family"
            
            // Sheet is already "expanded" to its fixed 75% peek height, no need to change state
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}