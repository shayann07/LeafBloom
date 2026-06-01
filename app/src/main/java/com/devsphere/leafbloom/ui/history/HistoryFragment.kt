package com.devsphere.leafbloom.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.devsphere.leafbloom.R
import com.devsphere.leafbloom.data.model.HistoryItem
import com.devsphere.leafbloom.databinding.FragmentHistoryBinding
import com.devsphere.leafbloom.ui.adapter.HistoryAdapter
import com.devsphere.leafbloom.ui.common.BaseFragment
import com.devsphere.leafbloom.ui.motion.Motion
import com.devsphere.leafbloom.ui.motion.beginFadeToggle
import com.devsphere.leafbloom.ui.motion.bounceOnPress
import com.devsphere.leafbloom.ui.motion.entranceFadeUp
import com.devsphere.leafbloom.ui.motion.primeFadeUp
import com.devsphere.leafbloom.ui.motion.snapVisible
import kotlinx.coroutines.launch

class HistoryFragment : BaseFragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var historyAdapter: HistoryAdapter
    private var lastFilterType: String? = null
    private var hasPlayedEntrance: Boolean = false
    private var latestData: HistoryUiState.Data? = null
    private var searchQuery: String = ""

    // Edge-triggered debouncing for the search focus crossfade.
    private var lastFocusActive: Boolean? = null
    private var lastEmptyState: Boolean? = null

    private val vm: HistoryViewModel by viewModels {
        HistoryViewModel.Factory(requireActivity().application, this)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()
        applySystemBarInsets(binding.root)

        setupRecyclerView()
        setupChipFilter()
        setupSearch()
        syncChipSelection(vm.currentScanType())

        // Tactile press feedback on the filter chips + search affordance.
        listOf(
            binding.searchContainer,
            binding.chipDiagnose, binding.chipIdentify, binding.chipPest,
        ).forEach { it.bounceOnPress() }

        playHeaderEntrance()

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.state.collect(::render) }
                launch {
                    vm.navigateIdentify.collect { nav ->
                        nav ?: return@collect
                        navigateToIdentifyResult(nav)
                        vm.onIdentifyNavigationHandled()
                    }
                }
            }
        }
    }

    private fun playHeaderEntrance() {
        if (hasPlayedEntrance || Motion.reduced(requireContext())) {
            listOf(
                binding.tvTitle, binding.searchContainer,
                binding.chipGroupFilter, binding.cardHistory,
            ).forEach { it.snapVisible() }
            startPostponedEnterTransition()
            return
        }
        binding.tvTitle.primeFadeUp()
        binding.searchContainer.primeFadeUp(20f)
        binding.chipGroupFilter.primeFadeUp(20f)
        androidx.core.view.OneShotPreDrawListener.add(binding.root) {
            if (_binding == null) return@add
            startPostponedEnterTransition()
            binding.tvTitle.entranceFadeUp(delay = 0L, duration = Motion.LONG_2)
            binding.searchContainer.entranceFadeUp(delay = 80L, duration = Motion.LONG_2)
            binding.chipGroupFilter.entranceFadeUp(delay = 160L, duration = Motion.LONG_2)
            hasPlayedEntrance = true
        }
    }

    private fun setupRecyclerView() {
        historyAdapter = HistoryAdapter(
            onItemClick = ::navigateToResult,
            onDeleteClick = { item -> vm.delete(item.id, item.imagePath) }
        )
        binding.rvHistory.adapter = historyAdapter
        binding.rvHistory.layoutAnimation =
            android.view.animation.AnimationUtils.loadLayoutAnimation(
                requireContext(), R.anim.layout_animation_list
            )
    }

    private fun setupChipFilter() {
        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            val type = when (checkedIds.firstOrNull()) {
                R.id.chipIdentify -> "IDENTIFY"
                R.id.chipPest -> "PEST"
                else -> "DIAGNOSE"
            }
            if (type != vm.currentScanType()) vm.setScanType(type)
        }
    }

    private fun syncChipSelection(type: String) {
        val targetId = when (type) {
            "IDENTIFY" -> R.id.chipIdentify
            "PEST" -> R.id.chipPest
            else -> R.id.chipDiagnose
        }
        if (binding.chipGroupFilter.checkedChipId != targetId) {
            binding.chipGroupFilter.check(targetId)
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener { editable ->
            val q = editable?.toString().orEmpty()
            searchQuery = q
            binding.ivClearSearch.visibility = if (q.isEmpty()) View.GONE else View.VISIBLE
            vm.setSearchQuery(q)
            latestData?.let { applyData(it, filterChanged = false) }
        }
        binding.ivClearSearch.setOnClickListener {
            binding.etSearch.setText("")
        }
    }

    private fun applySearchFocus(active: Boolean) {
        val vis = if (active) View.GONE else View.VISIBLE
        binding.tvTitle.visibility = vis
        binding.chipGroupFilter.visibility = vis
    }

    private fun render(state: HistoryUiState) {
        when (state) {
            HistoryUiState.Loading -> Unit
            is HistoryUiState.Data -> {
                val filterChanged = lastFilterType != state.scanType
                lastFilterType = state.scanType
                latestData = state
                applyData(state, filterChanged = filterChanged)
            }
        }
    }

    /** Applies the active search query on top of [data] and updates the UI. */
    private fun applyData(data: HistoryUiState.Data, filterChanged: Boolean) {
        val entries = filterEntries(data.entries, searchQuery)
        historyAdapter.submitEntries(entries)
        val isEmpty = entries.isEmpty()
        val isSearching = searchQuery.isNotBlank()

        val focusTarget = isSearching && !isEmpty
        val focusChanged = lastFocusActive != focusTarget
        val emptyChanged = lastEmptyState != isEmpty
        if (focusChanged || emptyChanged) {
            (binding.root as ViewGroup).beginFadeToggle(excludeRecycler = binding.rvHistory)
        }
        lastFocusActive = focusTarget
        lastEmptyState = isEmpty

        applySearchFocus(focusTarget)
        if (isEmpty) {
            binding.cardHistory.visibility = View.GONE
            binding.tvEmptyState.visibility = View.VISIBLE
            binding.tvEmptyState.text = if (isSearching) {
                getString(R.string.no_results_for, searchQuery.trim())
            } else {
                getString(R.string.no_scans_yet)
            }
        } else {
            binding.cardHistory.visibility = View.VISIBLE
            binding.tvEmptyState.visibility = View.GONE
            if (filterChanged && !Motion.reduced(requireContext())) {
                binding.rvHistory.scheduleLayoutAnimation()
            }
        }
    }

    /**
     * Filters the entry list by [query] (matched against plant name and status).
     * Section headers are rebuilt so empty date groups don't show a lonely header.
     * Empty query returns the original list untouched.
     */
    private fun filterEntries(
        entries: List<HistoryAdapter.ListEntry>,
        query: String
    ): List<HistoryAdapter.ListEntry> {
        if (query.isBlank()) return entries
        val q = query.trim()
        val out = mutableListOf<HistoryAdapter.ListEntry>()
        var pendingHeader: HistoryAdapter.ListEntry.Header? = null
        for (entry in entries) {
            when (entry) {
                is HistoryAdapter.ListEntry.Header -> pendingHeader = entry
                is HistoryAdapter.ListEntry.Item -> {
                    val item = entry.historyItem
                    val matches = item.plantName.contains(q, ignoreCase = true) ||
                        item.status.contains(q, ignoreCase = true)
                    if (matches) {
                        pendingHeader?.let { out.add(it); pendingHeader = null }
                        out.add(entry)
                    }
                }
            }
        }
        return out
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
            "IDENTIFY" -> vm.requestIdentifyNavigation(item.id, item.imagePath)
            else -> {
                val bundle = Bundle().apply { putLong("scanId", item.id) }
                findNavController().navigate(
                    R.id.action_historyFragment_to_historyDetailsFragment, bundle
                )
            }
        }
    }

    private fun navigateToIdentifyResult(nav: HistoryViewModel.IdentifyNav) {
        val bundle = Bundle().apply {
            putString("image_uri", nav.imagePath)
            putParcelable("identify_response", nav.response)
        }
        findNavController().navigate(
            R.id.action_historyFragment_to_identifyResultFragment, bundle
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        lastFocusActive = null
        lastEmptyState = null
    }
}
