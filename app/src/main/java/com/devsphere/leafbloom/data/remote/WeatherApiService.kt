package com.devsphere.leafbloom.data.remote

import com.devsphere.leafbloom.data.model.WeatherResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApiService {

    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("current") current: String = DEFAULT_CURRENT,
        @Query("hourly") hourly: String = DEFAULT_HOURLY,
        @Query("daily") daily: String = DEFAULT_DAILY,
        @Query("timezone") timezone: String = "auto",
        @Query("models") models: String = "best_match",
        @Query("cell_selection") cellSelection: String = "land",
        @Query("forecast_days") forecastDays: Int = 3
    ): Response<WeatherResponse>

    companion object {
        const val BASE_URL = "https://api.open-meteo.com/"

        const val DEFAULT_CURRENT =
            "temperature_2m,relative_humidity_2m,apparent_temperature," +
                    "is_day,weather_code,wind_speed_10m"

        const val DEFAULT_HOURLY =
            "uv_index,soil_temperature_0cm,soil_moisture_0_to_1cm," +
                    "vapour_pressure_deficit,et0_fao_evapotranspiration," +
                    "precipitation_probability"

        const val DEFAULT_DAILY =
            "temperature_2m_max,temperature_2m_min,uv_index_max," +
                    "precipitation_sum,precipitation_probability_max,sunrise,sunset," +
                    "leaf_wetness_probability_mean," +
                    "growing_degree_days_base_0_limit_50"
    }
}
