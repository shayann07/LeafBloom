package com.devsphere.leafbloom.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.devsphere.leafbloom.data.model.PredictionResult
import com.devsphere.leafbloom.data.source.local.DiseaseClassifier

class DiseaseRepository(context: Context) {
    

    private val classifier = DiseaseClassifier(context)

    suspend fun initialize() {
        classifier.loadModel()
    }

    fun predict(bitmap: Bitmap): PredictionResult {
        // Run inference
        val scores = classifier.predict(bitmap)
        
        // Find max
        var maxScore = -1f
        var maxIndex = -1
        
        for (i in scores.indices) {
            if (scores[i] > maxScore) {
                maxScore = scores[i]
                maxIndex = i
            }
        }
        
        // Map scores to names
        val scoreMap = mapOf(
            "Unknown" to scores[DiseaseClassifier.INDEX_UNKNOWN],
            "Early Blight" to scores[DiseaseClassifier.INDEX_EARLY_BLIGHT],
            "Healthy" to scores[DiseaseClassifier.INDEX_HEALTHY],
            "Late Blight" to scores[DiseaseClassifier.INDEX_LATE_BLIGHT],
            "Septoria" to scores[DiseaseClassifier.INDEX_SEPTORIA]
        )
        
        val winnerName = getClassName(maxIndex)
        
        return PredictionResult(
            predictedClass = winnerName,
            confidence = maxScore,
            scores = scoreMap
        )
    }
    
    private fun getClassName(index: Int): String {
        return when (index) {
            DiseaseClassifier.INDEX_EARLY_BLIGHT -> "Early Blight"
            DiseaseClassifier.INDEX_HEALTHY -> "Healthy"
            DiseaseClassifier.INDEX_LATE_BLIGHT -> "Late Blight"
            DiseaseClassifier.INDEX_SEPTORIA -> "Septoria"
            else -> "Unknown"
        }
    }
}
