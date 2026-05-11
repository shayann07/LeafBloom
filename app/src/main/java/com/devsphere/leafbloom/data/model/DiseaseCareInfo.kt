package com.devsphere.leafbloom.data.model

import androidx.annotation.StringRes
import com.devsphere.leafbloom.R

/**
 * Per-disease-class care data for the History Details screen.
 * Values sourced from UMN Extension, USDA, Almanac, K-State Extension.
 *
 * Provides:
 * - Symptom card values (Water, Sunlight, Fertilizer, Humidity)
 * - Overview paragraph
 * - Treatment paragraph
 * - Prevention paragraph
 */
data class DiseaseCareInfo(
    val water: String,
    val sunlight: String,
    val fertilizer: String,
    val humidity: String,
    @StringRes val overviewRes: Int,
    @StringRes val treatmentRes: Int,
    @StringRes val preventionRes: Int
) {
    companion object {

        private val CARE_MAP: Map<String, DiseaseCareInfo> = mapOf(
            "Early Blight" to DiseaseCareInfo(
                water = "250 ml",
                sunlight = "6–8h",
                fertilizer = "Low-N",
                humidity = "< 60%",
                overviewRes = R.string.care_overview_early_blight,
                treatmentRes = R.string.care_treatment_early_blight,
                preventionRes = R.string.care_prevention_early_blight
            ),
            "Healthy" to DiseaseCareInfo(
                water = "300 ml",
                sunlight = "6–8h",
                fertilizer = "NPK",
                humidity = "50–70%",
                overviewRes = R.string.care_overview_healthy,
                treatmentRes = R.string.care_treatment_healthy,
                preventionRes = R.string.care_prevention_healthy
            ),
            "Late Blight" to DiseaseCareInfo(
                water = "200 ml",
                sunlight = "6–8h",
                fertilizer = "Copper",
                humidity = "< 50%",
                overviewRes = R.string.care_overview_late_blight,
                treatmentRes = R.string.care_treatment_late_blight,
                preventionRes = R.string.care_prevention_late_blight
            ),
            "Septoria" to DiseaseCareInfo(
                water = "250 ml",
                sunlight = "6–8h",
                fertilizer = "Copper",
                humidity = "< 60%",
                overviewRes = R.string.care_overview_septoria,
                treatmentRes = R.string.care_treatment_septoria,
                preventionRes = R.string.care_prevention_septoria
            )
        )

        private val DEFAULT = DiseaseCareInfo(
            water = "250 ml",
            sunlight = "6–8h",
            fertilizer = "Balanced",
            humidity = "50–70%",
            overviewRes = R.string.care_overview_unknown,
            treatmentRes = R.string.care_treatment_unknown,
            preventionRes = R.string.care_prevention_unknown
        )

        fun get(predictedClass: String): DiseaseCareInfo =
            CARE_MAP[predictedClass] ?: DEFAULT
    }
}
