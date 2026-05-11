package com.devsphere.leafbloom.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.devsphere.leafbloom.data.model.WeatherResponse
import com.devsphere.leafbloom.data.repository.WeatherError
import com.devsphere.leafbloom.data.repository.WeatherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class WeatherUiState {
    object Empty : WeatherUiState()
    data class Data(
        val weather: WeatherResponse,
        val fetchedAtMs: Long,
        val isRefreshing: Boolean,
        val lastError: WeatherError?
    ) : WeatherUiState()
}

class HomeViewModel(
    application: Application,
    private val repository: WeatherRepository
) : AndroidViewModel(application) {

    private val refreshing = MutableStateFlow(false)
    private val lastError = MutableStateFlow<WeatherError?>(null)

    val weather: StateFlow<WeatherUiState> =
        combine(repository.observeWeather(), refreshing, lastError) { entity, isRefreshing, error ->
            if (entity == null) WeatherUiState.Empty
            else WeatherUiState.Data(
                weather = repository.decode(entity),
                fetchedAtMs = entity.fetchedAtMs,
                isRefreshing = isRefreshing,
                lastError = error
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = WeatherUiState.Empty
        )

    fun onLocation(lat: Double, lon: Double, force: Boolean = false) {
        viewModelScope.launch {
            refreshing.value = true
            lastError.value = null
            repository.refreshIfStale(lat, lon, force)
                .onFailure { throwable ->
                    lastError.value = throwable as? WeatherError ?: WeatherError.Unknown(throwable)
                }
            refreshing.value = false
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                "Unknown ViewModel class: $modelClass"
            }
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(
                application = application,
                repository = WeatherRepository.getInstance(application)
            ) as T
        }
    }
}
