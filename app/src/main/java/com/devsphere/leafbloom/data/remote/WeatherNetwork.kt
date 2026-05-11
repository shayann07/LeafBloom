package com.devsphere.leafbloom.data.remote

import android.content.Context
import com.google.gson.Gson
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

object WeatherNetwork {

    private const val CACHE_DIR = "weather_http_cache"
    private const val CACHE_SIZE_BYTES = 10L * 1024L * 1024L
    private const val TIMEOUT_SECONDS = 30L

    @Volatile
    private var apiService: WeatherApiService? = null

    @Volatile
    private var gsonInstance: Gson? = null

    val gson: Gson
        get() = gsonInstance ?: synchronized(this) {
            gsonInstance ?: Gson().also { gsonInstance = it }
        }

    fun api(context: Context): WeatherApiService {
        return apiService ?: synchronized(this) {
            apiService ?: buildApi(context.applicationContext).also { apiService = it }
        }
    }

    private fun buildApi(appContext: Context): WeatherApiService {
        val cache = Cache(File(appContext.cacheDir, CACHE_DIR), CACHE_SIZE_BYTES)

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .cache(cache)
            .addInterceptor(logging)
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(WeatherApiService.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(WeatherApiService::class.java)
    }
}
