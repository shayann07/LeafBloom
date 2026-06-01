package com.devsphere.leafbloom.data.model

import androidx.annotation.DrawableRes
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
    @StringRes val tip2DescRes: Int,
    @DrawableRes val thumbRes: Int = R.drawable.ic_leaf_24,
    @DrawableRes val headerRes: Int = R.drawable.disease_header
) {
    companion object {

        private val DISEASE_MAP: Map<String, DiseaseInfo> = mapOf(
            "Early Blight" to DiseaseInfo(
                scientificNameRes = R.string.disease_scientific_early_blight,
                tip1TitleRes = R.string.disease_tip1_title_early_blight,
                tip1DescRes = R.string.disease_tip1_desc_early_blight,
                tip2TitleRes = R.string.disease_tip2_title_early_blight,
                tip2DescRes = R.string.disease_tip2_desc_early_blight,
                thumbRes = R.drawable.disease_early_blight,
                headerRes = R.drawable.disease_early_blight_hd
            ),
            "Healthy" to DiseaseInfo(
                scientificNameRes = R.string.disease_scientific_healthy,
                tip1TitleRes = R.string.disease_tip1_title_healthy,
                tip1DescRes = R.string.disease_tip1_desc_healthy,
                tip2TitleRes = R.string.disease_tip2_title_healthy,
                tip2DescRes = R.string.disease_tip2_desc_healthy,
                thumbRes = R.drawable.disease_healthy,
                headerRes = R.drawable.disease_healthy_hd
            ),
            "Late Blight" to DiseaseInfo(
                scientificNameRes = R.string.disease_scientific_late_blight,
                tip1TitleRes = R.string.disease_tip1_title_late_blight,
                tip1DescRes = R.string.disease_tip1_desc_late_blight,
                tip2TitleRes = R.string.disease_tip2_title_late_blight,
                tip2DescRes = R.string.disease_tip2_desc_late_blight,
                thumbRes = R.drawable.disease_late_blight,
                headerRes = R.drawable.disease_late_blight_hd
            ),
            "Septoria" to DiseaseInfo(
                scientificNameRes = R.string.disease_scientific_septoria,
                tip1TitleRes = R.string.disease_tip1_title_septoria,
                tip1DescRes = R.string.disease_tip1_desc_septoria,
                tip2TitleRes = R.string.disease_tip2_title_septoria,
                tip2DescRes = R.string.disease_tip2_desc_septoria,
                thumbRes = R.drawable.disease_septoria,
                headerRes = R.drawable.disease_septoria_hd
            ),
            "Bacterial Spot" to genericInfo(
                R.string.disease_scientific_bacterial_spot,
                R.drawable.disease_bacterial_spot,
                R.drawable.disease_bacterial_spot_hd
            ),
            "Bacterial Canker" to genericInfo(
                R.string.disease_scientific_bacterial_canker,
                R.drawable.disease_bacterial_canker,
                R.drawable.disease_bacterial_canker_hd
            ),
            "Bacterial Wilt" to genericInfo(
                R.string.disease_scientific_bacterial_wilt,
                R.drawable.disease_bacterial_wilt,
                R.drawable.disease_bacterial_wilt_hd
            ),
            "Mosaic Virus" to genericInfo(
                R.string.disease_scientific_mosaic_virus,
                R.drawable.disease_mosaic_virus,
                R.drawable.disease_mosaic_virus_hd
            ),
            "Yellow Leaf Curl" to genericInfo(
                R.string.disease_scientific_yellow_leaf_curl,
                R.drawable.disease_yellow_leaf_curl,
                R.drawable.disease_yellow_leaf_curl_hd
            ),
            "Spotted Wilt" to genericInfo(
                R.string.disease_scientific_spotted_wilt,
                R.drawable.disease_spotted_wilt,
                R.drawable.disease_spotted_wilt_hd
            ),
            "Powdery Mildew" to genericInfo(
                R.string.disease_scientific_powdery_mildew,
                R.drawable.disease_powdery_mildew,
                R.drawable.disease_powdery_mildew_hd
            ),
            "Anthracnose" to genericInfo(
                R.string.disease_scientific_anthracnose,
                R.drawable.disease_anthracnose,
                R.drawable.disease_anthracnose_hd
            ),
            "Fusarium Wilt" to genericInfo(
                R.string.disease_scientific_fusarium_wilt,
                R.drawable.disease_fusarium_wilt,
                R.drawable.disease_fusarium_wilt_hd
            ),
            "Verticillium Wilt" to genericInfo(
                R.string.disease_scientific_verticillium_wilt,
                R.drawable.disease_verticillium_wilt,
                R.drawable.disease_verticillium_wilt_hd
            ),
            "Gray Mold" to genericInfo(
                R.string.disease_scientific_gray_mold,
                R.drawable.disease_gray_mold,
                R.drawable.disease_gray_mold_hd
            ),
            "Aphids" to DiseaseInfo(
                scientificNameRes = R.string.disease_scientific_unknown,
                tip1TitleRes = R.string.disease_tip1_title_generic,
                tip1DescRes = R.string.disease_tip1_desc_generic,
                tip2TitleRes = R.string.disease_tip2_title_generic,
                tip2DescRes = R.string.disease_tip2_desc_generic,
                thumbRes = R.drawable.disease_aphids,
                headerRes = R.drawable.disease_aphids_hd
            )
        )

        private fun genericInfo(
            @StringRes scientificName: Int,
            @DrawableRes thumbRes: Int,
            @DrawableRes headerRes: Int
        ) = DiseaseInfo(
            scientificNameRes = scientificName,
            tip1TitleRes = R.string.disease_tip1_title_generic,
            tip1DescRes = R.string.disease_tip1_desc_generic,
            tip2TitleRes = R.string.disease_tip2_title_generic,
            tip2DescRes = R.string.disease_tip2_desc_generic,
            thumbRes = thumbRes,
            headerRes = headerRes
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
