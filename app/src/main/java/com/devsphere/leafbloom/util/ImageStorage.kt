package com.devsphere.leafbloom.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Utility for saving scanned images to app-internal storage (filesDir/scan_images/).
 * Images are stored as JPEGs with a UUID filename to avoid collisions.
 * No storage permissions required since we use app-private internal storage.
 */
object ImageStorage {

    private const val DIR_NAME = "scan_images"

    /**
     * Copy an image from a content URI to internal storage.
     * @return absolute path of the saved file.
     */
    fun saveFromUri(context: Context, uri: Uri): String {
        val dir = getOrCreateDir(context)
        val file = File(dir, "${UUID.randomUUID()}.jpg")

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Cannot open input stream for URI: $uri")

        return file.absolutePath
    }

    /**
     * Save a Bitmap to internal storage as JPEG.
     * @return absolute path of the saved file.
     */
    fun saveFromBitmap(context: Context, bitmap: Bitmap, quality: Int = 85): String {
        val dir = getOrCreateDir(context)
        val file = File(dir, "${UUID.randomUUID()}.jpg")

        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
        }

        return file.absolutePath
    }

    /**
     * Delete a previously saved image file.
     * @return true if the file was deleted or didn't exist.
     */
    fun delete(path: String): Boolean {
        val file = File(path)
        return !file.exists() || file.delete()
    }

    /**
     * Check if the saved image file still exists on disk.
     */
    fun exists(path: String): Boolean = File(path).exists()

    private fun getOrCreateDir(context: Context): File {
        val dir = File(context.filesDir, DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}
