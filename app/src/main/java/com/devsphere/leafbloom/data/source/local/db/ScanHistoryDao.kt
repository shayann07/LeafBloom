package com.devsphere.leafbloom.data.source.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanHistoryDao {

    @Query("SELECT * FROM scan_history ORDER BY timestampMs DESC")
    fun observeAll(): Flow<List<ScanHistoryEntity>>

    @Query("SELECT * FROM scan_history ORDER BY timestampMs DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ScanHistoryEntity>>

    @Query("SELECT * FROM scan_history WHERE id = :id")
    suspend fun getById(id: Long): ScanHistoryEntity?

    @Query("SELECT * FROM scan_history WHERE scanType = :type ORDER BY timestampMs DESC")
    fun observeByType(type: String): Flow<List<ScanHistoryEntity>>

    @Insert
    suspend fun insert(entity: ScanHistoryEntity): Long

    @Query("DELETE FROM scan_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM scan_history")
    suspend fun deleteAll()
}
