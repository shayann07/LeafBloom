package com.devsphere.leafbloom.ui.result

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.devsphere.leafbloom.databinding.FragmentRipenessResultBinding
import com.devsphere.leafbloom.ui.common.BaseFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlin.math.roundToInt

class RipenessResultFragment : BaseFragment() {
    private var _binding: FragmentRipenessResultBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRipenessResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Adaptive Header
        setupAdaptiveHeader(binding.headerContainer, binding.ivHeader)
        
        setupUI()
        
        val uriString = arguments?.getString("image_uri") // Since we want an image in the background
        if (uriString != null) {
            val uri = Uri.parse(uriString)
            Glide.with(this).load(uri).into(binding.ivHeader)
        }

        val args = arguments
        if (args != null) {
            val scoreRipe = args.getFloat("score_ripe", 0f)
            val scoreUnripe = args.getFloat("score_unripe", 0f)
            val scoreUnknown = args.getFloat("score_unknown", 0f)
            
            displayResult(scoreRipe, scoreUnripe, scoreUnknown)
        } else {
            displayResult(0f, 0f, 0f)
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

    private fun displayResult(scoreRipe: Float, scoreUnripe: Float, scoreUnknown: Float) {
        with(binding.sheetIncluded) {
            // Find Best Match
            val bestScore = maxOf(scoreRipe, scoreUnripe, scoreUnknown)
            val stateName = when(bestScore) {
                scoreRipe -> getString(com.devsphere.leafbloom.R.string.ripe_tomato)
                scoreUnripe -> getString(com.devsphere.leafbloom.R.string.unripe_tomato)
                else -> getString(com.devsphere.leafbloom.R.string.unknown_stage)
            }

            val ripenessInfo = com.devsphere.leafbloom.data.model.RipenessInfo.get(stateName)

            // Update Title/Subtitle
            tvCommonName.text = stateName
            
            if (ripenessInfo.showScientificName) {
                tvScientificName.visibility = View.VISIBLE
                tvScientificName.text = getString(com.devsphere.leafbloom.R.string.solanum_lycopersicum)
            } else {
                tvScientificName.visibility = View.GONE
            }

            // Update Status Chip
            statusChip.text = getString(ripenessInfo.statusLabelRes)

            // Update Single Gauge
            val percent = (bestScore.coerceIn(0f, 1f) * 100).roundToInt()
            progressConfidence.setProgress(percent, true)
            tvConfidenceValue.text = "$percent%"

            // Update Advice
            tvCareBody.text = getString(ripenessInfo.adviceRes)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
