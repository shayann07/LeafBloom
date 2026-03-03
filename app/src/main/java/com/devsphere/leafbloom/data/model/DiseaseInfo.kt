package com.devsphere.leafbloom.data.model

import androidx.annotation.StringRes
import com.devsphere.leafbloom.R

/**
 * Metadata for each disease type detected by the ML model.
 * Uses string resource IDs for proper localization support.
 */
data class DiseaseInfo(
    @StringRes val scientificNameRes: Int,
    @StringRes val tip1TitleRes: Int,
    @StringRes val tip1DescRes: Int,
    @StringRes val tip2TitleRes: Int,
    @StringRes val tip2DescRes: Int
) {
    companion object {

        private val DISEASE_MAP: Map<String, DiseaseInfo> = mapOf(
            "Early Blight" to DiseaseInfo(
                scientificNameRes = R.string.disease_scientific_early_blight,
                tip1TitleRes = R.string.disease_tip1_title_early_blight,
                tip1DescRes = R.string.disease_tip1_desc_early_blight,
                tip2TitleRes = R.string.disease_tip2_title_early_blight,
                tip2DescRes = R.string.disease_tip2_desc_early_blight
            ),
            "Healthy" to DiseaseInfo(
                scientificNameRes = R.string.disease_scientific_healthy,
                tip1TitleRes = R.string.disease_tip1_title_healthy,
                tip1DescRes = R.string.disease_tip1_desc_healthy,
                tip2TitleRes = R.string.disease_tip2_title_healthy,
                tip2DescRes = R.string.disease_tip2_desc_healthy
            ),
            "Late Blight" to DiseaseInfo(
                scientificNameRes = R.string.disease_scientific_late_blight,
                tip1TitleRes = R.string.disease_tip1_title_late_blight,
                tip1DescRes = R.string.disease_tip1_desc_late_blight,
                tip2TitleRes = R.string.disease_tip2_title_late_blight,
                tip2DescRes = R.string.disease_tip2_desc_late_blight
            ),
            "Septoria" to DiseaseInfo(
                scientificNameRes = R.string.disease_scientific_septoria,
                tip1TitleRes = R.string.disease_tip1_title_septoria,
                tip1DescRes = R.string.disease_tip1_desc_septoria,
                tip2TitleRes = R.string.disease_tip2_title_septoria,
                tip2DescRes = R.string.disease_tip2_desc_septoria
            )
        )

        private val DEFAULT = DiseaseInfo(
            scientificNameRes = R.string.disease_scientific_unknown,
            tip1TitleRes = R.string.disease_tip1_title_unknown,
            tip1DescRes = R.string.disease_tip1_desc_unknown,
            tip2TitleRes = R.string.disease_tip2_title_unknown,
            tip2DescRes = R.string.disease_tip2_desc_unknown
        )

        fun get(name: String): DiseaseInfo = DISEASE_MAP[name] ?: DEFAULT
    }
}
