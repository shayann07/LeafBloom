package com.devsphere.leafbloom.data.source.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_history")
data class ScanHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val imagePath: String,
    val predictedClass: String,
    val confidence: Float,
    val scoreEarlyBlight: Float,
    val scoreHealthy: Float,
    val scoreLateBlight: Float,
    val scoreSeptoria: Float,
    val scanType: String = "DIAGNOSE",
    val identifyResponseJson: String? = null,
    val timestampMs: Long = System.currentTimeMillis()
)
