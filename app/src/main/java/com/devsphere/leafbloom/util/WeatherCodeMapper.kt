package com.devsphere.leafbloom.util

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.devsphere.leafbloom.R

/**
 * Maps WMO weather codes (Open-Meteo) to drawable + localized condition strings.
 * Reference: https://open-meteo.com/en/docs (Weather variable documentation, WMO Weather interpretation codes).
 */
object WeatherCodeMapper {

    data class Visual(
        @DrawableRes val iconRes: Int,
        @StringRes val labelRes: Int
    )

    fun visualFor(code: Int, isDay: Boolean): Visual = when (code) {
        0 -> Visual(
            if (isDay) R.drawable.weather_sun else R.drawable.weather_moon,
            R.string.weather_clear
        )

        1, 2 -> Visual(R.drawable.weather_partly_cloudy, R.string.weather_partly_cloudy)
        3 -> Visual(R.drawable.weather_cloud, R.string.weather_cloudy)

        45, 48 -> Visual(R.drawable.weather_fog, R.string.weather_fog)

        51, 53, 55 -> Visual(R.drawable.weather_drizzle, R.string.weather_drizzle)
        56, 57 -> Visual(R.drawable.weather_freezing_rain, R.string.weather_freezing_rain)

        61, 63, 65 -> Visual(R.drawable.weather_rain, R.string.weather_rain)
        66, 67 -> Visual(R.drawable.weather_freezing_rain, R.string.weather_freezing_rain)

        71, 73, 75, 77 -> Visual(R.drawable.weather_snow, R.string.weather_snow)

        80, 81, 82 -> Visual(R.drawable.weather_rain, R.string.weather_showers)
        85, 86 -> Visual(R.drawable.weather_snow, R.string.weather_snow_showers)

        95 -> Visual(R.drawable.weather_storm, R.string.weather_storm)
        96, 99 -> Visual(R.drawable.weather_storm_hail, R.string.weather_storm_hail)

        else -> Visual(R.drawable.weather_cloud, R.string.weather_unknown)
    }

    @DrawableRes
    fun iconFor(code: Int, isDay: Boolean): Int = visualFor(code, isDay).iconRes

    @StringRes
    fun labelFor(code: Int, isDay: Boolean): Int = visualFor(code, isDay).labelRes
}
