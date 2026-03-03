package com.devsphere.leafbloom.ui.scanner

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devsphere.leafbloom.data.model.PredictionResult
import com.devsphere.leafbloom.data.repository.DiseaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import com.devsphere.leafbloom.data.model.IdentifyResponse
import com.devsphere.leafbloom.data.repository.IdentifyRepository
import com.devsphere.leafbloom.data.source.local.LeafValidator

sealed class ScannerUiState {
    object Idle : ScannerUiState()
    object Loading : ScannerUiState()
    data class SuccessDiagnosis(val result: PredictionResult) : ScannerUiState()
    data class SuccessIdentify(val response: IdentifyResponse, val imageUri: android.net.Uri) : ScannerUiState()
    data class SuccessPest(val result: PredictionResult) : ScannerUiState()
    data class SuccessRipeness(val result: PredictionResult) : ScannerUiState()
    data class Error(val message: String) : ScannerUiState()
}

class ScannerViewModel(
    private val repository: DiseaseRepository,
    private val pestRepository: com.devsphere.leafbloom.data.repository.PestRepository,
    private val ripenessRepository: com.devsphere.leafbloom.data.repository.RipenessRepository,
    private val leafValidator: LeafValidator,
    private val applicationContext: android.content.Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScannerUiState>(ScannerUiState.Idle)
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private val _latestGalleryUri = MutableStateFlow<android.net.Uri?>(null)
    val latestGalleryUri: StateFlow<android.net.Uri?> = _latestGalleryUri.asStateFlow()

    init {
        // ASYNC INIT: Load model in background to avoid main thread freeze
        viewModelScope.launch {
            repository.initialize()
            pestRepository.initialize()
            ripenessRepository.initialize()
            leafValidator.loadModel()
        }
    }
    
    fun loadLatestGalleryImage(contentResolver: android.content.ContentResolver) {
         viewModelScope.launch(Dispatchers.IO) {
            val projection = arrayOf(
                android.provider.MediaStore.Images.ImageColumns._ID,
                android.provider.MediaStore.Images.ImageColumns.DATE_TAKEN
            )
            val sortOrder = "${android.provider.MediaStore.Images.ImageColumns.DATE_TAKEN} DESC"

            try {
                contentResolver.query(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    sortOrder
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Images.ImageColumns._ID)
                        val id = cursor.getLong(idColumn)
                        val contentUri = android.content.ContentUris.withAppendedId(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        _latestGalleryUri.value = contentUri
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ScannerViewModel", "Failed to load gallery image", e)
            }
        }
    }
    fun analyzeImage(bitmap: Bitmap) {
        _uiState.value = ScannerUiState.Loading
        
        viewModelScope.launch {
            try {
                // Determine if we need to squash inside Repo? 
                // Currently Repo is raw. Let's assume Repo handles prediction, 
                // but pre-processing (Squashing) was in Fragment.
                // Ideally Repo should handle data transformation, but we can move it later.
                // For now, let's keep logic simple: Input is Bitmap, Output is Result.
                
                // Switch to IO for heavy inference
                val result = withContext(Dispatchers.IO) {
                    repository.predict(bitmap)
                }
                
                _uiState.value = ScannerUiState.SuccessDiagnosis(result)
                
            } catch (e: Exception) {
                android.util.Log.e("ScannerViewModel", "Error analyzing image", e)
                _uiState.value = ScannerUiState.Error(e.message ?: "Unknown error occurred")
            }
        }
    }

    fun analyzePest(bitmap: Bitmap) {
        _uiState.value = ScannerUiState.Loading
        
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    pestRepository.predict(bitmap)
                }
                
                _uiState.value = ScannerUiState.SuccessPest(result)
                
            } catch (e: Exception) {
                android.util.Log.e("ScannerViewModel", "Error analyzing pest", e)
                _uiState.value = ScannerUiState.Error(e.message ?: "Unknown error occurred")
            }
        }
    }

    fun analyzeRipeness(bitmap: Bitmap) {
        _uiState.value = ScannerUiState.Loading
        
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    ripenessRepository.predict(bitmap)
                }
                
                _uiState.value = ScannerUiState.SuccessRipeness(result)
                
            } catch (e: Exception) {
                android.util.Log.e("ScannerViewModel", "Error analyzing ripeness", e)
                _uiState.value = ScannerUiState.Error(e.message ?: "Unknown error occurred")
            }
        }
    }
    
    fun resetState() {
        _uiState.value = ScannerUiState.Idle
    }

    fun identifyPlant(uri: android.net.Uri) {
        _uiState.value = ScannerUiState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Decode URI to Bitmap
                val bitmap = applicationContext.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it)
                }

                if (bitmap == null) {
                    _uiState.value = ScannerUiState.Error("Failed to decode image.")
                    return@launch
                }

                // 2. Run Local PyTorch Validation
                if (!leafValidator.isValidLeaf(bitmap)) {
                    _uiState.value = ScannerUiState.Error("NOT_A_PLANT")
                    return@launch
                }

                // 3. Convert Uri to Temporary File for Multipart API Call
                val tempFile = File.createTempFile("upload", ".jpg", applicationContext.cacheDir)
                applicationContext.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                // 4. Call Repository API
                val result = IdentifyRepository.identifyPlant(tempFile)

                result.onSuccess {
                    _uiState.value = ScannerUiState.SuccessIdentify(it, uri)
                }.onFailure {
                    _uiState.value = ScannerUiState.Error(it.message ?: "Unknown identification error")
                }

                // Cleanup temp file
                if (tempFile.exists()) tempFile.delete()

            } catch (e: Exception) {
                android.util.Log.e("ScannerViewModel", "Identification pipeline failed", e)
                _uiState.value = ScannerUiState.Error("Identification failed: ${e.message}")
            }
        }
    }

    class Factory(private val application: android.app.Application) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ScannerViewModel::class.java)) {
                val sharedValidator = LeafValidator(application.applicationContext)
                val repository = com.devsphere.leafbloom.data.repository.DiseaseRepository(application, sharedValidator)
                val pestRepo = com.devsphere.leafbloom.data.repository.PestRepository(application)
                val ripenessRepo = com.devsphere.leafbloom.data.repository.RipenessRepository(application)
                return ScannerViewModel(repository, pestRepo, ripenessRepo, sharedValidator, application.applicationContext) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
