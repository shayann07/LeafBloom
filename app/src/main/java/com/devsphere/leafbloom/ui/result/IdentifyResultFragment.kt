package com.devsphere.leafbloom.ui.result

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
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

        setupUI()
        observeViewModel()

        // Get URI and trigger identification
        val uriString = arguments?.getString("image_uri")
        if (uriString != null) {
            val uri = Uri.parse(uriString)
            
            // Allow Glide to load image
            Glide.with(this)
                .load(uri)
                .into(binding.ivCapturedImage)

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

        // Setup Bottom Sheet
        // Access via included binding 'sheetIncluded'
        val bottomSheetBehavior = BottomSheetBehavior.from(binding.sheetIncluded.bottomSheet)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
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
            
            scoreChip.text = "${((bestMatch.score ?: 0.0) * 100).toInt()}% Confidence"
            familyChip.text = bestMatch.family ?: "Unknown Family"
            
            // Expand BottomSheet
            BottomSheetBehavior.from(bottomSheet).state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}