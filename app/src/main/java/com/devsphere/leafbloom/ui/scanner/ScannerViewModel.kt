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

sealed class ScannerUiState {
    object Idle : ScannerUiState()
    object Loading : ScannerUiState()
    data class Success(val result: PredictionResult) : ScannerUiState()
    data class Error(val message: String) : ScannerUiState()
}

class ScannerViewModel(private val repository: DiseaseRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<ScannerUiState>(ScannerUiState.Idle)
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private val _latestGalleryUri = MutableStateFlow<android.net.Uri?>(null)
    val latestGalleryUri: StateFlow<android.net.Uri?> = _latestGalleryUri.asStateFlow()

    init {
        // ASYNC INIT: Load model in background to avoid main thread freeze
        viewModelScope.launch {
            repository.initialize()
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
                e.printStackTrace()
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
                
                _uiState.value = ScannerUiState.Success(result)
                
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = ScannerUiState.Error(e.message ?: "Unknown error occurred")
            }
        }
    }
    
    fun resetState() {
        _uiState.value = ScannerUiState.Idle
    }

    class Factory(private val application: android.app.Application) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ScannerViewModel::class.java)) {
                val repository = com.devsphere.leafbloom.data.repository.DiseaseRepository(application)
                return ScannerViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
