package com.devsphere.leafbloom.data.model

data class PredictionResult(
    val predictedClass: String,
    val confidence: Float,
    val scores: Map<String, Float>
)
