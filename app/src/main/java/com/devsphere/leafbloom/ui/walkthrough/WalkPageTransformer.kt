package com.devsphere.leafbloom.ui.walkthrough

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.util.TypedValue
import android.view.View
import androidx.viewpager2.widget.ViewPager2
import com.devsphere.leafbloom.R
import com.devsphere.leafbloom.ui.motion.Motion
import kotlin.math.abs

/**
 * Premium parallax + entrance transformer.
 *
 *  - Background tier (illustration) parallaxes at 35% of page offset.
 *  - Foreground tier (text) parallaxes at 8% — gives real depth without nausea.
 *  - Active page fades from 1.0 to 0.55 alpha as it leaves; off-axis pages shrink to 92%.
 *  - First time a page settles to centre, plays an Emphasized-Decelerate entrance:
 *      illustration: scale 0.82 → 1.0 + alpha 0 → 1 over 500 ms
 *      title:        ty 32 → 0 + alpha 0 → 1 over 400 ms, delay 80 ms
 *      body:         ty 32 → 0 + alpha 0 → 1 over 400 ms, delay 160 ms
 *  - Centred illustration runs a 3.4 s breathing loop (±6 dp). Cancelled on neighbours.
 *  - Respects system reduce-motion: snaps to final state, skips loops.
 */
class WalkPageTransformer : ViewPager2.PageTransformer {

    override fun transformPage(page: View, position: Float) {
        val absPos = abs(position)
        val clamped = absPos.coerceAtMost(1f)

        val illustration = page.getTag(R.id.tag_walk_illustration) as? View
        val textContainer = page.getTag(R.id.tag_walk_text_container) as? View

        // Soft fade for adjacent pages — keeps the active page foregrounded.
        page.alpha = 1f - clamped * 0.45f

        // Depth-tier parallax.
        illustration?.translationX = -position * page.width * 0.35f
        textContainer?.translationX = -position * page.width * 0.08f

        // Light depth scale on adjacent pages.
        val depth = 0.92f + (1f - clamped) * 0.08f
        page.scaleX = depth
        page.scaleY = depth

        when {
            position == 0f -> {
                if (page.getTag(R.id.tag_walk_entrance_played) != true) {
                    page.setTag(R.id.tag_walk_entrance_played, true)
                    playEntrance(page)
                } else {
                    startBreathing(illustration)
                }
            }
            absPos >= 0.5f -> stopBreathing(illustration)
        }
    }

    private fun playEntrance(page: View) {
        val reduced = Motion.reduced(page.context)

        val illustration = page.getTag(R.id.tag_walk_illustration) as? View
        val title = page.getTag(R.id.tag_walk_title) as? View
        val body = page.getTag(R.id.tag_walk_body) as? View

        if (reduced) {
            listOfNotNull(illustration, title, body).forEach {
                it.alpha = 1f; it.translationY = 0f; it.scaleX = 1f; it.scaleY = 1f
            }
            return
        }

        illustration?.apply {
            alpha = 0f
            scaleX = 0.82f
            scaleY = 0.82f
            translationY = 0f
            animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(Motion.LONG_2)
                .setInterpolator(Motion.EmphasizedDecelerate)
                .withEndAction { startBreathing(this) }
                .start()
        }

        animateInUp(title, startDelay = Motion.STAGGER_GAP)
        animateInUp(body, startDelay = Motion.STAGGER_GAP * 2)
    }

    private fun animateInUp(view: View?, startDelay: Long) {
        view ?: return
        val offset = dp(view, 32f)
        view.alpha = 0f
        view.translationY = offset
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(startDelay)
            .setDuration(Motion.MEDIUM_4)
            .setInterpolator(Motion.EmphasizedDecelerate)
            .start()
    }

    private fun startBreathing(view: View?) {
        view ?: return
        if (Motion.reduced(view.context)) return
        val existing = view.getTag(R.id.tag_walk_float_animator) as? ValueAnimator
        if (existing?.isRunning == true) return
        val amplitude = -dp(view, 6f)
        val animator = ObjectAnimator.ofFloat(view, "translationY", 0f, amplitude, 0f).apply {
            duration = 3400L
            repeatCount = ValueAnimator.INFINITE
            interpolator = Motion.Standard
        }
        view.setTag(R.id.tag_walk_float_animator, animator)
        animator.start()
    }

    private fun stopBreathing(view: View?) {
        view ?: return
        val animator = view.getTag(R.id.tag_walk_float_animator) as? ValueAnimator
        animator?.cancel()
        view.setTag(R.id.tag_walk_float_animator, null)
    }

    private fun dp(view: View, value: Float): Float =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value, view.resources.displayMetrics
        )
}
