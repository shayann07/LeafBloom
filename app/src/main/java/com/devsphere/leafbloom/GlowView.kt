package com.devsphere.leafbloom

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.toColorInt

class GlowView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowColor = "#408A10".toColorInt() // The green color
    private val blurRadius = 80f // Large blur for softness

    init {
        // Software layer is required for BlurMaskFilter on some versions to render correctly
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        
        paint.color = glowColor
        paint.style = Paint.Style.FILL
        paint.maskFilter = BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL)
        paint.alpha = 90 // Adjust opacity (0-255) for subtle effect
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        
        // Draw a circle smaller than the view so the blur has space to spread
        // Radius = half width - margin for blur
        val radius = (width.coerceAtMost(height) / 2f) - blurRadius
        
        if (radius > 0) {
            canvas.drawCircle(cx, cy, radius, paint)
        }
    }
}
