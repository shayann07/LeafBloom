package com.devsphere.leafbloom.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.devsphere.leafbloom.R
import com.devsphere.leafbloom.data.model.DiseaseCareInfo
import com.devsphere.leafbloom.databinding.FragmentHistoryDetailsBinding
import com.devsphere.leafbloom.databinding.ItemSymptomCardBinding
import com.devsphere.leafbloom.ui.common.BaseFragment
import com.devsphere.leafbloom.ui.motion.Motion
import com.devsphere.leafbloom.ui.motion.bounceOnPress
import com.devsphere.leafbloom.ui.motion.entranceFadeUp
import com.devsphere.leafbloom.ui.motion.entrancePop
import com.devsphere.leafbloom.ui.motion.entranceScaleFadeUp
import com.devsphere.leafbloom.ui.motion.primeFadeUp
import com.devsphere.leafbloom.ui.motion.primeScaleFadeUp
import com.devsphere.leafbloom.ui.motion.primeScalePop
import com.devsphere.leafbloom.ui.motion.pulseOnce
import com.devsphere.leafbloom.ui.motion.snapVisible
import com.devsphere.leafbloom.util.SnackbarUtils
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.io.File

class HistoryDetailsFragment : BaseFragment() {

    private var _binding: FragmentHistoryDetailsBinding? = null
    private val binding get() = _binding!!

    private val vm: HistoryDetailsViewModel by viewModels {
        HistoryDetailsViewModel.Factory(requireActivity().application, this, arguments)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()

        setupAdaptiveHeader(binding.headerContainer, binding.ivHeader)
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        setupBottomSheet()

        // Tactile press feedback on interactive elements.
        val sheet = binding.sheetIncluded
        listOf(
            binding.btnBack, binding.fabChat,
            sheet.cardWater.root, sheet.cardSunlight.root,
            sheet.cardFertilizer.root, sheet.cardHumidity.root,
        ).forEach { it.bounceOnPress() }

        playEntrance()

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect(::render)
            }
        }
    }

    private fun playEntrance() {
        val sheet = binding.sheetIncluded
        val statCards = listOf(
            sheet.cardWater.root, sheet.cardSunlight.root,
            sheet.cardFertilizer.root, sheet.cardHumidity.root,
        )
        val textBlocks = listOf(
            sheet.tvOverviewLabel, sheet.tvOverviewBody,
            sheet.tvTreatmentLabel, sheet.tvTreatmentBody,
            sheet.tvPreventionLabel, sheet.tvPreventionBody,
        )

        if (Motion.reduced(requireContext())) {
            (listOf(binding.btnBack, sheet.tvTitle, sheet.gridContainer, binding.fabChat)
                + statCards + textBlocks).forEach { it.snapVisible() }
            startPostponedEnterTransition()
            return
        }

        binding.btnBack.primeFadeUp()
        sheet.tvTitle.primeFadeUp(20f)
        statCards.forEach { it.primeScaleFadeUp(translationDp = 24f) }
        textBlocks.forEach { it.primeFadeUp(16f) }
        binding.fabChat.primeScalePop()

        androidx.core.view.OneShotPreDrawListener.add(binding.root) {
            if (_binding == null) return@add
            startPostponedEnterTransition()

            binding.btnBack.entranceFadeUp(delay = 0L, duration = Motion.LONG_2)
            sheet.tvTitle.entranceFadeUp(delay = 120L, duration = Motion.LONG_2)

            // Stat cards stagger like Diagnose's gauge cards.
            statCards.forEachIndexed { i, v ->
                v.entranceScaleFadeUp(delay = 240L + i * 100L, duration = 600L)
            }

            // Text blocks fade up after the cards have started landing.
            textBlocks.forEachIndexed { i, v ->
                v.entranceFadeUp(delay = 700L + i * Motion.STAGGER_GAP, duration = Motion.LONG_2)
            }

            binding.fabChat.entrancePop(delay = 1300L, duration = Motion.LONG_2)

            // Subtle flourish on the stat grid once everything has landed.
            sheet.gridContainer.pulseOnce(peakScale = 1.04f, duration = 800L, delay = 1500L)
        }
    }

    private fun render(state: DetailsUiState) {
        when (state) {
            DetailsUiState.Loading -> Unit
            DetailsUiState.NotFound -> {
                startPostponedEnterTransition()
                SnackbarUtils.showSnackbar(
                    requireView(),
                    "Scan not found",
                    Snackbar.LENGTH_SHORT,
                    SnackbarUtils.Type.WARNING
                )
                findNavController().popBackStack()
            }
            is DetailsUiState.Resolved -> populate(state)
        }
    }

    private fun populate(state: DetailsUiState.Resolved) {
        val care = state.careInfo

        val path = state.headerImagePath
        if (path != null && File(path).exists()) {
            Glide.with(this)
                .load(File(path))
                .centerCrop()
                .placeholder(state.headerImageRes)
                .error(state.headerImageRes)
                .into(binding.ivHeader)
        } else {
            binding.ivHeader.setImageResource(state.headerImageRes)
        }

        binding.sheetIncluded.tvTitle.text = state.title
        binding.sheetIncluded.tvOverviewBody.text = getString(care.overviewRes)
        binding.sheetIncluded.tvTreatmentBody.text = getString(care.treatmentRes)
        binding.sheetIncluded.tvPreventionBody.text = getString(care.preventionRes)

        setupCard(binding.sheetIncluded.cardWater, getString(R.string.water), care.water,
            R.color.accent_water_blue, R.drawable.water_icon)
        setupCard(binding.sheetIncluded.cardSunlight, getString(R.string.sunlight), care.sunlight,
            R.color.accent_sun_yellow, R.drawable.sunlight_icon)
        setupCard(binding.sheetIncluded.cardFertilizer, getString(R.string.fertilizer), care.fertilizer,
            R.color.accent_fertilizer_coral, R.drawable.fertilizer_icon)
        setupCard(binding.sheetIncluded.cardHumidity, getString(R.string.humidity), care.humidity,
            R.color.accent_humidity_orange, R.drawable.humidity_icon)

        binding.fabChat.setOnClickListener {
            val systemPrompt = buildHistorySystemPrompt(
                state.title,
                state.scanType,
                getString(care.overviewRes),
                getString(care.treatmentRes),
                getString(care.preventionRes),
                care.water,
                care.sunlight,
                care.fertilizer,
                care.humidity
            )
            findNavController().navigate(
                R.id.action_historyDetails_to_chat,
                bundleOf(
                    "system_prompt" to systemPrompt,
                    "context_title" to state.title
                )
            )
        }
    }

    private fun setupBottomSheet() {
        val bottomSheet = binding.sheetIncluded.bottomSheet
        val behavior = BottomSheetBehavior.from(bottomSheet)

        val params = bottomSheet.layoutParams
        params.height = ViewGroup.LayoutParams.MATCH_PARENT
        bottomSheet.layoutParams = params

        behavior.isFitToContents = false
        behavior.halfExpandedRatio = 0.75f
        behavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
        behavior.isHideable = false
        behavior.peekHeight = (resources.displayMetrics.heightPixels * 0.20).toInt()

        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    behavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
                }
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })
    }

    private fun setupCard(
        cardBinding: ItemSymptomCardBinding,
        title: String,
        value: String,
        colorRes: Int,
        iconRes: Int
    ) {
        val color = ContextCompat.getColor(requireContext(), colorRes)
        cardBinding.tvLabel.text = title
        cardBinding.tvLabel.setTextColor(color)
        cardBinding.tvValue.text = value
        cardBinding.iconContainer.setCardBackgroundColor(color)
        cardBinding.ivIcon.setImageResource(iconRes)
    }

    private fun buildHistorySystemPrompt(
        predictedName: String,
        scanType: String,
        overview: String,
        treatment: String,
        prevention: String,
        water: String,
        sunlight: String,
        fertilizer: String,
        humidity: String
    ): String = """
You are LeafBloom AI, a friendly plant health assistant inside the LeafBloom app.
The user is viewing a past ${if (scanType.equals("PEST", true)) "pest scan" else "leaf diagnosis"} from their history: "$predictedName".

Context from the saved scan:
- Overview: $overview
- Treatment advice: $treatment
- Prevention tips: $prevention
- Recommended care: Water=$water, Sunlight=$sunlight, Fertilizer=$fertilizer, Humidity=$humidity

Guidelines:
- Answer in the SAME LANGUAGE the user writes in.
- Match response length to the question: keep casual chat and simple questions short (1-2 sentences), and only go longer when the user asks for details, steps, or a full explanation. Never pad replies.
- Focus on practical, actionable advice for home gardeners.
- You may suggest organic or chemical treatments available in local markets.
- Do not diagnose new diseases from text descriptions alone — recommend another scan.
    """.trimIndent()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
