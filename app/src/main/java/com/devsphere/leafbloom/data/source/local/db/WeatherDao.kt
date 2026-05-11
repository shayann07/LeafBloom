package com.devsphere.leafbloom.data.source.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WeatherDao {

    @Query("SELECT * FROM weather_cache WHERE id = 1 LIMIT 1")
    fun observe(): Flow<WeatherEntity?>

    @Query("SELECT * FROM weather_cache WHERE id = 1 LIMIT 1")
    suspend fun getOnce(): WeatherEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WeatherEntity)

    @Query("DELETE FROM weather_cache")
    suspend fun clear()
}
