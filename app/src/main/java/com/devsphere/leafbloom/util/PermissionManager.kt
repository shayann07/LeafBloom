package com.devsphere.leafbloom.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.app.Activity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Single source of truth for permission logic.
 * Follows clean architecture principles by abstracting framework checks.
 */
object PermissionManager {

    /**
     * Checks if a specific permission is granted.
     */
    fun hasPermission(context: Context, permission: String): Boolean {
        // notification permission is only needed for Android 13+ (API 33)
        if (permission == android.Manifest.permission.POST_NOTIFICATIONS && 
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks if we should show UI with rationale for requesting a permission.
     * This returns true if the user has previously denied the permission.
     */
    fun shouldShowRationale(activity: Activity, permission: String): Boolean {
        return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }
}
