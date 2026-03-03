package com.devsphere.leafbloom.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.devsphere.leafbloom.data.model.PredictionResult
import com.devsphere.leafbloom.data.source.local.PestClassifier


class PestRepository(context: Context) {

    private val classifier = PestClassifier(context)

    suspend fun initialize() {
        classifier.loadModel()
    }

    suspend fun predict(bitmap: Bitmap): PredictionResult {
        val rawScores = classifier.predict(bitmap)

        // The model already outputs softmax probabilities (SoftmaxWrapper in convert_pests.py),
        // so we use the scores directly — no additional softmax needed.
        // Only consider the 12 real pest classes (not the appended Unknown slot at index 12).
        val pestClassCount = 12
        var maxProb = -1f
        var maxIndex = -1

        for (i in 0 until pestClassCount) {
            if (rawScores[i] > maxProb) {
                maxProb = rawScores[i]
                maxIndex = i
            }
        }

        Log.d("PestRepository", "Max softmax probability: $maxProb for index $maxIndex (${getClassName(maxIndex)})")

        // Confidence threshold gate — if max probability is below threshold,
        // classify as Unknown (the image is likely not a pest at all)
        val winnerName = if (maxProb >= CONFIDENCE_THRESHOLD) {
            getClassName(maxIndex)
        } else {
            Log.d("PestRepository", "Confidence $maxProb below threshold $CONFIDENCE_THRESHOLD — returning Unknown")
            "Unknown"
        }

        // Build score map using model probabilities
        val scoreMap = mapOf(
            "Ants" to rawScores[PestClassifier.INDEX_ANTS],
            "Bees" to rawScores[PestClassifier.INDEX_BEES],
            "Beetle" to rawScores[PestClassifier.INDEX_BEETLE],
            "Catterpillar" to rawScores[PestClassifier.INDEX_CATTERPILLAR],
            "Earthworms" to rawScores[PestClassifier.INDEX_EARTHWORMS],
            "Earwig" to rawScores[PestClassifier.INDEX_EARWIG],
            "Grasshopper" to rawScores[PestClassifier.INDEX_GRASSHOPPER],
            "Moth" to rawScores[PestClassifier.INDEX_MOTH],
            "Slug" to rawScores[PestClassifier.INDEX_SLUG],
            "Snail" to rawScores[PestClassifier.INDEX_SNAIL],
            "Wasp" to rawScores[PestClassifier.INDEX_WASP],
            "Weevil" to rawScores[PestClassifier.INDEX_WEEVIL],
            "Unknown" to if (winnerName == "Unknown") 1f else 0f
        )

        return PredictionResult(
            predictedClass = winnerName,
            confidence = if (winnerName == "Unknown") 0f else maxProb,
            scores = scoreMap
        )
    }



    private fun getClassName(index: Int): String {
        return when (index) {
            PestClassifier.INDEX_ANTS -> "Ants"
            PestClassifier.INDEX_BEES -> "Bees"
            PestClassifier.INDEX_BEETLE -> "Beetle"
            PestClassifier.INDEX_CATTERPILLAR -> "Catterpillar"
            PestClassifier.INDEX_EARTHWORMS -> "Earthworms"
            PestClassifier.INDEX_EARWIG -> "Earwig"
            PestClassifier.INDEX_GRASSHOPPER -> "Grasshopper"
            PestClassifier.INDEX_MOTH -> "Moth"
            PestClassifier.INDEX_SLUG -> "Slug"
            PestClassifier.INDEX_SNAIL -> "Snail"
            PestClassifier.INDEX_WASP -> "Wasp"
            PestClassifier.INDEX_WEEVIL -> "Weevil"
            else -> "Unknown"
        }
    }

    companion object {
        /**
         * Minimum softmax probability required to accept a pest prediction.
         * Below this threshold, the image is considered out-of-distribution
         * and classified as "Unknown". A value of 0.60 provides a good
         * balance between rejecting irrelevant images and accepting
         * slightly uncertain but valid pest detections.
         */
        private const val CONFIDENCE_THRESHOLD = 0.60f
    }
}

