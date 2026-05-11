package com.devsphere.leafbloom.data.model

import com.google.gson.annotations.SerializedName

data class WeatherResponse(
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("timezone") val timezone: String?,
    @SerializedName("timezone_abbreviation") val timezoneAbbreviation: String?,
    @SerializedName("utc_offset_seconds") val utcOffsetSeconds: Int?,
    @SerializedName("elevation") val elevation: Double?,
    @SerializedName("current") val current: CurrentWeather,
    @SerializedName("current_units") val currentUnits: Map<String, String>? = null,
    @SerializedName("hourly") val hourly: HourlyWeather? = null,
    @SerializedName("hourly_units") val hourlyUnits: Map<String, String>? = null,
    @SerializedName("daily") val daily: DailyWeather? = null,
    @SerializedName("daily_units") val dailyUnits: Map<String, String>? = null
)

data class CurrentWeather(
    @SerializedName("time") val time: String,
    @SerializedName("interval") val interval: Int? = null,
    @SerializedName("temperature_2m") val temperature2m: Double,
    @SerializedName("relative_humidity_2m") val relativeHumidity2m: Int,
    @SerializedName("apparent_temperature") val apparentTemperature: Double,
    @SerializedName("is_day") val isDay: Int,
    @SerializedName("weather_code") val weatherCode: Int,
    @SerializedName("wind_speed_10m") val windSpeed10m: Double
)

data class HourlyWeather(
    @SerializedName("time") val time: List<String>,
    @SerializedName("uv_index") val uvIndex: List<Double?>? = null,
    @SerializedName("soil_temperature_0cm") val soilTemperature0cm: List<Double?>? = null,
    @SerializedName("soil_moisture_0_to_1cm") val soilMoisture0to1cm: List<Double?>? = null,
    @SerializedName("vapour_pressure_deficit") val vapourPressureDeficit: List<Double?>? = null,
    @SerializedName("et0_fao_evapotranspiration") val et0FaoEvapotranspiration: List<Double?>? = null,
    @SerializedName("precipitation_probability") val precipitationProbability: List<Int?>? = null
)

data class DailyWeather(
    @SerializedName("time") val time: List<String>,
    @SerializedName("temperature_2m_max") val temperature2mMax: List<Double?>? = null,
    @SerializedName("temperature_2m_min") val temperature2mMin: List<Double?>? = null,
    @SerializedName("uv_index_max") val uvIndexMax: List<Double?>? = null,
    @SerializedName("precipitation_sum") val precipitationSum: List<Double?>? = null,
    @SerializedName("precipitation_probability_max") val precipitationProbabilityMax: List<Int?>? = null,
    @SerializedName("sunrise") val sunrise: List<String?>? = null,
    @SerializedName("sunset") val sunset: List<String?>? = null,
    @SerializedName("leaf_wetness_probability_mean") val leafWetnessProbabilityMean: List<Double?>? = null,
    @SerializedName("growing_degree_days_base_0_limit_50") val growingDegreeDays: List<Double?>? = null
)
