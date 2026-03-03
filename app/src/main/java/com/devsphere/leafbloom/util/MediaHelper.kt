package com.devsphere.leafbloom.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.IOException
import java.io.OutputStream

/**
 * Handles media operations using modern Scoped Storage and MediaStore APIs.
 * strictly avoids usage of absolute file paths and legacy storage permissions.
 */
object MediaHelper {

    /**
     * Saves a bitmap to the system Gallery (Pictures/LeafBloom directory).
     * @return Uri of the saved image, or null if failed.
     */
    fun saveImageToGallery(context: Context, bitmap: Bitmap, filename: String = "leafbloom_capture_${System.currentTimeMillis()}"): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$filename.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/LeafBloom")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let {
            try {
                val outputStream: OutputStream? = resolver.openOutputStream(it)
                outputStream?.use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(it, contentValues, null, null)
                }
                return it
            } catch (e: IOException) {
                e.printStackTrace()
                // Cleanup partial file if write failed
                resolver.delete(it, null, null)
            }
        }
        return null
    }
}
