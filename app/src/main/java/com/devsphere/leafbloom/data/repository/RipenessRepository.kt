package com.devsphere.leafbloom.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.devsphere.leafbloom.data.model.PredictionResult
import com.devsphere.leafbloom.data.source.local.RipenessClassifier

class RipenessRepository(context: Context) {

    private val classifier = RipenessClassifier(context)

    suspend fun initialize() {
        classifier.loadModel()
    }

    suspend fun predict(bitmap: Bitmap): PredictionResult {
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
            "Ripe" to scores[RipenessClassifier.INDEX_RIPE],
            "Unknown" to scores[RipenessClassifier.INDEX_UNKNOWN],
            "Unripe" to scores[RipenessClassifier.INDEX_UNRIPE]
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
            RipenessClassifier.INDEX_RIPE -> "Ripe"
            RipenessClassifier.INDEX_UNRIPE -> "Unripe"
            else -> "Unknown"
        }
    }
}
