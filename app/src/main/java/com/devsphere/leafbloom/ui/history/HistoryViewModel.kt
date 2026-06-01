package com.devsphere.leafbloom.ui.history

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedStateRegistryOwner
import com.devsphere.leafbloom.data.model.IdentifyResponse
import com.devsphere.leafbloom.data.repository.ScanHistoryRepository
import com.devsphere.leafbloom.ui.adapter.HistoryAdapter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class HistoryUiState {
    data object Loading : HistoryUiState()
    data class Data(
        val entries: List<HistoryAdapter.ListEntry>,
        val isEmpty: Boolean,
        val scanType: String
    ) : HistoryUiState()
}

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
    private val repository: ScanHistoryRepository
) : AndroidViewModel(application) {

    /** The "navigate to identify result" one-shot. Drained by the fragment. */
    private val _navigateIdentify = MutableStateFlow<IdentifyNav?>(null)
    val navigateIdentify: StateFlow<IdentifyNav?> = _navigateIdentify

    private val scanTypeFlow: StateFlow<String> =
        savedStateHandle.getStateFlow(KEY_SCAN_TYPE, DEFAULT_TYPE)

    private val searchQueryFlow = MutableStateFlow("")

    val state: StateFlow<HistoryUiState> = combine(scanTypeFlow, searchQueryFlow) { type, query ->
        type to query
    }
        .flatMapLatest { (type, query) ->
            val rowsFlow = if (query.isBlank()) {
                repository.observeMapped(type, application)
            } else {
                repository.observeAllMapped(application)
            }
            rowsFlow.map { rows ->
                val entries = buildEntries(rows.map { it.item }, rows.associate { it.item.id to it.sectionLabel })
                HistoryUiState.Data(
                    entries = entries,
                    isEmpty = rows.isEmpty(),
                    scanType = type
                ) as HistoryUiState
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), HistoryUiState.Loading)

    fun setScanType(type: String) {
        savedStateHandle[KEY_SCAN_TYPE] = type
    }

    fun setSearchQuery(query: String) {
        searchQueryFlow.value = query
    }

    fun currentScanType(): String = savedStateHandle[KEY_SCAN_TYPE] ?: DEFAULT_TYPE

    fun delete(id: Long, imagePath: String?) {
        viewModelScope.launch { repository.deleteWithImage(id, imagePath) }
    }

    /** Loads + decodes identify JSON off main, then emits a navigation request. */
    fun requestIdentifyNavigation(id: Long, imagePath: String?) {
        viewModelScope.launch {
            val response = repository.decodeIdentifyById(id) ?: return@launch
            _navigateIdentify.value = IdentifyNav(imagePath, response)
        }
    }

    fun onIdentifyNavigationHandled() {
        _navigateIdentify.value = null
    }

    private fun buildEntries(
        items: List<com.devsphere.leafbloom.data.model.HistoryItem>,
        sectionLabels: Map<Long, String>
    ): List<HistoryAdapter.ListEntry> {
        val entries = mutableListOf<HistoryAdapter.ListEntry>()
        var lastSection = ""
        for (item in items) {
            val section = sectionLabels[item.id] ?: ""
            if (section != lastSection) {
                entries.add(HistoryAdapter.ListEntry.Header(section))
                lastSection = section
            }
            entries.add(HistoryAdapter.ListEntry.Item(item))
        }
        return entries
    }

    data class IdentifyNav(val imagePath: String?, val response: IdentifyResponse)

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
            require(modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
                "Unknown ViewModel class: $modelClass"
            }
            @Suppress("UNCHECKED_CAST")
            return HistoryViewModel(
                application = application,
                savedStateHandle = handle,
                repository = ScanHistoryRepository.getInstance(application)
            ) as T
        }
    }

    companion object {
        const val DEFAULT_TYPE = "DIAGNOSE"
        private const val KEY_SCAN_TYPE = "scanType"
    }
}
