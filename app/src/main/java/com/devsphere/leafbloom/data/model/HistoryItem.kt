package com.devsphere.leafbloom.data.model

data class HistoryItem(
    val plantName: String,
    val status: String,
    val confidence: Int,
    val date: String,
    val imageResId: Int,
    val isHealthy: Boolean = true
)
