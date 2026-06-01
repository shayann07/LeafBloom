package com.devsphere.leafbloom.ui.profile

import android.app.Application
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedStateRegistryOwner
import com.devsphere.leafbloom.data.repository.ScanHistoryRepository
import com.devsphere.leafbloom.prefs.UserPrefs
import com.devsphere.leafbloom.prefs.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class PickTarget { HEADER, AVATAR }

sealed class ImageSaveResult {
    data class Saved(val target: PickTarget, val absolutePath: String) : ImageSaveResult()
    data class Failed(val target: PickTarget) : ImageSaveResult()
}

class ProfileViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
    private val userPrefs: UserPrefs,
    private val historyRepository: ScanHistoryRepository
) : AndroidViewModel(application) {

    val profile: StateFlow<UserProfile> = userPrefs.profileFlow

    val pendingPickTarget: StateFlow<PickTarget> =
        savedStateHandle.getStateFlow(KEY_PICK_TARGET, PickTarget.AVATAR)

    private val _imageSaveEvents = Channel<ImageSaveResult>(Channel.BUFFERED)
    val imageSaveEvents: Flow<ImageSaveResult> = _imageSaveEvents.receiveAsFlow()

    private val _historyClearedEvents = Channel<Unit>(Channel.BUFFERED)
    val historyClearedEvents: Flow<Unit> = _historyClearedEvents.receiveAsFlow()

    fun setName(value: String) {
        userPrefs.userName = value
    }

    fun setEmail(value: String) {
        userPrefs.userEmail = value
    }

    fun enableDevModeAndResetOnboarding() {
        userPrefs.isDevMode = true
        userPrefs.resetOnboarding()
    }

    fun launchPicker(target: PickTarget) {
        savedStateHandle[KEY_PICK_TARGET] = target
    }

    fun handlePickedImage(uri: Uri, target: PickTarget) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val savedPath = withContext(Dispatchers.IO) {
                copyToInternalStorage(app, uri, fileNameFor(target))
            }
            val event = if (savedPath == null) {
                ImageSaveResult.Failed(target)
            } else {
                when (target) {
                    PickTarget.HEADER -> userPrefs.headerImagePath = savedPath
                    PickTarget.AVATAR -> userPrefs.profilePicturePath = savedPath
                }
                ImageSaveResult.Saved(target, savedPath)
            }
            _imageSaveEvents.trySend(event)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            historyRepository.deleteAll()
            _historyClearedEvents.trySend(Unit)
        }
    }

    private fun fileNameFor(target: PickTarget): String = when (target) {
        PickTarget.HEADER -> "header.jpg"
        PickTarget.AVATAR -> "avatar.jpg"
    }

    private fun copyToInternalStorage(
        app: Application,
        uri: Uri,
        fileName: String
    ): String? = runCatching {
        val dir = File(app.filesDir, "profile").apply { mkdirs() }
        val target = File(dir, fileName)
        app.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        }
        target.absolutePath
    }.getOrNull()

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
            require(modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
                "Unknown ViewModel class: $modelClass"
            }
            @Suppress("UNCHECKED_CAST")
            return ProfileViewModel(
                application = application,
                savedStateHandle = handle,
                userPrefs = UserPrefs.getInstance(application),
                historyRepository = ScanHistoryRepository.getInstance(application)
            ) as T
        }
    }

    companion object {
        private const val KEY_PICK_TARGET = "pickTarget"
    }
}
