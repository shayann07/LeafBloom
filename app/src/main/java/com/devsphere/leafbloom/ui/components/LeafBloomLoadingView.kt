package com.devsphere.leafbloom.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.animation.doOnEnd
import androidx.core.graphics.PathParser
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withTranslation
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

class LeafBloomLoadingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Soft shadow paint
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(28, 0, 0, 0)
        style = Paint.Style.FILL
    }

    // Glow overlay paint (per leaf)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val paths = mutableListOf<Path>()
    private val shaders = mutableListOf<Shader>()

    // Per-leaf alpha 0..1 and 0..255
    private val alphaFactors = FloatArray(8) { 0f }
    private val alphas = IntArray(8) { 0 }

    // Global factors
    private var globalAlpha = 1f
    private var pulseScale = 1f
    private var exitScale = 1f

    // Entrance fade (0..1) when startLoop() is called
    private var globalEnterAlpha = 1f

    // External exit (0..1) driven by playCompletionAndStop()
    private var externalExitAlpha = 1f
    private var externalExitScale = 1f

    // Breeze sway angle
    private var breezeAngleDeg = 0f

    // Vector viewport
    private val viewportW = 194f
    private val viewportH = 195f

    // Combined bounds of all petals (for shadow, pivot, etc.)
    private val combinedBounds = RectF()
    private val tmpLeafBounds = RectF()

    // Animators
    private var loopAnimator: ValueAnimator? = null
    private var breezeAnimator: ValueAnimator? = null
    private var breatheAnimator: ValueAnimator? = null
    private var enterAnimator: ValueAnimator? = null
    private var externalExitAnimator: ValueAnimator? = null

    // Stage index: 0..7
    private var stageIndex: Int = 0
    private val leafCount = 8

    // Fixed leaf order (clockwise from ~11PM of your SVG)
    private val leafOrder = listOf(6, 0, 1, 2, 3, 4, 5, 7)

    // Stage helpers (group membership)
    private val prevGroup = BooleanArray(leafCount) { false }
    private val currGroup = BooleanArray(leafCount) { false }
    private val newLeafOrderIndex = IntArray(leafCount) { -1 }
    private var newLeafCount: Int = 0

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        initPaths()
        initShaders()
        startBreeze()
    }

    // -----------------------------
    // INIT
    // -----------------------------
    private fun initPaths() {
        val pathDataList = listOf(
            "m95.22 97.05 1.43-76.6L99.5 82.3c11.47-10.75 17.81-25.87 17.45-41.57C116.58 25.02 109.55 10.2 97.6 0c-28.4 37.38-29.19 69.73-2.38 97.05Z",
            "m95.55 96.27 55.17-53.15L109 88.87c15.7.5 30.88-5.7 41.74-17.07 10.85-11.36 16.34-26.8 15.11-42.47-46.5 6.35-69.94 28.66-70.3 66.94Z",
            "m96.33 95.95 76.59 1.42-61.84 2.86c10.74 11.46 25.86 17.8 41.57 17.44 15.71-.36 30.52-7.4 40.73-19.35-37.38-28.4-69.73-29.19-97.05-2.37Z",
            "m97.1 96.27 53.16 55.17-45.75-41.72c-.5 15.7 5.7 30.89 17.06 41.74 11.37 10.85 26.81 16.35 42.48 15.11-6.35-46.5-28.66-69.94-66.94-70.3Z",
            "M97.43 97.05 96 173.65l-2.85-61.85c-11.46 10.74-17.8 25.86-17.44 41.57.36 15.71 7.4 30.52 19.34 40.73 28.4-37.38 29.2-69.73 2.38-97.05Z",
            "m97.1 97.83-55.16 53.15 41.71-45.75c-15.7-.5-30.88 5.7-41.73 17.06-10.85 11.37-16.35 26.82-15.12 42.48 46.51-6.35 69.95-28.66 70.3-66.94Z",
            "M95.55 97.83 42.4 42.66l45.75 41.71c.5-15.7-5.7-30.88-17.07-41.73C59.72 31.8 44.27 26.3 28.6 27.52c6.35 46.52 28.67 69.95 66.95 70.31Z",
            "m97.17 95.33-76.6-1.53 61.85-2.77C71.7 79.55 56.58 73.19 40.87 73.53c-15.7.34-30.53 7.36-40.75 19.3 37.34 28.44 69.69 29.28 97.05 2.5Z"
        )

        paths.clear()
        pathDataList.forEach { data ->
            paths.add(PathParser.createPathFromPathData(data))
        }

        if (paths.isNotEmpty()) {
            val tmp = RectF()
            paths[0].computeBounds(combinedBounds, true)
            for (i in 1 until paths.size) {
                paths[i].computeBounds(tmp, true)
                combinedBounds.union(tmp)
            }
        }
    }

    private fun initShaders() {
        shaders.clear()

        shaders.add(
            LinearGradient(
                80.52f, 80.28f, 112.87f, -2.97f,
                intArrayOf("#FF008B31".toColorInt(), "#FFFFEF00".toColorInt()),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        )

        shaders.add(
            LinearGradient(
                98.44f, 77.27f, 180.19f, 41.28f,
                intArrayOf("#FF008B31".toColorInt(), "#FFFFEF00".toColorInt()),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        )

        shaders.add(
            LinearGradient(
                113.10f, 81.24f, 196.35f, 113.59f,
                intArrayOf("#FF008B31".toColorInt(), "#FFFFEF00".toColorInt()),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        )

        shaders.add(
            LinearGradient(
                116.10f, 99.16f, 152.10f, 180.91f,
                intArrayOf("#FF008B31".toColorInt(), "#FFFFEF00".toColorInt()),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        )

        shaders.add(
            LinearGradient(
                112.13f, 113.82f, 79.78f, 197.07f,
                intArrayOf("#FF008B31".toColorInt(), "#FFFFEF00".toColorInt()),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        )

        shaders.add(
            LinearGradient(
                94.21f, 116.83f, 12.47f, 152.82f,
                intArrayOf("#FF008B31".toColorInt(), "#FFFFEF00".toColorInt()),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        )

        shaders.add(
            LinearGradient(
                76.55f, 94.93f, 40.56f, 13.19f,
                intArrayOf("#FF008B31".toColorInt(), "#FFFFEF00".toColorInt()),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        )

        shaders.add(
            LinearGradient(
                81.67f, 106.69f, -1.54f, 74.24f,
                intArrayOf("#FF008B31".toColorInt(), "#FFFFEF00".toColorInt()),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        )
    }

    // -----------------------------
    // PUBLIC API
    // -----------------------------
    fun startLoop() {
        // Full reset
        stopLoop()
        resetAllState()

        // Start from stage 0
        stageIndex = 0

        // Soft global entrance fade
        globalEnterAlpha = 0f
        startEnterFade()

        startBreathe()
        startBreeze()
        startStageAnimator()
    }

    /**
     * Hard stop: cancel animators and clear state.
     * This does NOT play the pretty exit. Use playCompletionAndStop()
     * if you want the smooth fade-out.
     */
    fun stopLoop() {
        loopAnimator?.cancel()
        loopAnimator = null

        breatheAnimator?.cancel()
        breatheAnimator = null

        breezeAnimator?.cancel()
        breezeAnimator = null

        enterAnimator?.cancel()
        enterAnimator = null

        externalExitAnimator?.cancel()
        externalExitAnimator = null

        resetAllState()
        invalidate()
    }

    /**
     * Premium completion animation:
     *  - Gently fades the flower out and slightly scales it down.
     *  - When finished, it stops all internal animators and invokes onEnd.
     */
    fun playCompletionAndStop(onEnd: (() -> Unit)? = null) {
        if (width == 0 || height == 0) {
            // View is not laid out or already gone; just hard stop.
            stopLoop()
            onEnd?.invoke()
            return
        }

        externalExitAnimator?.cancel()
        externalExitAlpha = 1f
        externalExitScale = 1f

        externalExitAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1100L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val t = (it.animatedValue as Float).coerceIn(0f, 1f)
                val eased = 1f - (1f - t).pow(2f)

                externalExitAlpha = 1f - eased
                externalExitScale = 1f - 0.12f * eased
                invalidate()
            }
            doOnEnd {
                stopLoop()
                onEnd?.invoke()
            }
            start()
        }
    }

    private fun resetAllState() {
        for (i in 0 until leafCount) {
            alphaFactors[i] = 0f
            alphas[i] = 0
            prevGroup[i] = false
            currGroup[i] = false
            newLeafOrderIndex[i] = -1
        }
        newLeafCount = 0

        globalAlpha = 1f
        pulseScale = 1f
        exitScale = 1f
        globalEnterAlpha = 1f
        externalExitAlpha = 1f
        externalExitScale = 1f
        stageIndex = 0
        breezeAngleDeg = 0f
    }

    // -----------------------------
    // ENTER FADE
    // -----------------------------
    private fun startEnterFade() {
        enterAnimator?.cancel()
        enterAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 600L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                globalEnterAlpha = (it.animatedValue as Float).coerceIn(0f, 1f)
                invalidate()
            }
            start()
        }
    }

    // -----------------------------
    // STAGE ENGINE
    // -----------------------------
    private fun computeStageGroup(stage: Int, out: BooleanArray) {
        for (i in 0 until leafCount) {
            out[i] = false
        }
        if (stage < 0) return

        val groupSize = (stage + 1).coerceAtMost(leafCount)
        val basePos = stage % leafCount

        for (k in 0 until groupSize) {
            val pos = (basePos + k) % leafCount
            val leafIndex = leafOrder[pos]
            out[leafIndex] = true
        }
    }

    private fun computeNewLeafOrderForStage() {
        for (i in 0 until leafCount) {
            newLeafOrderIndex[i] = -1
        }
        newLeafCount = 0

        val groupSize = (stageIndex + 1).coerceAtMost(leafCount)
        val basePos = stageIndex % leafCount

        for (k in 0 until groupSize) {
            val pos = (basePos + k) % leafCount
            val leafIndex = leafOrder[pos]
            if (currGroup[leafIndex] && !prevGroup[leafIndex]) {
                newLeafOrderIndex[leafIndex] = newLeafCount
                newLeafCount++
            }
        }
    }

    private fun startStageAnimator() {
        loopAnimator?.cancel()

        val duration = 1200L
        val interpolator = DecelerateInterpolator()

        // Prepare group data for this stage
        val prevStage = stageIndex - 1
        if (prevStage < 0) {
            for (i in 0 until leafCount) {
                prevGroup[i] = false
            }
        } else {
            computeStageGroup(prevStage, prevGroup)
        }

        computeStageGroup(stageIndex, currGroup)
        computeNewLeafOrderForStage()

        loopAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            this.interpolator = interpolator

            addUpdateListener { anim ->
                val phase = (anim.animatedValue as Float).coerceIn(0f, 1f)
                updateStage(phase)
                invalidate()
            }

            doOnEnd {
                if (!isAttachedToWindow) return@doOnEnd
                stageIndex++
                if (stageIndex >= leafCount) {
                    stageIndex = 0
                }
                startStageAnimator()
            }

            start()
        }
    }

    /**
     * One stage:
     *  - First part: fade out leaves that were in prevGroup but not in currGroup.
     *  - Rest: reveal new leaves of currGroup one-by-one; shared leaves stay fully visible.
     */
    private fun updateStage(phase: Float) {
        val fadeOutPortion = 0.25f
        val fadeOutProgress = (phase / fadeOutPortion).coerceIn(0f, 1f)

        val revealPhase = if (phase <= fadeOutPortion) {
            0f
        } else {
            ((phase - fadeOutPortion) / (1f - fadeOutPortion)).coerceIn(0f, 1f)
        }

        val perLeafSpan = if (newLeafCount > 0) 1f / newLeafCount else 1f
        val fadeWindow = perLeafSpan * 0.8f // slight overlap

        for (leafIndex in 0 until leafCount) {
            val wasInPrev = prevGroup[leafIndex]
            val inCurr = currGroup[leafIndex]
            var a = 0f

            if (wasInPrev && !inCurr) {
                // Fade out old leaves at stage start
                a = if (phase <= fadeOutPortion) {
                    1f - fadeOutProgress
                } else {
                    0f
                }
            } else if (inCurr) {
                if (wasInPrev) {
                    // Shared: already visible, stay visible
                    a = 1f
                } else {
                    // New leaf of this stage → fade in one-by-one
                    if (newLeafCount == 0) {
                        a = 1f
                    } else {
                        val idx = newLeafOrderIndex[leafIndex]
                        if (idx >= 0) {
                            val start = idx * perLeafSpan
                            val t = ((revealPhase - start) / fadeWindow).coerceIn(0f, 1f)
                            a = t
                        } else {
                            a = 0f
                        }
                    }
                }
            } else {
                a = 0f
            }

            alphaFactors[leafIndex] = a.coerceIn(0f, 1f)
            alphas[leafIndex] =
                (alphaFactors[leafIndex] * 255f).toInt().coerceIn(0, 255)
        }
    }

    // -----------------------------
    // Ambient breeze sway
    // -----------------------------
    private fun startBreeze() {
        if (breezeAnimator != null) return

        breezeAnimator = ValueAnimator.ofFloat(0f, (Math.PI * 2).toFloat()).apply {
            duration = 2200L
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                val t = it.animatedValue as Float
                val maxAngle = 1.2f // degrees
                breezeAngleDeg = (sin(t.toDouble()) * maxAngle).toFloat()
                invalidate()
            }
            start()
        }
    }

    // -----------------------------
    // Global breathe (scale)
    // -----------------------------
    private fun startBreathe() {
        if (breatheAnimator != null) return

        val interpolator = DecelerateInterpolator()

        breatheAnimator = ValueAnimator.ofFloat(0f, (Math.PI * 2).toFloat()).apply {
            duration = 1500L
            repeatCount = ValueAnimator.INFINITE
            this.interpolator = interpolator
            addUpdateListener {
                val t = it.animatedValue as Float
                val amp = 0.02f // 2% pulse
                pulseScale = 1f + (sin(t.toDouble()) * amp).toFloat()
                invalidate()
            }
            start()
        }
    }

    // -----------------------------
    // DRAW
    // -----------------------------
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (paths.isEmpty()) return

        val baseScale = min(width / viewportW, height / viewportH)
        val dx = (width - viewportW * baseScale) / 2f
        val dy = (height - viewportH * baseScale) / 2f

        val pivotX = viewportW / 2f
        val pivotY = viewportH / 2f

        canvas.withTranslation(dx, dy) {
            // Fit vector
            scale(baseScale, baseScale, 0f, 0f)

            // Breeze sway
            rotate(breezeAngleDeg, pivotX, pivotY)

            // Breathing + possible shrink + external exit scale
            val animScale = pulseScale * exitScale * externalExitScale
            scale(animScale, animScale, pivotX, pivotY)

            // Shadow
            if (!combinedBounds.isEmpty) {
                val shadowTop = combinedBounds.bottom + 6f
                val shadowBottom = shadowTop + combinedBounds.height() * 0.18f
                val shadowRadius = combinedBounds.width() * 0.25f
                drawRoundRect(
                    combinedBounds.left,
                    shadowTop,
                    combinedBounds.right,
                    shadowBottom,
                    shadowRadius,
                    shadowRadius,
                    shadowPaint
                )
            }

            // Leaves + glow
            for (i in 0 until leafCount) {
                val leafAlpha =
                    (alphas[i] * globalAlpha * globalEnterAlpha * externalExitAlpha).toInt()
                        .coerceIn(0, 255)
                if (leafAlpha <= 0) continue

                paint.shader = shaders[i]
                paint.alpha = leafAlpha
                drawPath(paths[i], paint)

                // Soft glow on visible leaves
                if (leafAlpha in 32..245) {
                    paths[i].computeBounds(tmpLeafBounds, true)
                    val cx = tmpLeafBounds.centerX()
                    val cy = tmpLeafBounds.centerY()
                    val radius = max(tmpLeafBounds.width(), tmpLeafBounds.height()) * 0.6f

                    val combined = (leafAlpha / 255f) *
                            globalAlpha *
                            globalEnterAlpha *
                            externalExitAlpha

                    // Slightly stronger, quadratic glow ramp
                    val glowStrength = (combined * combined) * 0.21f
                    val glowAlpha = (glowStrength * 255).toInt().coerceIn(0, 255)

                    glowPaint.shader = RadialGradient(
                        cx,
                        cy,
                        radius,
                        Color.argb(glowAlpha, 255, 255, 255),
                        Color.TRANSPARENT,
                        Shader.TileMode.CLAMP
                    )
                    drawCircle(cx, cy, radius, glowPaint)
                }
            }
        }
    }

    // -----------------------------
    // Cleanup
    // -----------------------------
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopLoop()
    }
}
