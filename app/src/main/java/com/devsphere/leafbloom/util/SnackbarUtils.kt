package com.devsphere.leafbloom.util

import android.content.res.ColorStateList
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.devsphere.leafbloom.R
import com.google.android.material.snackbar.Snackbar

object SnackbarUtils {

    enum class Type {
        INFO, SUCCESS, WARNING, ERROR
    }

    /**
     * Shows a customized standard Snackbar using the global app theme (`LeafBloomSnackbar`),
     * but injects dynamic state colors (Error/Warning/Success) for better UX.
     * 
     * @param view The view to find a parent from (usually `binding.root`).
     * @param message The text to display.
     * @param length How long to display the message.
     * @param type The semantic state of the message.
     */
    fun showSnackbar(
        view: View, 
        message: String, 
        length: Int = Snackbar.LENGTH_LONG,
        type: Type = Type.INFO
    ) {
        val snackbar = Snackbar.make(view, message, length)
        val context = view.context

        // Determine component colors based on the requested semantic type
        val backgroundColorRes = when (type) {
            Type.ERROR -> R.color.surface_error_light
            Type.WARNING -> R.color.surface_warning_light
            Type.SUCCESS -> R.color.surface_success_light
            Type.INFO -> R.color.surface_variant
        }

        // Text always stays the deep brand green for contrast against the light pastel backgrounds
        val textColorRes = R.color.brand_green_primary_dark

        // Programmatically override the base XML theme colors for this specific instance
        snackbar.view.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, backgroundColorRes))
        val textView = snackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        textView.setTextColor(ContextCompat.getColor(context, textColorRes))

        snackbar.show()
    }
}
