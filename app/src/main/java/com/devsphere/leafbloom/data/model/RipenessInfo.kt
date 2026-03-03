package com.devsphere.leafbloom.data.model

import androidx.annotation.StringRes
import com.devsphere.leafbloom.R

/**
 * Metadata for each ripeness stage detected by the ML model.
 * Uses string resource IDs for proper localization support.
 */
data class RipenessInfo(
    @StringRes val statusLabelRes: Int,
    @StringRes val adviceRes: Int,
    val showScientificName: Boolean = true
) {
    companion object {

        private val RIPENESS_MAP: Map<String, RipenessInfo> = mapOf(
            "Ripe Tomato" to RipenessInfo(
                statusLabelRes = R.string.ready_to_harvest,
                adviceRes = R.string.ripeness_advice_ripe,
                showScientificName = true
            ),
            "Unripe Tomato" to RipenessInfo(
                statusLabelRes = R.string.keep_on_vine,
                adviceRes = R.string.ripeness_advice_unripe,
                showScientificName = true
            )
        )

        private val DEFAULT = RipenessInfo(
            statusLabelRes = R.string.needs_inspection,
            adviceRes = R.string.ripeness_advice_unknown,
            showScientificName = false // Don't show tomato scientific name for unknown objects
        )

        fun get(name: String): RipenessInfo = RIPENESS_MAP[name] ?: DEFAULT
    }
}
