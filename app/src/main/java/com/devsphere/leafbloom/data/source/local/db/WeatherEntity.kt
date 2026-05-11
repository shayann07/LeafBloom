package com.devsphere.leafbloom.data.source.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weather_cache")
data class WeatherEntity(
    @PrimaryKey val id: Int = 1,
    val lat: Double,
    val lon: Double,
    val fetchedAtMs: Long,
    val ttlMs: Long = DEFAULT_TTL_MS,
    val unitSystem: String = "metric",
    val responseJson: String
) {
    companion object {
        const val DEFAULT_TTL_MS: Long = 30L * 60L * 1000L
        const val SINGLE_ROW_ID: Int = 1
    }
}
