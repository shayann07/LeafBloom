package com.devsphere.leafbloom.ui.common

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment

abstract class BaseFragment : Fragment() {

    /**
     * Applies system bar insets to a specific container view.
     * Use ONLY for content that must avoid status/navigation bars.
     */
    protected fun applySystemBarInsets(target: View) {
        ViewCompat.setOnApplyWindowInsetsListener(target) { v, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            v.setPadding(
                v.paddingLeft, sysBars.top, v.paddingRight, sysBars.bottom
            )
            insets
        }
    }

    /**
     * Adapts a header image (circular/curved) to fit the container height, preventing bottom clipping
     * even when the container is wide (landscape) or when the image is zoomed.
     */
    protected fun setupAdaptiveHeader(container: View, headerImage: View, scale: Float = 1.18f) {
        container.doOnLayout {
            val w = it.width
            val h = it.height

            // Adaptive Logic: Fit bottom curve to container height
            // Logic: Center the arc such that ends are at y=0 and bottom is at y=h
            // Sag = h. R = (w^2 / 8h) + h/2. D = 2R. TransY = h - D.
            val sag = h.toFloat()
            val r = (w * w) / (8 * sag) + (sag / 2)
            val d = (2 * r).toInt()

            // User requested zoom - compensate translation so bottom edge stays at 'sag' (height)
            // translationY = sag - (d/2) * (1 + scale)
            val translationY = sag - (d / 2f) * (1 + scale)

            headerImage.layoutParams =
                (headerImage.layoutParams as android.widget.FrameLayout.LayoutParams).apply {
                    width = d
                    height = d
                    gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
                }

            headerImage.translationY = translationY

            headerImage.scaleX = scale
            headerImage.scaleY = scale
        }
    }
}
