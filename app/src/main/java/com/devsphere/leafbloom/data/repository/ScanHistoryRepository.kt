package com.devsphere.leafbloom.data.repository

import android.content.Context
import com.devsphere.leafbloom.data.model.HistoryItem
import com.devsphere.leafbloom.data.model.IdentifyResponse
import com.devsphere.leafbloom.data.model.PestInfo
import com.devsphere.leafbloom.data.model.PredictionResult
import com.devsphere.leafbloom.data.source.local.db.LeafBloomDatabase
import com.devsphere.leafbloom.data.source.local.db.ScanHistoryDao
import com.devsphere.leafbloom.data.source.local.db.ScanHistoryEntity
import com.devsphere.leafbloom.util.DateUtils
import com.devsphere.leafbloom.util.ImageStorage
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Single source of truth for scan history reads/writes.
 *
 * Main-safety: all JSON decode + grouping happen on Dispatchers.Default via
 * [observeMapped] / [decodeIdentify]. Disk writes (delete + image cleanup)
 * use Dispatchers.IO via [deleteWithImage]. Room DAO calls dispatch
 * internally so no wrapper is needed for them.
 */
class ScanHistoryRepository(private val dao: ScanHistoryDao) {

    private val gson = Gson()

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
            identifyResponseJson = gson.toJson(response)
        )
        return dao.insert(entity)
    }

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun deleteAll() = dao.deleteAll()

    /** Off-main decode of the stored IDENTIFY JSON for a single entity. */
    suspend fun decodeIdentify(entity: ScanHistoryEntity): IdentifyResponse? =
        withContext(Dispatchers.Default) {
            entity.identifyResponseJson?.let { gson.fromJson(it, IdentifyResponse::class.java) }
        }

    /** Convenience: decode IDENTIFY JSON by scanId. */
    suspend fun decodeIdentifyById(id: Long): IdentifyResponse? {
        val entity = dao.getById(id) ?: return null
        return decodeIdentify(entity)
    }

    /**
     * Observe scans of [type] already decoded into UI-ready rows.
     * JSON parsing + section-label resolution happen on Dispatchers.Default.
     */
    fun observeMapped(type: String, context: Context): Flow<List<MappedScanRow>> {
        val appContext = context.applicationContext
        return dao.observeByType(type)
            .map { entities -> entities.map { it.toMappedRow(appContext) } }
            .flowOn(Dispatchers.Default)
    }

    /** Observe scans across all types, decoded into UI-ready rows. */
    fun observeAllMapped(context: Context): Flow<List<MappedScanRow>> {
        val appContext = context.applicationContext
        return dao.observeAll()
            .map { entities -> entities.map { it.toMappedRow(appContext) } }
            .flowOn(Dispatchers.Default)
    }

    /** Delete a scan + its image file on Dispatchers.IO. */
    suspend fun deleteWithImage(id: Long, imagePath: String?) = withContext(Dispatchers.IO) {
        dao.deleteById(id)
        imagePath?.let { ImageStorage.delete(it) }
    }

    private fun ScanHistoryEntity.toMappedRow(context: Context): MappedScanRow {
        val isHealthy = predictedClass.equals("Healthy", ignoreCase = true)
        var displayName = predictedClass
        var identifyCommonName: String? = null

        val status: String = when (scanType) {
            "PEST" -> {
                val info = PestInfo.get(predictedClass)
                runCatching { context.getString(info.threatLevelRes) }.getOrDefault("Unknown")
            }
            "IDENTIFY" -> {
                identifyResponseJson?.let { json ->
                    runCatching {
                        val response = gson.fromJson(json, IdentifyResponse::class.java)
                        val common = response.data?.results?.firstOrNull()?.commonNames?.firstOrNull()
                        if (!common.isNullOrBlank()) {
                            identifyCommonName = common
                            displayName = common.replaceFirstChar { c ->
                                if (c.isLowerCase()) c.titlecase(Locale.ROOT) else c.toString()
                            }
                        }
                        "Identified"
                    }.getOrDefault("Unknown")
                } ?: "Unknown"
            }
            else -> if (isHealthy) "Healthy" else "Infected"
        }

        val item = HistoryItem(
            id = id,
            plantName = displayName,
            status = status,
            confidence = (confidence * 100).toInt(),
            date = DateUtils.getTimeOnly(timestampMs),
            imagePath = imagePath,
            isHealthy = isHealthy,
            scanType = scanType
        )
        return MappedScanRow(
            item = item,
            sectionLabel = DateUtils.getSectionLabel(context, timestampMs),
            identifyCommonName = identifyCommonName
        )
    }

    companion object {
        @Volatile
        private var INSTANCE: ScanHistoryRepository? = null

        fun getInstance(context: Context): ScanHistoryRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ScanHistoryRepository(
                    LeafBloomDatabase.getInstance(context.applicationContext).scanHistoryDao()
                ).also { INSTANCE = it }
            }
        }
    }
}

/** UI-ready row produced by [ScanHistoryRepository.observeMapped]. */
data class MappedScanRow(
    val item: HistoryItem,
    val sectionLabel: String,
    val identifyCommonName: String?
)
