package com.devsphere.leafbloom.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import com.devsphere.leafbloom.R
import com.devsphere.leafbloom.databinding.FragmentHistoryDetailsBinding
import com.devsphere.leafbloom.databinding.ItemSymptomCardBinding
import com.devsphere.leafbloom.ui.common.BaseFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior

class HistoryDetailsFragment : BaseFragment() {

    private var _binding: FragmentHistoryDetailsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryDetailsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Adaptive Header
        setupAdaptiveHeader(binding.headerContainer, binding.ivHeader)

        // 2. Navigation
        binding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        // 3. UI Setup
        setupBottomSheet()
        setupSymptomCards()
    }

    private fun setupBottomSheet() {
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
                if (newState == BottomSheetBehavior.STATE_EXPANDED || newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    behavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })
    }

    private fun setupSymptomCards() {
        with(binding.sheetIncluded) {
            setupCard(
                cardWater, "Water", "250 ml", R.color.accent_water_blue, R.drawable.water_icon
            )
            setupCard(
                cardSunlight,
                "Sunlight",
                "Normal",
                R.color.accent_sun_yellow,
                R.drawable.sunlight_icon
            )
            setupCard(
                cardFertilizer,
                "Fertilizer",
                "70 ml",
                R.color.accent_fertilizer_coral,
                R.drawable.fertilizer_icon
            )
            setupCard(
                cardHumidity,
                "Humidity",
                "54%",
                R.color.accent_humidity_orange,
                R.drawable.humidity_icon
            )
        }
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
