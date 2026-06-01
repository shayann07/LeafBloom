package com.devsphere.leafbloom.prefs

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Immutable snapshot of user-facing profile prefs for flow consumers. */
data class UserProfile(
    val userName: String,
    val userEmail: String,
    val avatarPath: String?,
    val headerPath: String?
)

class UserPrefs(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _profile = MutableStateFlow(snapshot())
    val profileFlow: StateFlow<UserProfile> = _profile.asStateFlow()

    companion object {
        private const val PREFS_NAME = "leafbloom_prefs"
        private const val KEY_FIRST_RUN = "is_first_run"
        private const val KEY_DEV_MODE = "dev_mode"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_PROFILE_PICTURE_PATH = "profile_picture_path"
        private const val KEY_HEADER_IMAGE_PATH = "header_image_path"

        // Singleton instance
        @Volatile
        private var INSTANCE: UserPrefs? = null

        fun getInstance(context: Context): UserPrefs {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: UserPrefs(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    var isFirstRun: Boolean
        get() = prefs.getBoolean(KEY_FIRST_RUN, true)
        set(value) = prefs.edit().putBoolean(KEY_FIRST_RUN, value).apply()

    var isDevMode: Boolean
        get() = prefs.getBoolean(KEY_DEV_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_DEV_MODE, value).apply()

    var userName: String
        get() = prefs.getString(KEY_USER_NAME, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_USER_NAME, value).apply()
            _profile.value = _profile.value.copy(userName = value)
        }

    var userEmail: String
        get() = prefs.getString(KEY_USER_EMAIL, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_USER_EMAIL, value).apply()
            _profile.value = _profile.value.copy(userEmail = value)
        }

    var profilePicturePath: String?
        get() = prefs.getString(KEY_PROFILE_PICTURE_PATH, null)
        set(value) {
            prefs.edit().putString(KEY_PROFILE_PICTURE_PATH, value).apply()
            _profile.value = _profile.value.copy(avatarPath = value)
        }

    var headerImagePath: String?
        get() = prefs.getString(KEY_HEADER_IMAGE_PATH, null)
        set(value) {
            prefs.edit().putString(KEY_HEADER_IMAGE_PATH, value).apply()
            _profile.value = _profile.value.copy(headerPath = value)
        }

    /**
     * Developer helper: resets onboarding so the walkthrough + model prep
     * flow plays again on next app launch.
     */
    fun resetOnboarding() {
        isFirstRun = true
    }

    fun clear() {
        prefs.edit().clear().apply()
        _profile.value = snapshot()
    }

    private fun snapshot(): UserProfile = UserProfile(
        userName = prefs.getString(KEY_USER_NAME, "") ?: "",
        userEmail = prefs.getString(KEY_USER_EMAIL, "") ?: "",
        avatarPath = prefs.getString(KEY_PROFILE_PICTURE_PATH, null),
        headerPath = prefs.getString(KEY_HEADER_IMAGE_PATH, null)
    )
}
