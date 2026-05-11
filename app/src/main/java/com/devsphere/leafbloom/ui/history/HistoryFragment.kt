package com.devsphere.leafbloom.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.devsphere.leafbloom.R
import com.devsphere.leafbloom.data.model.HistoryItem
import com.devsphere.leafbloom.data.model.IdentifyResponse
import com.devsphere.leafbloom.data.model.PestInfo
import com.devsphere.leafbloom.data.repository.ScanHistoryRepository
import com.devsphere.leafbloom.data.source.local.db.LeafBloomDatabase
import com.devsphere.leafbloom.data.source.local.db.ScanHistoryEntity
import com.devsphere.leafbloom.databinding.FragmentHistoryBinding
import com.devsphere.leafbloom.ui.adapter.HistoryAdapter
import com.devsphere.leafbloom.ui.common.BaseFragment
import com.devsphere.leafbloom.util.DateUtils
import com.devsphere.leafbloom.util.ImageStorage
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HistoryFragment : BaseFragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var historyRepository: ScanHistoryRepository
    private lateinit var historyAdapter: HistoryAdapter
    private var observeJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applySystemBarInsets(binding.root)

        val db = LeafBloomDatabase.getInstance(requireContext())
        historyRepository = ScanHistoryRepository(db.scanHistoryDao())

        binding.btnBack.setOnClickListener { findNavController().popBackStack() }

        setupRecyclerView()
        setupChipFilter()

        // Default: Diagnose chip is checked, load diagnose history
        observeHistory("DIAGNOSE")
    }

    private fun setupRecyclerView() {
        historyAdapter = HistoryAdapter(
            onItemClick = { item -> navigateToResult(item) },
            onDeleteClick = { item ->
                lifecycleScope.launch(Dispatchers.IO) {
                    historyRepository.deleteById(item.id)
                    item.imagePath?.let { ImageStorage.delete(it) }
                }
            }
        )
        binding.rvHistory.adapter = historyAdapter
    }

    private fun setupChipFilter() {
        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            val type = when (checkedIds.firstOrNull()) {
                R.id.chipIdentify -> "IDENTIFY"
                R.id.chipPest -> "PEST"
                else -> "DIAGNOSE"
            }
            observeHistory(type)
        }
    }

    private fun observeHistory(scanType: String) {
        observeJob?.cancel()
        observeJob = viewLifecycleOwner.lifecycleScope.launch {
            historyRepository.observeByType(scanType).collectLatest { entities ->
                val items = entities.map { it.toHistoryItem() }
                val sectionLabels = entities.associate { entity ->
                    entity.id to DateUtils.getSectionLabel(requireContext(), entity.timestampMs)
                }
                historyAdapter.submitGroupedList(items, sectionLabels)

                if (items.isEmpty()) {
                    binding.cardHistory.visibility = View.GONE
                    binding.tvEmptyState.visibility = View.VISIBLE
                } else {
                    binding.cardHistory.visibility = View.VISIBLE
                    binding.tvEmptyState.visibility = View.GONE
                }
            }
        }
    }

    private fun navigateToResult(item: HistoryItem) {
        when (item.scanType) {
            "PEST" -> {
                val bundle = Bundle().apply {
                    putString("image_uri", item.imagePath)
                    putString("predicted_class_name", item.plantName)
                    putFloat("confidence", item.confidence / 100f)
                }
                findNavController().navigate(
                    R.id.action_historyFragment_to_pestResultFragment, bundle
                )
            }
            "IDENTIFY" -> {
                val bundle = Bundle().apply {
                    putString("image_uri", item.imagePath)
                    // Retrieve full entity to get JSON — scanId lookup
                    // We pass the scanId so the fragment can load it
                    putLong("scanId", item.id)
                }
                // Load identify response from DB and navigate
                lifecycleScope.launch {
                    val entity = historyRepository.getById(item.id)
                    val response = entity?.identifyResponseJson?.let {
                        Gson().fromJson(it, IdentifyResponse::class.java)
                    }
                    if (response != null) {
                        val navBundle = Bundle().apply {
                            putString("image_uri", item.imagePath)
                            putParcelable("identify_response", response)
                        }
                        findNavController().navigate(
                            R.id.action_historyFragment_to_identifyResultFragment, navBundle
                        )
                    }
                }
            }
            else -> {
                val bundle = Bundle().apply {
                    putLong("scanId", item.id)
                }
                findNavController().navigate(
                    R.id.action_historyFragment_to_historyDetailsFragment, bundle
                )
            }
        }
    }

    private fun ScanHistoryEntity.toHistoryItem(): HistoryItem {
        val isHealthy = predictedClass.equals("Healthy", ignoreCase = true)
        var displayPlantName = predictedClass

        val status = when (scanType) {
            "PEST" -> {
                val pestInfo = PestInfo.get(predictedClass)
                try { getString(pestInfo.threatLevelRes) } catch (_: Exception) { "Unknown" }
            }
            "IDENTIFY" -> {
                identifyResponseJson?.let {
                    try {
                        val response = Gson().fromJson(it, IdentifyResponse::class.java)
                        val bestMatch = response.data?.results?.firstOrNull()
                        
                        // Extract common name
                        val commonName = bestMatch?.commonNames?.firstOrNull()
                        if (!commonName.isNullOrBlank()) {
                            displayPlantName = commonName.replaceFirstChar { char ->
                                if (char.isLowerCase()) char.titlecase(java.util.Locale.ROOT) else char.toString()
                            }
                        }
                        "Identified"
                    } catch (_: Exception) { "Unknown" }
                } ?: "Unknown"
            }
            else -> if (isHealthy) "Healthy" else "Infected"
        }

        return HistoryItem(
            id = id,
            plantName = displayPlantName,
            status = status,
            confidence = (confidence * 100).toInt(),
            date = DateUtils.getTimeOnly(timestampMs),
            imagePath = imagePath,
            isHealthy = isHealthy,
            scanType = scanType
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        observeJob?.cancel()
        _binding = null
    }
}