package com.devsphere.leafbloom.ui.result

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.devsphere.leafbloom.data.model.IdentifyResponse
import com.devsphere.leafbloom.data.repository.IdentifyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

sealed class IdentifyUiState {
    object Idle : IdentifyUiState()
    object Loading : IdentifyUiState()
    data class Success(val response: IdentifyResponse) : IdentifyUiState()
    data class Error(val message: String) : IdentifyUiState()
}

class IdentifyViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<IdentifyUiState>(IdentifyUiState.Idle)
    val uiState: StateFlow<IdentifyUiState> = _uiState

    fun identifyPlant(uri: Uri) {
        if (_uiState.value is IdentifyUiState.Loading) return

        _uiState.value = IdentifyUiState.Loading

        viewModelScope.launch {
            try {
                // Convert Uri to File (needed for Multipart)
                // Using a temp file in cache
                val context = getApplication<Application>().applicationContext
                val tempFile = File.createTempFile("upload", ".jpg", context.cacheDir)
                
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                // Call Repository
                val result = IdentifyRepository.identifyPlant(tempFile)

                result.onSuccess {
                    _uiState.value = IdentifyUiState.Success(it)
                }.onFailure {
                    _uiState.value = IdentifyUiState.Error(it.message ?: "Unknown error")
                }

                // Cleanup temp file
                if (tempFile.exists()) tempFile.delete()

            } catch (e: Exception) {
                _uiState.value = IdentifyUiState.Error(e.localizedMessage ?: "Failed to process image")
            }
        }
    }

    class Factory(private val app: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
           if (modelClass.isAssignableFrom(IdentifyViewModel::class.java)) {
               @Suppress("UNCHECKED_CAST")
               return IdentifyViewModel(app) as T
           }
           throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
