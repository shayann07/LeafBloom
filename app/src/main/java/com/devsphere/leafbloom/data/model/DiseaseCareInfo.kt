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
            ),
            "Bacterial Spot" to DiseaseCareInfo(
                water = "Base only",
                sunlight = "Full sun",
                fertilizer = "Balanced",
                humidity = "< 60%",
                overviewRes = R.string.care_overview_bacterial_spot,
                treatmentRes = R.string.care_treatment_bacterial_spot,
                preventionRes = R.string.care_prevention_bacterial_spot
            ),
            "Bacterial Canker" to DiseaseCareInfo(
                water = "Base only",
                sunlight = "Full sun",
                fertilizer = "Balanced",
                humidity = "< 65%",
                overviewRes = R.string.care_overview_bacterial_canker,
                treatmentRes = R.string.care_treatment_bacterial_canker,
                preventionRes = R.string.care_prevention_bacterial_canker
            ),
            "Bacterial Wilt" to DiseaseCareInfo(
                water = "Moderate",
                sunlight = "Full sun",
                fertilizer = "Low-N",
                humidity = "< 70%",
                overviewRes = R.string.care_overview_bacterial_wilt,
                treatmentRes = R.string.care_treatment_bacterial_wilt,
                preventionRes = R.string.care_prevention_bacterial_wilt
            ),
            "Mosaic Virus" to DiseaseCareInfo(
                water = "Regular",
                sunlight = "Full sun",
                fertilizer = "Balanced",
                humidity = "50–70%",
                overviewRes = R.string.care_overview_mosaic_virus,
                treatmentRes = R.string.care_treatment_mosaic_virus,
                preventionRes = R.string.care_prevention_mosaic_virus
            ),
            "Yellow Leaf Curl" to DiseaseCareInfo(
                water = "Regular",
                sunlight = "Full sun",
                fertilizer = "Balanced",
                humidity = "50–70%",
                overviewRes = R.string.care_overview_yellow_leaf_curl,
                treatmentRes = R.string.care_treatment_yellow_leaf_curl,
                preventionRes = R.string.care_prevention_yellow_leaf_curl
            ),
            "Spotted Wilt" to DiseaseCareInfo(
                water = "Regular",
                sunlight = "Full sun",
                fertilizer = "Balanced",
                humidity = "50–70%",
                overviewRes = R.string.care_overview_spotted_wilt,
                treatmentRes = R.string.care_treatment_spotted_wilt,
                preventionRes = R.string.care_prevention_spotted_wilt
            ),
            "Powdery Mildew" to DiseaseCareInfo(
                water = "Morning",
                sunlight = "Full sun",
                fertilizer = "Low-N",
                humidity = "< 70%",
                overviewRes = R.string.care_overview_powdery_mildew,
                treatmentRes = R.string.care_treatment_powdery_mildew,
                preventionRes = R.string.care_prevention_powdery_mildew
            ),
            "Anthracnose" to DiseaseCareInfo(
                water = "Base only",
                sunlight = "Full sun",
                fertilizer = "Balanced",
                humidity = "< 60%",
                overviewRes = R.string.care_overview_anthracnose,
                treatmentRes = R.string.care_treatment_anthracnose,
                preventionRes = R.string.care_prevention_anthracnose
            ),
            "Fusarium Wilt" to DiseaseCareInfo(
                water = "Moderate",
                sunlight = "Full sun",
                fertilizer = "Low-N",
                humidity = "50–70%",
                overviewRes = R.string.care_overview_fusarium_wilt,
                treatmentRes = R.string.care_treatment_fusarium_wilt,
                preventionRes = R.string.care_prevention_fusarium_wilt
            ),
            "Verticillium Wilt" to DiseaseCareInfo(
                water = "Regular",
                sunlight = "Full sun",
                fertilizer = "Balanced",
                humidity = "50–70%",
                overviewRes = R.string.care_overview_verticillium_wilt,
                treatmentRes = R.string.care_treatment_verticillium_wilt,
                preventionRes = R.string.care_prevention_verticillium_wilt
            ),
            "Gray Mold" to DiseaseCareInfo(
                water = "Morning",
                sunlight = "Full sun",
                fertilizer = "Balanced",
                humidity = "< 85%",
                overviewRes = R.string.care_overview_gray_mold,
                treatmentRes = R.string.care_treatment_gray_mold,
                preventionRes = R.string.care_prevention_gray_mold
            ),
            "Aphids" to DiseaseCareInfo(
                water = "Regular",
                sunlight = "Full sun",
                fertilizer = "Balanced",
                humidity = "50–70%",
                overviewRes = R.string.care_overview_aphids,
                treatmentRes = R.string.care_treatment_aphids,
                preventionRes = R.string.care_prevention_aphids
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
