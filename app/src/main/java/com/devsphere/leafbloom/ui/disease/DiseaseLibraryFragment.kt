package com.devsphere.leafbloom.ui.disease

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.MarginPageTransformer
import androidx.viewpager2.widget.ViewPager2
import com.devsphere.leafbloom.R
import com.devsphere.leafbloom.data.model.DiseaseInfo
import com.devsphere.leafbloom.databinding.FragmentDiseaseLibraryBinding
import com.devsphere.leafbloom.databinding.ItemCategoryTileBinding
import com.devsphere.leafbloom.databinding.ItemDiseaseTileBinding
import com.devsphere.leafbloom.ui.adapter.TipCarouselAdapter
import com.devsphere.leafbloom.ui.common.BaseFragment
import com.devsphere.leafbloom.ui.motion.Motion
import com.devsphere.leafbloom.ui.motion.beginFadeToggle
import com.devsphere.leafbloom.ui.motion.bounceOnPress
import com.devsphere.leafbloom.ui.motion.entranceFadeUp
import com.devsphere.leafbloom.ui.motion.entrancePop
import com.devsphere.leafbloom.ui.motion.entranceScaleFadeUp
import com.devsphere.leafbloom.ui.motion.primeFadeUp
import com.devsphere.leafbloom.ui.motion.primeScaleFadeUp
import com.devsphere.leafbloom.ui.motion.primeScalePop
import com.devsphere.leafbloom.ui.motion.snapVisible
import kotlinx.coroutines.launch

class DiseaseLibraryFragment : BaseFragment() {

    private var _binding: FragmentDiseaseLibraryBinding? = null
    private val binding get() = _binding!!

    private val vm: DiseaseLibraryViewModel by viewModels {
        DiseaseLibraryViewModel.Factory(this)
    }

    private var lastDotPosition = -1
    private var hasPlayedEntrance = false
    private var latestState: LibraryUiState? = null
    private var searchQuery: String = ""

    // Edge-triggered debouncing for the search focus crossfade.
    private var lastFocusActive: Boolean? = null
    private var lastEmptyState: Boolean? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDiseaseLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()

        binding.fabChat.setOnClickListener {
            findNavController().navigate(
                R.id.action_diseaseLibrary_to_chat,
                bundleOf(
                    "system_prompt" to buildLibrarySystemPrompt(),
                    "context_title" to getString(R.string.disease_library_title)
                )
            )
        }

        binding.tvSeeAll.setOnClickListener { vm.toggleSeeAll() }
        listOf(binding.fabChat, binding.tvSeeAll).forEach { it.bounceOnPress() }

        setupSearch()
        setupTipCarousel()
        playEntrance()

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state.collect(::render)
            }
        }
    }

    private fun playEntrance() {
        val headerBlocks = listOf(
            binding.tvTitle, binding.searchContainer,
            binding.tipHeader, binding.tipPager, binding.dotIndicators,
            binding.tvCategoriesTitle, binding.categoriesGrid,
        )

        if (Motion.reduced(requireContext())) {
            (headerBlocks + binding.fabChat).forEach { it.snapVisible() }
            startPostponedEnterTransition()
            hasPlayedEntrance = true
            return
        }

        if (hasPlayedEntrance) {
            (headerBlocks + binding.fabChat).forEach { it.snapVisible() }
            startPostponedEnterTransition()
            return
        }

        headerBlocks.forEach { it.primeFadeUp(16f) }
        binding.fabChat.primeScalePop()

        androidx.core.view.OneShotPreDrawListener.add(binding.root) {
            if (_binding == null) return@add
            startPostponedEnterTransition()
            headerBlocks.forEachIndexed { i, v ->
                v.entranceFadeUp(delay = i * Motion.STAGGER_GAP, duration = Motion.LONG_2)
            }
            binding.fabChat.entrancePop(
                delay = 200L + headerBlocks.size * Motion.STAGGER_GAP,
                duration = Motion.LONG_2
            )
            hasPlayedEntrance = true
        }
    }

    private fun setupTipCarousel() {
        val tips = vm.tips
        binding.tipPager.adapter = TipCarouselAdapter(tips)
        binding.tipPager.setPageTransformer(
            MarginPageTransformer(resources.getDimensionPixelSize(R.dimen.space_12))
        )
        buildDots(tips.size)
        binding.tipPager.setCurrentItem(vm.tipPosition(), false)
        updateTipPager(vm.tipPosition())

        binding.tipPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                val nearest = if (positionOffset >= 0.5f) position + 1 else position
                if (nearest in tips.indices) updateTipPager(nearest)
            }

            override fun onPageSelected(position: Int) {
                updateTipPager(position)
                vm.onTipPositionChanged(position)
            }
        })
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener { editable ->
            val q = editable?.toString().orEmpty()
            searchQuery = q
            binding.ivClearSearch.visibility = if (q.isEmpty()) View.GONE else View.VISIBLE
            latestState?.let(::render)
        }
        binding.ivClearSearch.setOnClickListener {
            binding.etSearch.setText("")
        }
    }

    private fun applySearchFocus(active: Boolean) {
        val vis = if (active) View.GONE else View.VISIBLE
        listOf(
            binding.tvTitle,
            binding.tipHeader,
            binding.tipPager,
            binding.dotIndicators,
            binding.tvCategoriesTitle,
            binding.categoriesGrid,
            binding.fabChat,
        ).forEach { it.visibility = vis }
    }

    private fun matchesSearch(entry: DiseaseEntry): Boolean {
        if (searchQuery.isBlank()) return true
        val q = searchQuery.trim()
        return entry.name.contains(q, ignoreCase = true) ||
            getString(entry.categoryRes).contains(q, ignoreCase = true)
    }

    private fun render(state: LibraryUiState) {
        latestState = state
        binding.tvSeeAll.setText(if (state.seeAllExpanded) R.string.show_less else R.string.see_all)

        val isSearching = searchQuery.isNotBlank()
        val detectedMatches = state.detectedFiltered.any(::matchesSearch)
        val otherMatches = state.otherFiltered.any(::matchesSearch)
        val hasResults = detectedMatches || otherMatches

        val focusTarget = isSearching && hasResults
        val emptyTarget = isSearching && !hasResults
        val focusChanged = lastFocusActive != focusTarget
        val emptyChanged = lastEmptyState != emptyTarget
        if (focusChanged || emptyChanged) {
            (binding.root as ViewGroup).beginFadeToggle()
        }
        lastFocusActive = focusTarget
        lastEmptyState = emptyTarget

        renderCategories(state)
        renderDetected(state)
        renderOther(state)

        applySearchFocus(focusTarget || emptyTarget)

        if (emptyTarget) {
            binding.tvNoResults.text = getString(R.string.no_results_for, searchQuery.trim())
            binding.tvNoResults.visibility = View.VISIBLE
        } else {
            binding.tvNoResults.visibility = View.GONE
        }
    }

    private fun renderCategories(state: LibraryUiState) {
        val slots = listOf(
            binding.slotCatFungal,
            binding.slotCatBacterial,
            binding.slotCatViral,
            binding.slotCatPest
        )
        slots.forEachIndexed { i, slot ->
            val entry = state.categories.getOrNull(i) ?: return@forEachIndexed
            inflateCategory(slot, entry, entry.nameRes == state.selectedCategoryRes)
        }
    }

    private fun inflateCategory(slot: ViewGroup, entry: CategoryEntry, isSelected: Boolean) {
        slot.removeAllViews()
        val tile = ItemCategoryTileBinding.inflate(layoutInflater, slot, false)
        tile.tvCategoryName.setText(entry.nameRes)
        tile.tvCategoryCount.text = getString(R.string.cat_count_format, entry.count)
        tile.ivCategoryIcon.setImageResource(entry.iconRes)
        val cardColorRes = if (isSelected) entry.cardBgRes else R.color.surface_card
        tile.cardCategory.setCardBackgroundColor(
            ContextCompat.getColor(requireContext(), cardColorRes)
        )
        tile.iconBg.background = createCircle(entry.iconBgRes)
        tile.cardCategory.setOnClickListener { vm.onCategoryClicked(entry.nameRes) }
        tile.cardCategory.bounceOnPress()
        slot.addView(tile.root)
    }

    private fun renderDetected(state: LibraryUiState) {
        binding.detectedGrid.removeAllViews()
        val items = state.detectedFiltered.filter(::matchesSearch)
        val visible = items.isNotEmpty()
        binding.tvDetectedTitle.visibility = if (visible) View.VISIBLE else View.GONE
        binding.detectedGrid.visibility = if (visible) View.VISIBLE else View.GONE
        addDiseaseGrid(binding.detectedGrid, items)
    }

    private fun renderOther(state: LibraryUiState) {
        binding.otherGrid.removeAllViews()
        val source = if (searchQuery.isBlank()) state.otherVisible else state.otherFiltered
        val items = source.filter(::matchesSearch)
        val visible = items.isNotEmpty()
        binding.otherHeader.visibility = if (visible) View.VISIBLE else View.GONE
        binding.otherGrid.visibility = if (visible) View.VISIBLE else View.GONE
        binding.tvSeeAll.visibility = if (searchQuery.isBlank()) View.VISIBLE else View.GONE
        addDiseaseGrid(binding.otherGrid, items)
    }

    private fun buildDots(count: Int) {
        binding.dotIndicators.removeAllViews()
        val inactiveSize = resources.getDimensionPixelSize(R.dimen.space_6)
        val margin = resources.getDimensionPixelSize(R.dimen.space_4)
        for (i in 0 until count) {
            val dot = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(inactiveSize, inactiveSize).apply {
                    marginStart = margin
                    marginEnd = margin
                }
                background = ContextCompat.getDrawable(requireContext(), R.drawable.dot_inactive)
            }
            binding.dotIndicators.addView(dot)
        }
    }

    private fun updateTipPager(position: Int) {
        if (position == lastDotPosition) return
        lastDotPosition = position
        val tips = vm.tips
        binding.tvTipPager.text = "${position + 1}/${tips.size}"
        val activeBg = ContextCompat.getDrawable(requireContext(), R.drawable.dot_active)
        val inactiveBg = ContextCompat.getDrawable(requireContext(), R.drawable.dot_inactive)
        for (i in 0 until binding.dotIndicators.childCount) {
            binding.dotIndicators.getChildAt(i).background =
                if (i == position) activeBg else inactiveBg
        }
    }

    private fun createCircle(@ColorRes colorRes: Int): android.graphics.drawable.Drawable {
        val drawable = android.graphics.drawable.GradientDrawable()
        drawable.shape = android.graphics.drawable.GradientDrawable.OVAL
        drawable.setColor(ContextCompat.getColor(requireContext(), colorRes))
        return drawable
    }

    private fun addDiseaseGrid(parent: LinearLayout, items: List<DiseaseEntry>) {
        val gap = resources.getDimensionPixelSize(R.dimen.space_6)
        val rowMarginTop = resources.getDimensionPixelSize(R.dimen.space_12)

        var row: LinearLayout? = null
        items.forEachIndexed { index, entry ->
            if (index % 2 == 0) {
                row = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        if (parent.childCount > 0) topMargin = rowMarginTop
                    }
                    isBaselineAligned = false
                }
                parent.addView(row)
            }
            val tileBinding = ItemDiseaseTileBinding.inflate(layoutInflater, row, false)
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (index % 2 == 0) marginEnd = gap else marginStart = gap
            }
            tileBinding.root.layoutParams = lp
            tileBinding.ivDiseaseThumb.setImageResource(DiseaseInfo.get(entry.name).thumbRes)
            tileBinding.tvDiseaseName.text = entry.name
            tileBinding.dotSeverity.background =
                ContextCompat.getDrawable(requireContext(), entry.severityDot)
            tileBinding.tvSeverity.text = getString(
                R.string.severity_dot_format,
                getString(entry.severityRes),
                getString(entry.categoryRes)
            )
            tileBinding.cardDisease.setOnClickListener { openDisease(entry.name) }
            tileBinding.cardDisease.bounceOnPress()
            row?.addView(tileBinding.root)

            if (!Motion.reduced(requireContext())) {
                val tile = tileBinding.root
                tile.primeScaleFadeUp(translationDp = 16f)
                tile.entranceScaleFadeUp(
                    delay = index * 40L,
                    duration = 500L
                )
            }

            if (index == items.lastIndex && index % 2 == 0) {
                val spacer = View(requireContext())
                spacer.layoutParams = LinearLayout.LayoutParams(0, 0, 1f).apply {
                    marginStart = gap
                }
                row?.addView(spacer)
            }
        }
    }

    private fun openDisease(name: String) {
        findNavController().navigate(
            R.id.action_diseaseLibrary_to_historyDetails,
            bundleOf("diseaseName" to name)
        )
    }

    private fun buildLibrarySystemPrompt(): String {
        val state = vm.snapshot()
        val all = state.allDetected + state.allOther
        val countsByCategory = all.groupBy { getString(it.categoryRes) }
            .mapValues { (_, list) -> list.map { it.name } }
        val catalog = countsByCategory.entries.joinToString("\n") { (cat, names) ->
            "- $cat (${names.size}): ${names.joinToString(", ")}"
        }
        val selectedLine = state.selectedCategoryRes?.let {
            "The user has filtered the library to the \"${getString(it)}\" category."
        } ?: "The user is browsing all categories."
        return """
You are LeafBloom AI, a friendly plant health assistant inside the LeafBloom app.
The user is on the Disease Library screen, browsing the catalog of plant diseases the app knows about — not a specific scan result.
$selectedLine

Catalog the user can see (grouped by category):
$catalog

Guidelines:
- Answer in the SAME LANGUAGE the user writes in.
- Help the user explore, compare, and learn about diseases in the catalog above (symptoms, causes, severity, treatment, prevention, lookalikes).
- If asked about a disease NOT in the catalog, say so briefly, then answer with general horticultural knowledge.
- Match response length to the question: keep casual chat short (1-2 sentences); only expand when the user asks for steps, comparisons, or full explanations.
- Focus on practical, actionable advice for home gardeners. You may suggest organic or chemical options available in local markets.
- Do not diagnose a real plant from text descriptions alone — recommend the user run a scan from the home screen.
        """.trimIndent()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        lastFocusActive = null
        lastEmptyState = null
    }
}
