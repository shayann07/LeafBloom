package com.devsphere.leafbloom.data.model

data class HistoryItem(
    val id: Long,
    val plantName: String,
    val status: String,
    val confidence: Int,
    val date: String,
    val imagePath: String?,
    val isHealthy: Boolean = false,
    val scanType: String = "DIAGNOSE"
)
