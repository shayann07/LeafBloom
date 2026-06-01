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
import com.devsphere.leafbloom.ui.motion.Motion
import com.devsphere.leafbloom.ui.motion.animateGaugeInt
import com.devsphere.leafbloom.ui.motion.bounceOnPress
import com.devsphere.leafbloom.ui.motion.entranceFadeUp
import com.devsphere.leafbloom.ui.motion.entranceScaleFadeUp
import com.devsphere.leafbloom.ui.motion.primeFadeUp
import com.devsphere.leafbloom.ui.motion.primeScaleFadeUp
import com.devsphere.leafbloom.ui.motion.pulseOnce
import com.devsphere.leafbloom.ui.motion.setGaugeIntInstant
import com.devsphere.leafbloom.ui.motion.snapVisible
import com.google.android.material.bottomsheet.BottomSheetBehavior

class RipenessResultFragment : BaseFragment() {
    private var _binding: FragmentRipenessResultBinding? = null
    private val binding get() = _binding!!

    private var hasPlayedEntrance: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRipenessResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()

        // 1. Adaptive Header
        setupAdaptiveHeader(binding.headerContainer, binding.ivHeader)
        
        setupUI()
        
        val uriString = arguments?.getString("image_uri") // Since we want an image in the background
        if (uriString != null) {
            val uri = Uri.parse(uriString)
            Glide.with(this).load(uri).into(binding.ivHeader)
        }

        val args = arguments
        val scoreRipe = args?.getFloat("score_ripe", 0f) ?: 0f
        val scoreUnripe = args?.getFloat("score_unripe", 0f) ?: 0f
        val scoreUnknown = args?.getFloat("score_unknown", 0f) ?: 0f
        displayResult(scoreRipe, scoreUnripe, scoreUnknown)

        binding.btnBack.bounceOnPress()
        playEntrance(maxOf(scoreRipe, scoreUnripe, scoreUnknown))
    }

    private fun playEntrance(bestScore: Float) {
        val sheet = binding.sheetIncluded
        if (hasPlayedEntrance || Motion.reduced(requireContext())) {
            setGaugeIntInstant(sheet.progressConfidence, sheet.tvConfidenceValue, bestScore)
            listOf(
                binding.btnBack, sheet.cardResult, sheet.tvCareTitle, sheet.cardCare,
            ).forEach { it.snapVisible() }
            startPostponedEnterTransition()
            return
        }

        binding.btnBack.primeFadeUp()
        sheet.cardResult.primeScaleFadeUp(translationDp = 28f)
        sheet.tvCareTitle.primeFadeUp(20f)
        sheet.cardCare.primeFadeUp(28f)

        androidx.core.view.OneShotPreDrawListener.add(binding.root) {
            if (_binding == null) return@add
            startPostponedEnterTransition()
            binding.btnBack.entranceFadeUp(delay = 0L, duration = Motion.LONG_2)
            sheet.cardResult.entranceScaleFadeUp(delay = 200L, duration = 650L)
            animateGaugeInt(
                sheet.progressConfidence, sheet.tvConfidenceValue, bestScore,
                delay = 500L, duration = 1400L,
            )
            sheet.tvCareTitle.entranceFadeUp(delay = 700L, duration = Motion.LONG_2)
            sheet.cardCare.entranceFadeUp(delay = 820L, duration = 650L)
            if (bestScore >= 0.5f) {
                sheet.cardResult.pulseOnce(peakScale = 1.06f, duration = 900L, delay = 2100L)
            }
            hasPlayedEntrance = true
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

            // Update Single Gauge — start at 0; animateGaugeInt in playEntrance owns the sweep.
            progressConfidence.progress = 0
            tvConfidenceValue.text = "0%"

            // Update Advice
            tvCareBody.text = getString(ripenessInfo.adviceRes)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
