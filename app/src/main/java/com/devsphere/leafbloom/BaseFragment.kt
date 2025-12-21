package com.devsphere.leafbloom

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
}
