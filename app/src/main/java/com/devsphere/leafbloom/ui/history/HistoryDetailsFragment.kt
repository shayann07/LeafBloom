package com.devsphere.leafbloom.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.bumptech.glide.Glide
import com.devsphere.leafbloom.R
import com.devsphere.leafbloom.data.model.DiseaseCareInfo
import com.devsphere.leafbloom.data.model.DiseaseInfo
import com.devsphere.leafbloom.data.repository.ScanHistoryRepository
import com.devsphere.leafbloom.data.source.local.db.LeafBloomDatabase
import com.devsphere.leafbloom.data.source.local.db.ScanHistoryEntity
import com.devsphere.leafbloom.databinding.FragmentHistoryDetailsBinding
import com.devsphere.leafbloom.databinding.ItemSymptomCardBinding
import com.devsphere.leafbloom.ui.common.BaseFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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

        // 3. Bottom Sheet
        setupBottomSheet()

        // 4. Load data from Room if scanId is provided, otherwise show static fallback
        val scanId = arguments?.getLong("scanId", -1L) ?: -1L
        if (scanId > 0) {
            loadFromDatabase(scanId)
        } else {
            // Fallback: show static data for backwards compatibility
            setupStaticContent()
        }
    }

    private fun loadFromDatabase(scanId: Long) {
        val db = LeafBloomDatabase.getInstance(requireContext())
        val repository = ScanHistoryRepository(db.scanHistoryDao())

        viewLifecycleOwner.lifecycleScope.launch {
            val entity = withContext(Dispatchers.IO) {
                repository.getById(scanId)
            }

            if (entity == null) {
                // Scan was deleted or ID is invalid
                com.devsphere.leafbloom.util.SnackbarUtils.showSnackbar(
                    requireView(),
                    "Scan not found",
                    com.google.android.material.snackbar.Snackbar.LENGTH_SHORT,
                    com.devsphere.leafbloom.util.SnackbarUtils.Type.WARNING
                )
                findNavController().popBackStack()
                return@launch
            }

            populateFromEntity(entity)
        }
    }

    private fun populateFromEntity(entity: ScanHistoryEntity) {
        // Load header image with fallback
        val imagePath = entity.imagePath
        if (imagePath.isNotEmpty() && File(imagePath).exists()) {
            Glide.with(this)
                .load(File(imagePath))
                .centerCrop()
                .placeholder(R.drawable.disease_header)
                .error(R.drawable.disease_header)
                .into(binding.ivHeader)
        }
        // else: keep the default disease_header from XML

        // Get care info for this disease class
        val careInfo = DiseaseCareInfo.get(entity.predictedClass)
        val diseaseInfo = DiseaseInfo.get(entity.predictedClass)

        // Title
        binding.sheetIncluded.tvTitle.text = entity.predictedClass

        // Overview
        binding.sheetIncluded.tvOverviewBody.text = getString(careInfo.overviewRes)

        // Symptom cards with per-class values
        setupCard(
            binding.sheetIncluded.cardWater,
            getString(R.string.water),
            careInfo.water,
            R.color.accent_water_blue,
            R.drawable.water_icon
        )
        setupCard(
            binding.sheetIncluded.cardSunlight,
            getString(R.string.sunlight),
            careInfo.sunlight,
            R.color.accent_sun_yellow,
            R.drawable.sunlight_icon
        )
        setupCard(
            binding.sheetIncluded.cardFertilizer,
            getString(R.string.fertilizer),
            careInfo.fertilizer,
            R.color.accent_fertilizer_coral,
            R.drawable.fertilizer_icon
        )
        setupCard(
            binding.sheetIncluded.cardHumidity,
            getString(R.string.humidity),
            careInfo.humidity,
            R.color.accent_humidity_orange,
            R.drawable.humidity_icon
        )

        // Treatment & Prevention
        binding.sheetIncluded.tvTreatmentBody.text = getString(careInfo.treatmentRes)
        binding.sheetIncluded.tvPreventionBody.text = getString(careInfo.preventionRes)
    }

    /**
     * Static fallback content for when no scanId is provided (backwards compatibility).
     */
    private fun setupStaticContent() {
        val careInfo = DiseaseCareInfo.get("Healthy")
        binding.sheetIncluded.tvTitle.text = getString(R.string.tomato)
        binding.sheetIncluded.tvOverviewBody.text = getString(careInfo.overviewRes)

        setupCard(
            binding.sheetIncluded.cardWater,
            getString(R.string.water),
            careInfo.water,
            R.color.accent_water_blue,
            R.drawable.water_icon
        )
        setupCard(
            binding.sheetIncluded.cardSunlight,
            getString(R.string.sunlight),
            careInfo.sunlight,
            R.color.accent_sun_yellow,
            R.drawable.sunlight_icon
        )
        setupCard(
            binding.sheetIncluded.cardFertilizer,
            getString(R.string.fertilizer),
            careInfo.fertilizer,
            R.color.accent_fertilizer_coral,
            R.drawable.fertilizer_icon
        )
        setupCard(
            binding.sheetIncluded.cardHumidity,
            getString(R.string.humidity),
            careInfo.humidity,
            R.color.accent_humidity_orange,
            R.drawable.humidity_icon
        )

        binding.sheetIncluded.tvTreatmentBody.text = getString(careInfo.treatmentRes)
        binding.sheetIncluded.tvPreventionBody.text = getString(careInfo.preventionRes)
    }

    private fun setupBottomSheet() {
        val bottomSheet = binding.sheetIncluded.bottomSheet
        val behavior = BottomSheetBehavior.from(bottomSheet)

        // 1. Allow sheet to stretch (Match Parent)
        val params = bottomSheet.layoutParams
        params.height = ViewGroup.LayoutParams.MATCH_PARENT
        bottomSheet.layoutParams = params

        // 2. Configure resting state at 75%
        behavior.isFitToContents = false
        behavior.halfExpandedRatio = 0.75f
        behavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED

        // 3. Allow full expansion for scrolling, prevent hiding
        behavior.isHideable = false
        behavior.peekHeight = (resources.displayMetrics.heightPixels * 0.20).toInt()

        // 4. Only prevent full collapse — allow full expansion for scrolling
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
