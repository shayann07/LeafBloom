package com.devsphere.leafbloom.ui.motion

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.TextView
import androidx.transition.Fade
import androidx.transition.TransitionManager
import com.google.android.material.progressindicator.CircularProgressIndicator
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Shared Material 3 motion tokens and entrance helpers.
 * Extracted so every screen uses the same easing + durations.
 */
object Motion {
    val EmphasizedDecelerate: PathInterpolator = PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate: PathInterpolator = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)
    val Standard: PathInterpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
    val Overshoot: PathInterpolator = PathInterpolator(0.34f, 1.4f, 0.64f, 1f)

    const val SHORT_3 = 150L
    const val SHORT_4 = 200L
    const val MEDIUM_2 = 300L
    const val MEDIUM_4 = 400L
    const val LONG_1 = 450L
    const val LONG_2 = 500L
    const val LONG_4 = 700L
    const val STAGGER_GAP = 80L

    fun reduced(context: Context): Boolean {
        val r = context.contentResolver
        val a = Settings.Global.getFloat(r, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        val t = Settings.Global.getFloat(r, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
        return a == 0f || t == 0f
    }
}

private fun View.dp(v: Float): Float = v * resources.displayMetrics.density

fun View.primeFadeUp(translationDp: Float = 16f) {
    alpha = 0f
    translationY = dp(translationDp)
}

fun View.entranceFadeUp(
    delay: Long = 0L,
    duration: Long = Motion.MEDIUM_2,
) {
    animate().cancel()
    animate()
        .alpha(1f)
        .translationY(0f)
        .setStartDelay(delay)
        .setDuration(duration)
        .setInterpolator(Motion.EmphasizedDecelerate)
        .start()
}

fun View.primeScaleFadeUp(translationDp: Float = 24f, startScale: Float = 0.92f) {
    alpha = 0f
    translationY = dp(translationDp)
    scaleX = startScale
    scaleY = startScale
}

fun View.entranceScaleFadeUp(
    delay: Long = 0L,
    duration: Long = Motion.MEDIUM_4,
) {
    animate().cancel()
    animate()
        .alpha(1f)
        .translationY(0f)
        .scaleX(1f).scaleY(1f)
        .setStartDelay(delay)
        .setDuration(duration)
        .setInterpolator(Motion.EmphasizedDecelerate)
        .start()
}

fun View.primeScalePop(startScale: Float = 0.6f) {
    alpha = 0f
    scaleX = startScale
    scaleY = startScale
}

fun View.entrancePop(
    delay: Long = 0L,
    duration: Long = Motion.MEDIUM_4,
) {
    animate().cancel()
    animate()
        .alpha(1f)
        .scaleX(1f).scaleY(1f)
        .setStartDelay(delay)
        .setDuration(duration)
        .setInterpolator(Motion.Overshoot)
        .start()
}

/**
 * Stage a Fade transition on the next layout pass for this ViewGroup, so any
 * visibility flips that follow crossfade instead of pop. No-op under reduced
 * motion. Pass [excludeRecycler] to keep a RecyclerView's item animations
 * (Fade interferes with them otherwise).
 */
fun ViewGroup.beginFadeToggle(
    duration: Long = Motion.MEDIUM_2,
    excludeRecycler: View? = null,
) {
    if (Motion.reduced(context)) return
    val fade = Fade().apply {
        this.duration = duration
        interpolator = Motion.Standard
        excludeRecycler?.let { excludeChildren(it, true) }
    }
    TransitionManager.beginDelayedTransition(this, fade)
}

/** Snap-to-final state — used when system reduced-motion is active. */
fun View.snapVisible() {
    animate().cancel()
    alpha = 1f
    translationY = 0f
    scaleX = 1f
    scaleY = 1f
}

@SuppressLint("ClickableViewAccessibility")
fun View.bounceOnPress() {
    setOnTouchListener { v, ev ->
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> v.animate()
                .scaleX(0.94f).scaleY(0.94f)
                .setDuration(80L).setInterpolator(Motion.Standard).start()
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> v.animate()
                .scaleX(1f).scaleY(1f)
                .setDuration(140L).setInterpolator(Motion.EmphasizedDecelerate).start()
        }
        false
    }
}

fun View.pulseOnce(peakScale: Float = 1.06f, duration: Long = 600L, delay: Long = 0L) {
    val anim = ValueAnimator.ofFloat(1f, peakScale, 1f)
    anim.duration = duration
    anim.startDelay = delay
    anim.interpolator = Motion.EmphasizedDecelerate
    anim.addUpdateListener {
        val s = it.animatedValue as Float
        scaleX = s
        scaleY = s
    }
    anim.start()
}

/** Sweep the indicator from 0% to target and count the percent text in sync. */
fun animateGauge(
    indicator: CircularProgressIndicator,
    percentView: TextView,
    targetFraction: Float,
    duration: Long = Motion.LONG_4,
    delay: Long = 0L,
) {
    val safe = targetFraction.coerceIn(0f, 1f)
    indicator.progress = 0
    percentView.text = String.format(Locale.US, "%.2f%%", 0f)

    val anim = ValueAnimator.ofFloat(0f, safe)
    anim.duration = duration
    anim.startDelay = delay
    anim.interpolator = Motion.EmphasizedDecelerate
    anim.addUpdateListener {
        val f = it.animatedValue as Float
        indicator.setProgressCompat((f * 100f).roundToInt().coerceIn(0, 100), false)
        percentView.text = String.format(Locale.US, "%.2f%%", f * 100f)
    }
    anim.start()
}

fun setGaugeInstant(
    indicator: CircularProgressIndicator,
    percentView: TextView,
    fraction: Float,
) {
    val safe = fraction.coerceIn(0f, 1f)
    indicator.setProgressCompat((safe * 100f).roundToInt(), false)
    percentView.text = String.format(Locale.US, "%.2f%%", safe * 100f)
}

/** Integer-percent variant (e.g. "87%") for result sheets. */
fun animateGaugeInt(
    indicator: CircularProgressIndicator,
    percentView: TextView,
    targetFraction: Float,
    duration: Long = Motion.LONG_4,
    delay: Long = 0L,
) {
    val safe = targetFraction.coerceIn(0f, 1f)
    indicator.progress = 0
    percentView.text = "0%"

    val anim = ValueAnimator.ofFloat(0f, safe)
    anim.duration = duration
    anim.startDelay = delay
    anim.interpolator = Motion.EmphasizedDecelerate
    anim.addUpdateListener {
        val f = it.animatedValue as Float
        val p = (f * 100f).roundToInt().coerceIn(0, 100)
        indicator.setProgressCompat(p, false)
        percentView.text = "$p%"
    }
    anim.start()
}

fun setGaugeIntInstant(
    indicator: CircularProgressIndicator,
    percentView: TextView,
    fraction: Float,
) {
    val safe = fraction.coerceIn(0f, 1f)
    val p = (safe * 100f).roundToInt()
    indicator.setProgressCompat(p, false)
    percentView.text = "$p%"
}
