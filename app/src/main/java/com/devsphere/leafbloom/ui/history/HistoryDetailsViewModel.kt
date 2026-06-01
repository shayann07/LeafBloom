package com.devsphere.leafbloom.ui.history

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.annotation.DrawableRes
import androidx.savedstate.SavedStateRegistryOwner
import com.devsphere.leafbloom.R
import com.devsphere.leafbloom.data.model.DiseaseCareInfo
import com.devsphere.leafbloom.data.model.DiseaseInfo
import com.devsphere.leafbloom.data.repository.ScanHistoryRepository
import com.devsphere.leafbloom.data.source.local.db.ScanHistoryEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class DetailsUiState {
    data object Loading : DetailsUiState()
    data class Resolved(
        val title: String,
        val scanType: String,
        val careInfo: DiseaseCareInfo,
        val headerImagePath: String?,
        @DrawableRes val headerImageRes: Int
    ) : DetailsUiState()
    data object NotFound : DetailsUiState()
}

class HistoryDetailsViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
    private val repository: ScanHistoryRepository
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<DetailsUiState>(DetailsUiState.Loading)
    val state: StateFlow<DetailsUiState> = _state.asStateFlow()

    init {
        resolve()
    }

    private fun resolve() {
        val diseaseName = savedStateHandle.get<String>(ARG_DISEASE_NAME)
        val scanId = savedStateHandle.get<Long>(ARG_SCAN_ID) ?: -1L
        when {
            !diseaseName.isNullOrBlank() -> _state.value = DetailsUiState.Resolved(
                title = diseaseName,
                scanType = "DIAGNOSE",
                careInfo = DiseaseCareInfo.get(diseaseName),
                headerImagePath = null,
                headerImageRes = DiseaseInfo.get(diseaseName).headerRes
            )
            scanId > 0 -> viewModelScope.launch { loadEntity(scanId) }
            else -> _state.value = DetailsUiState.Resolved(
                title = STATIC_TITLE,
                scanType = "DIAGNOSE",
                careInfo = DiseaseCareInfo.get("Healthy"),
                headerImagePath = null,
                headerImageRes = DiseaseInfo.get("Healthy").headerRes
            )
        }
    }

    private suspend fun loadEntity(id: Long) {
        val entity: ScanHistoryEntity? = repository.getById(id)
        _state.value = if (entity == null) {
            DetailsUiState.NotFound
        } else {
            DetailsUiState.Resolved(
                title = entity.predictedClass,
                scanType = entity.scanType,
                careInfo = DiseaseCareInfo.get(entity.predictedClass),
                headerImagePath = entity.imagePath.takeIf { it.isNotBlank() },
                headerImageRes = DiseaseInfo.get(entity.predictedClass).headerRes
            )
        }
    }

    class Factory(
        private val application: Application,
        owner: SavedStateRegistryOwner,
        defaultArgs: Bundle? = null
    ) : AbstractSavedStateViewModelFactory(owner, defaultArgs) {
        override fun <T : ViewModel> create(
            key: String,
            modelClass: Class<T>,
            handle: SavedStateHandle
        ): T {
            require(modelClass.isAssignableFrom(HistoryDetailsViewModel::class.java)) {
                "Unknown ViewModel class: $modelClass"
            }
            @Suppress("UNCHECKED_CAST")
            return HistoryDetailsViewModel(
                application = application,
                savedStateHandle = handle,
                repository = ScanHistoryRepository.getInstance(application)
            ) as T
        }
    }

    companion object {
        const val ARG_DISEASE_NAME = "diseaseName"
        const val ARG_SCAN_ID = "scanId"
        private const val STATIC_TITLE = "Tomato"
    }
}
