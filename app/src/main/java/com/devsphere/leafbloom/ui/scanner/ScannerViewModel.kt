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
