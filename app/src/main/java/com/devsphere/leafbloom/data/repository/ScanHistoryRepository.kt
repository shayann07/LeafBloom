package com.devsphere.leafbloom.data.repository

import com.devsphere.leafbloom.data.model.IdentifyResponse
import com.devsphere.leafbloom.data.model.PredictionResult
import com.devsphere.leafbloom.data.source.local.db.ScanHistoryDao
import com.devsphere.leafbloom.data.source.local.db.ScanHistoryEntity
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow

class ScanHistoryRepository(private val dao: ScanHistoryDao) {

    fun observeRecent(limit: Int): Flow<List<ScanHistoryEntity>> = dao.observeRecent(limit)

    fun observeAll(): Flow<List<ScanHistoryEntity>> = dao.observeAll()

    fun observeByType(type: String): Flow<List<ScanHistoryEntity>> = dao.observeByType(type)

    suspend fun getById(id: Long): ScanHistoryEntity? = dao.getById(id)

    suspend fun saveDiagnosis(imagePath: String, result: PredictionResult): Long {
        val entity = ScanHistoryEntity(
            imagePath = imagePath,
            predictedClass = result.predictedClass,
            confidence = result.confidence,
            scoreEarlyBlight = result.scores["Early Blight"] ?: 0f,
            scoreHealthy = result.scores["Healthy"] ?: 0f,
            scoreLateBlight = result.scores["Late Blight"] ?: 0f,
            scoreSeptoria = result.scores["Septoria"] ?: 0f
        )
        return dao.insert(entity)
    }

    suspend fun savePest(imagePath: String, result: PredictionResult): Long {
        val entity = ScanHistoryEntity(
            imagePath = imagePath,
            predictedClass = result.predictedClass,
            confidence = result.confidence,
            scoreEarlyBlight = 0f,
            scoreHealthy = 0f,
            scoreLateBlight = 0f,
            scoreSeptoria = 0f,
            scanType = "PEST"
        )
        return dao.insert(entity)
    }

    suspend fun saveIdentify(imagePath: String, response: IdentifyResponse): Long {
        val bestMatch = response.data?.results?.firstOrNull()
        val entity = ScanHistoryEntity(
            imagePath = imagePath,
            predictedClass = response.data?.bestMatch ?: "Unknown",
            confidence = (bestMatch?.score ?: 0.0).toFloat(),
            scoreEarlyBlight = 0f,
            scoreHealthy = 0f,
            scoreLateBlight = 0f,
            scoreSeptoria = 0f,
            scanType = "IDENTIFY",
            identifyResponseJson = Gson().toJson(response)
        )
        return dao.insert(entity)
    }

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun deleteAll() = dao.deleteAll()
}

