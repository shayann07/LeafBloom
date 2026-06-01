package com.devsphere.leafbloom.ui.disease

import android.os.Bundle
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedStateRegistryOwner
import com.devsphere.leafbloom.R
import com.devsphere.leafbloom.ui.adapter.CarouselTip
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DiseaseEntry(
    val name: String,
    @StringRes val severityRes: Int,
    @DrawableRes val severityDot: Int,
    @StringRes val categoryRes: Int
)

data class CategoryEntry(
    @StringRes val nameRes: Int,
    val count: Int,
    @DrawableRes val iconRes: Int,
    @ColorRes val cardBgRes: Int,
    @ColorRes val iconBgRes: Int
)

data class LibraryUiState(
    val selectedCategoryRes: Int?,
    val seeAllExpanded: Boolean,
    val tipPosition: Int,
    val categories: List<CategoryEntry>,
    val detectedFiltered: List<DiseaseEntry>,
    val otherFiltered: List<DiseaseEntry>,
    val otherVisible: List<DiseaseEntry>,
    val allDetected: List<DiseaseEntry>,
    val allOther: List<DiseaseEntry>,
    val tips: List<CarouselTip>
)

class DiseaseLibraryViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    val detected: List<DiseaseEntry> = listOf(
        DiseaseEntry("Early Blight", R.string.severity_high, R.drawable.bg_dot_severity_high, R.string.cat_fungal),
        DiseaseEntry("Late Blight", R.string.severity_high, R.drawable.bg_dot_severity_high, R.string.cat_fungal),
        DiseaseEntry("Septoria", R.string.severity_moderate, R.drawable.bg_dot_severity_moderate, R.string.cat_fungal),
        DiseaseEntry("Healthy", R.string.severity_low, R.drawable.bg_dot_severity_low, R.string.cat_fungal)
    )

    val otherAll: List<DiseaseEntry> = listOf(
        DiseaseEntry("Bacterial Spot", R.string.severity_high, R.drawable.bg_dot_severity_high, R.string.cat_bacterial),
        DiseaseEntry("Mosaic Virus", R.string.severity_high, R.drawable.bg_dot_severity_high, R.string.cat_viral),
        DiseaseEntry("Bacterial Canker", R.string.severity_high, R.drawable.bg_dot_severity_high, R.string.cat_bacterial),
        DiseaseEntry("Bacterial Wilt", R.string.severity_high, R.drawable.bg_dot_severity_high, R.string.cat_bacterial),
        DiseaseEntry("Yellow Leaf Curl", R.string.severity_high, R.drawable.bg_dot_severity_high, R.string.cat_viral),
        DiseaseEntry("Spotted Wilt", R.string.severity_moderate, R.drawable.bg_dot_severity_moderate, R.string.cat_viral),
        DiseaseEntry("Powdery Mildew", R.string.severity_moderate, R.drawable.bg_dot_severity_moderate, R.string.cat_fungal),
        DiseaseEntry("Anthracnose", R.string.severity_moderate, R.drawable.bg_dot_severity_moderate, R.string.cat_fungal),
        DiseaseEntry("Fusarium Wilt", R.string.severity_high, R.drawable.bg_dot_severity_high, R.string.cat_fungal),
        DiseaseEntry("Verticillium Wilt", R.string.severity_moderate, R.drawable.bg_dot_severity_moderate, R.string.cat_fungal),
        DiseaseEntry("Gray Mold", R.string.severity_moderate, R.drawable.bg_dot_severity_moderate, R.string.cat_fungal),
        DiseaseEntry("Aphids", R.string.severity_moderate, R.drawable.bg_dot_severity_moderate, R.string.cat_pest)
    )

    val tips: List<CarouselTip> = listOf(
        CarouselTip(R.string.tip_carousel_prune_tag, R.string.tip_carousel_prune_title, R.string.tip_carousel_prune_body, R.drawable.ic_pruning_shears, R.color.tip_prune_container, R.color.tip_prune_accent),
        CarouselTip(R.string.tip_carousel_water_tag, R.string.tip_carousel_water_title, R.string.tip_carousel_water_body, R.drawable.water_drop, R.color.tip_water_container, R.color.tip_water_accent),
        CarouselTip(R.string.tip_carousel_sun_tag, R.string.tip_carousel_sun_title, R.string.tip_carousel_sun_body, R.drawable.temp_icon, R.color.tip_sun_container, R.color.tip_sun_accent)
    )

    private val allCategories: List<CategoryEntry> by lazy {
        val counts = (detected + otherAll).groupingBy { it.categoryRes }.eachCount()
        listOf(
            CategoryEntry(R.string.cat_fungal, counts[R.string.cat_fungal] ?: 0, R.drawable.ic_mushroom_24, R.color.cat_fungal_container, R.color.cat_fungal_icon_bg),
            CategoryEntry(R.string.cat_bacterial, counts[R.string.cat_bacterial] ?: 0, R.drawable.ic_capsule_24, R.color.cat_bacterial_container, R.color.cat_bacterial_icon_bg),
            CategoryEntry(R.string.cat_viral, counts[R.string.cat_viral] ?: 0, R.drawable.ic_virus_24, R.color.cat_viral_container, R.color.cat_viral_icon_bg),
            CategoryEntry(R.string.cat_pest, counts[R.string.cat_pest] ?: 0, R.drawable.ic_bug_24, R.color.cat_pest_container, R.color.cat_pest_icon_bg)
        )
    }

    private val categoryFlow: StateFlow<Int?> =
        savedStateHandle.getStateFlow<Int?>(KEY_CATEGORY, null)
    private val seeAllFlow: StateFlow<Boolean> =
        savedStateHandle.getStateFlow(KEY_SEE_ALL, false)
    private val tipPositionFlow: StateFlow<Int> =
        savedStateHandle.getStateFlow(KEY_TIP_POSITION, 0)

    val state: StateFlow<LibraryUiState> = combine(
        categoryFlow, seeAllFlow, tipPositionFlow
    ) { selected, seeAll, tipPos ->
        buildState(selected, seeAll, tipPos)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        buildState(selectedCategoryRes(), seeAllExpanded(), tipPosition())
    )

    fun selectedCategoryRes(): Int? = savedStateHandle[KEY_CATEGORY]
    fun seeAllExpanded(): Boolean = savedStateHandle[KEY_SEE_ALL] ?: false
    fun tipPosition(): Int = savedStateHandle[KEY_TIP_POSITION] ?: 0

    fun onCategoryClicked(categoryRes: Int) {
        val current = selectedCategoryRes()
        savedStateHandle[KEY_CATEGORY] = if (current == categoryRes) null else categoryRes
    }

    fun toggleSeeAll() {
        savedStateHandle[KEY_SEE_ALL] = !seeAllExpanded()
    }

    fun onTipPositionChanged(position: Int) {
        if (position != tipPosition()) savedStateHandle[KEY_TIP_POSITION] = position
    }

    fun snapshot(): LibraryUiState =
        buildState(selectedCategoryRes(), seeAllExpanded(), tipPosition())

    private fun buildState(selected: Int?, seeAll: Boolean, tipPos: Int): LibraryUiState {
        val detectedFiltered = detected.filter { selected == null || it.categoryRes == selected }
        val otherFiltered = otherAll.filter { selected == null || it.categoryRes == selected }
        val otherVisible = if (seeAll) otherFiltered else otherFiltered.take(2)
        return LibraryUiState(
            selectedCategoryRes = selected,
            seeAllExpanded = seeAll,
            tipPosition = tipPos,
            categories = allCategories,
            detectedFiltered = detectedFiltered,
            otherFiltered = otherFiltered,
            otherVisible = otherVisible,
            allDetected = detected,
            allOther = otherAll,
            tips = tips
        )
    }

    class Factory(
        owner: SavedStateRegistryOwner,
        defaultArgs: Bundle? = null
    ) : AbstractSavedStateViewModelFactory(owner, defaultArgs) {
        override fun <T : ViewModel> create(
            key: String,
            modelClass: Class<T>,
            handle: SavedStateHandle
        ): T {
            require(modelClass.isAssignableFrom(DiseaseLibraryViewModel::class.java)) {
                "Unknown ViewModel class: $modelClass"
            }
            @Suppress("UNCHECKED_CAST")
            return DiseaseLibraryViewModel(handle) as T
        }
    }

    companion object {
        private const val KEY_CATEGORY = "selectedCategoryRes"
        private const val KEY_SEE_ALL = "seeAllExpanded"
        private const val KEY_TIP_POSITION = "tipPosition"
    }
}
