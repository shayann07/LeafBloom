package com.devsphere.leafbloom.prefs

import android.content.Context
import android.content.SharedPreferences

class UserPrefs(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "leafbloom_prefs"
        private const val KEY_FIRST_RUN = "is_first_run"

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

    fun clear() {
        prefs.edit().clear().apply()
    }
}
