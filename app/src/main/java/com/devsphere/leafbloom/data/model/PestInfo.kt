package com.devsphere.leafbloom.data.model

import androidx.annotation.StringRes
import com.devsphere.leafbloom.R

/**
 * Metadata for each pest type detected by the ML model.
 * Uses string resource IDs for proper localization support.
 */
data class PestInfo(
    @StringRes val speciesRes: Int,
    @StringRes val threatLevelRes: Int,
    @StringRes val adviceRes: Int
) {
    companion object {

        private val PEST_MAP: Map<String, PestInfo> = mapOf(
            "Ants" to PestInfo(
                speciesRes = R.string.pest_species_ants,
                threatLevelRes = R.string.threat_level_moderate,
                adviceRes = R.string.pest_advice_ants
            ),
            "Bees" to PestInfo(
                speciesRes = R.string.pest_species_bees,
                threatLevelRes = R.string.threat_level_low,
                adviceRes = R.string.pest_advice_bees
            ),
            "Beetle" to PestInfo(
                speciesRes = R.string.pest_species_beetle,
                threatLevelRes = R.string.threat_level_moderate,
                adviceRes = R.string.pest_advice_beetle
            ),
            "Catterpillar" to PestInfo(
                speciesRes = R.string.pest_species_caterpillar,
                threatLevelRes = R.string.threat_level_high,
                adviceRes = R.string.pest_advice_caterpillar
            ),
            "Earthworms" to PestInfo(
                speciesRes = R.string.pest_species_earthworms,
                threatLevelRes = R.string.threat_level_low,
                adviceRes = R.string.pest_advice_earthworms
            ),
            "Earwig" to PestInfo(
                speciesRes = R.string.pest_species_earwig,
                threatLevelRes = R.string.threat_level_low,
                adviceRes = R.string.pest_advice_earwig
            ),
            "Grasshopper" to PestInfo(
                speciesRes = R.string.pest_species_grasshopper,
                threatLevelRes = R.string.threat_level_high,
                adviceRes = R.string.pest_advice_grasshopper
            ),
            "Moth" to PestInfo(
                speciesRes = R.string.pest_species_moth,
                threatLevelRes = R.string.threat_level_moderate,
                adviceRes = R.string.pest_advice_moth
            ),
            "Slug" to PestInfo(
                speciesRes = R.string.pest_species_slug,
                threatLevelRes = R.string.threat_level_high,
                adviceRes = R.string.pest_advice_slug
            ),
            "Snail" to PestInfo(
                speciesRes = R.string.pest_species_snail,
                threatLevelRes = R.string.threat_level_high,
                adviceRes = R.string.pest_advice_snail
            ),
            "Wasp" to PestInfo(
                speciesRes = R.string.pest_species_wasp,
                threatLevelRes = R.string.threat_level_low,
                adviceRes = R.string.pest_advice_wasp
            ),
            "Weevil" to PestInfo(
                speciesRes = R.string.pest_species_weevil,
                threatLevelRes = R.string.threat_level_high,
                adviceRes = R.string.pest_advice_weevil
            )
        )

        private val DEFAULT = PestInfo(
            speciesRes = R.string.pest_species_unknown,
            threatLevelRes = R.string.threat_level_unknown,
            adviceRes = R.string.pest_advice_unknown
        )

        fun get(name: String): PestInfo = PEST_MAP[name] ?: DEFAULT
    }
}
