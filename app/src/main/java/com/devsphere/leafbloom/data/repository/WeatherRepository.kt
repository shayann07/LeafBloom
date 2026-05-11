package com.devsphere.leafbloom.data.repository

import android.content.Context
import com.devsphere.leafbloom.data.model.WeatherResponse
import com.devsphere.leafbloom.data.remote.WeatherApiService
import com.devsphere.leafbloom.data.remote.WeatherNetwork
import com.devsphere.leafbloom.data.source.local.db.LeafBloomDatabase
import com.devsphere.leafbloom.data.source.local.db.WeatherDao
import com.devsphere.leafbloom.data.source.local.db.WeatherEntity
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

class WeatherRepository(
    private val dao: WeatherDao,
    private val api: WeatherApiService,
    private val gson: Gson,
    private val ttlMs: Long = WeatherEntity.DEFAULT_TTL_MS
) {

    fun observeWeather(): Flow<WeatherEntity?> = dao.observe()

    fun decode(entity: WeatherEntity): WeatherResponse =
        gson.fromJson(entity.responseJson, WeatherResponse::class.java)

    suspend fun refreshIfStale(
        lat: Double,
        lon: Double,
        force: Boolean = false
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val rLat = roundTo4dp(lat)
            val rLon = roundTo4dp(lon)
            val now = System.currentTimeMillis()
            val cached = dao.getOnce()

            val fresh = cached != null &&
                    (now - cached.fetchedAtMs) < ttlMs &&
                    withinKm(cached.lat, cached.lon, rLat, rLon, MAX_REUSE_KM)

            if (!force && fresh) return@withContext Result.success(Unit)

            val response = api.getForecast(rLat, rLon)
            val body = response.body()
            if (!response.isSuccessful || body == null) {
                return@withContext Result.failure(WeatherError.fromHttp(response.code()))
            }

            dao.upsert(
                WeatherEntity(
                    id = WeatherEntity.SINGLE_ROW_ID,
                    lat = rLat,
                    lon = rLon,
                    fetchedAtMs = now,
                    ttlMs = ttlMs,
                    responseJson = gson.toJson(body)
                )
            )
            Result.success(Unit)
        } catch (io: IOException) {
            Result.failure(WeatherError.NoNetwork)
        } catch (e: Exception) {
            Result.failure(WeatherError.Unknown(e))
        }
    }

    companion object {
        private const val MAX_REUSE_KM = 5.0
        private const val EARTH_RADIUS_KM = 6371.0088

        @Volatile
        private var INSTANCE: WeatherRepository? = null

        fun getInstance(context: Context): WeatherRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WeatherRepository(
                    dao = LeafBloomDatabase.getInstance(context).weatherDao(),
                    api = WeatherNetwork.api(context),
                    gson = WeatherNetwork.gson
                ).also { INSTANCE = it }
            }
        }

        internal fun roundTo4dp(value: Double): Double =
            (value * 10_000.0).roundToLong() / 10_000.0

        internal fun withinKm(
            lat1: Double, lon1: Double,
            lat2: Double, lon2: Double,
            km: Double
        ): Boolean = haversineKm(lat1, lon1, lat2, lon2) <= km

        private fun haversineKm(
            lat1: Double, lon1: Double,
            lat2: Double, lon2: Double
        ): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).let { it * it } +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                    sin(dLon / 2).let { it * it }
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return EARTH_RADIUS_KM * c
        }
    }
}
