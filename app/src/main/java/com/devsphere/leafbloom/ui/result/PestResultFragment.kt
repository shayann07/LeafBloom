package com.devsphere.leafbloom.ui.result

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.devsphere.leafbloom.R
import com.devsphere.leafbloom.data.model.PestInfo
import com.devsphere.leafbloom.databinding.FragmentPestResultBinding
import com.devsphere.leafbloom.ui.common.BaseFragment
import com.devsphere.leafbloom.ui.motion.Motion
import com.devsphere.leafbloom.ui.motion.animateGaugeInt
import com.devsphere.leafbloom.ui.motion.bounceOnPress
import com.devsphere.leafbloom.ui.motion.entranceFadeUp
import com.devsphere.leafbloom.ui.motion.entrancePop
import com.devsphere.leafbloom.ui.motion.entranceScaleFadeUp
import com.devsphere.leafbloom.ui.motion.primeFadeUp
import com.devsphere.leafbloom.ui.motion.primeScaleFadeUp
import com.devsphere.leafbloom.ui.motion.primeScalePop
import com.devsphere.leafbloom.ui.motion.pulseOnce
import com.devsphere.leafbloom.ui.motion.setGaugeIntInstant
import com.devsphere.leafbloom.ui.motion.snapVisible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlin.math.roundToInt

class PestResultFragment : BaseFragment() {
    private var _binding: FragmentPestResultBinding? = null
    private val binding get() = _binding!!

    private var hasPlayedEntrance: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPestResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()

        // 1. Adaptive Header
        setupAdaptiveHeader(binding.headerContainer, binding.ivHeader)
        
        setupUI()
        
        val uriString = arguments?.getString("image_uri")
        val predictedName = arguments?.getString("predicted_class_name", getString(com.devsphere.leafbloom.R.string.unknown_pest))
        val confidenceVal = arguments?.getFloat("confidence", 0f) ?: 0f

        // Chat FAB
        binding.fabChat.setOnClickListener {
            val pestInfo = PestInfo.get(predictedName!!)
            val systemPrompt = buildPestSystemPrompt(
                predictedName, confidenceVal,
                getString(pestInfo.speciesRes),
                getString(pestInfo.threatLevelRes),
                getString(pestInfo.adviceRes)
            )
            findNavController().navigate(
                R.id.action_pestResult_to_chat,
                bundleOf("system_prompt" to systemPrompt, "context_title" to predictedName)
            )
        }

        if (uriString != null) {
            val imageSource: Any = if (uriString.startsWith("/")) {
                java.io.File(uriString)
            } else {
                Uri.parse(uriString)
            }
            Glide.with(this).load(imageSource).into(binding.ivHeader)
        }
        
        displayResult(predictedName!!, confidenceVal)

        listOf(
            binding.btnBack,
            binding.fabChat,
            binding.sheetIncluded.cardResult,
            binding.sheetIncluded.cardCare,
        ).forEach { it.bounceOnPress() }
        playEntrance(confidenceVal)
    }

    private fun playEntrance(confidence: Float) {
        val sheet = binding.sheetIncluded
        if (hasPlayedEntrance || Motion.reduced(requireContext())) {
            setGaugeIntInstant(sheet.progressConfidence, sheet.tvConfidenceValue, confidence)
            listOf(
                binding.btnBack, sheet.cardResult, sheet.cardCare,
                sheet.tvCareTitle, binding.fabChat,
            ).forEach { it.snapVisible() }
            startPostponedEnterTransition()
            return
        }

        binding.btnBack.primeFadeUp()
        sheet.cardResult.primeScaleFadeUp(translationDp = 28f)
        sheet.tvCareTitle.primeFadeUp(20f)
        sheet.cardCare.primeFadeUp(28f)
        binding.fabChat.primeScalePop()

        androidx.core.view.OneShotPreDrawListener.add(binding.root) {
            if (_binding == null) return@add
            startPostponedEnterTransition()
            binding.btnBack.entranceFadeUp(delay = 0L, duration = Motion.LONG_2)
            sheet.cardResult.entranceScaleFadeUp(delay = 200L, duration = 650L)
            animateGaugeInt(
                sheet.progressConfidence, sheet.tvConfidenceValue, confidence,
                delay = 500L, duration = 1400L,
            )
            sheet.tvCareTitle.entranceFadeUp(delay = 700L, duration = Motion.LONG_2)
            sheet.cardCare.entranceFadeUp(delay = 820L, duration = 650L)
            binding.fabChat.entrancePop(delay = 1000L, duration = Motion.LONG_2)
            // Pulse the result card once the gauge sweep lands — same flourish as Diagnose's winner.
            if (confidence >= 0.5f) {
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

    private fun displayResult(name: String, confidence: Float) {
        val pestInfo = com.devsphere.leafbloom.data.model.PestInfo.get(name)

        with(binding.sheetIncluded) {
            tvCommonName.text = name
            tvScientificName.text = getString(pestInfo.speciesRes)
            statusChip.text = getString(pestInfo.threatLevelRes)

            // Start at 0; playEntrance's animateGaugeInt owns the sweep.
            // Setting setProgress(value, true) here would race with that animator and stutter.
            progressConfidence.progress = 0
            tvConfidenceValue.text = "0%"

            tvCareBody.text = getString(pestInfo.adviceRes)
        }
    }

    private fun buildPestSystemPrompt(
        predictedName: String,
        confidence: Float,
        species: String,
        threatLevel: String,
        advice: String
    ): String = """
You are LeafBloom AI, a friendly pest management assistant inside the LeafBloom app.
The user just scanned an insect/pest and the AI model identified: "$predictedName" (species: $species).
Detection confidence: ${(confidence * 100).roundToInt()}%
Threat level: $threatLevel
Recommended action: $advice

Guidelines:
- Answer in the SAME LANGUAGE the user writes in.
- Match response length to the question: keep casual chat and simple questions short (1-2 sentences), and only go longer when the user asks for details, steps, or a full explanation. Never pad replies.
- Focus on practical pest management for home gardens.
- Suggest both organic and chemical control methods when relevant.
- If the detected organism is beneficial (e.g., bees, earthworms), explain why.
- Do not identify new pests from text descriptions alone — recommend another scan.
    """.trimIndent()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
