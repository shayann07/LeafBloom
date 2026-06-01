package com.devsphere.leafbloom.ui.walkthrough

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.devsphere.leafbloom.R
import com.devsphere.leafbloom.databinding.FragmentWalkthroughBinding
import com.devsphere.leafbloom.ui.adapter.WalkthroughAdapter
import com.devsphere.leafbloom.ui.common.BaseFragment
import com.devsphere.leafbloom.ui.motion.Motion
import com.devsphere.leafbloom.ui.motion.bounceOnPress

class WalkthroughFragment : BaseFragment() {

    private var _binding: FragmentWalkthroughBinding? = null
    private val binding get() = _binding!!

    private val layouts = listOf(
        R.layout.layout_walk_1, R.layout.layout_walk_2, R.layout.layout_walk_3
    )

    private var lastPage = 0
    private var labelAnimator: AnimatorSet? = null
    private var skipAnimator: ObjectAnimator? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWalkthroughBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applySystemBarInsets(binding.root)
        setupViewPager()

        binding.btnSkip.bounceOnPress()
        binding.btnSkip.setOnClickListener { v ->
            performTickHaptic(v)
            findNavController().navigate(R.id.modelDownloadFragment)
        }
    }

    private fun setupViewPager() {
        binding.viewPagerWalk.apply {
            adapter = WalkthroughAdapter(layouts)
            offscreenPageLimit = 1
            setPageTransformer(WalkPageTransformer())
        }
        binding.dotsIndicator.attachTo(binding.viewPagerWalk)

        binding.viewPagerWalk.registerOnPageChangeCallback(object :
            ViewPager2.OnPageChangeCallback() {

            override fun onPageSelected(position: Int) {
                if (position != lastPage) {
                    performTickHaptic(binding.viewPagerWalk)
                    lastPage = position
                }
                val isLast = position == layouts.size - 1
                updateNextLabel(isLast)
                fadeSkip(visible = !isLast)
            }
        })

        binding.btnNext.setOnClickListener { v ->
            val index = binding.viewPagerWalk.currentItem
            if (index < layouts.size - 1) {
                // Page swipe is the feedback — no competing scale on btnNext.
                binding.viewPagerWalk.currentItem = index + 1
            } else {
                pressFeedback(v)
                performConfirmHaptic(v)
                findNavController().navigate(R.id.modelDownloadFragment)
            }
        }
    }

    private fun pressFeedback(view: View) {
        if (Motion.reduced(view.context)) return
        val down = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, View.SCALE_X, view.scaleX, 0.96f),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, view.scaleY, 0.96f)
            )
            duration = Motion.SHORT_3
            interpolator = Motion.EmphasizedAccelerate
        }
        val up = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, View.SCALE_X, 0.96f, 1f),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, 0.96f, 1f)
            )
            duration = Motion.SHORT_4
            interpolator = Motion.EmphasizedDecelerate
        }
        AnimatorSet().apply { playSequentially(down, up) }.start()
    }

    private fun updateNextLabel(isLast: Boolean) {
        val target = if (isLast) getString(R.string.continue_text) else getString(R.string.next)
        val btn = binding.btnNext
        if (btn.text?.toString() == target) return
        if (Motion.reduced(btn.context)) { btn.text = target; return }
        labelAnimator?.cancel()
        val fadeOut = ObjectAnimator.ofFloat(btn, View.ALPHA, btn.alpha, 0f).apply {
            duration = Motion.SHORT_3
            interpolator = Motion.EmphasizedAccelerate
        }
        val fadeIn = ObjectAnimator.ofFloat(btn, View.ALPHA, 0f, 1f).apply {
            duration = Motion.MEDIUM_2
            interpolator = Motion.EmphasizedDecelerate
        }
        labelAnimator = AnimatorSet().apply {
            playSequentially(fadeOut, fadeIn)
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    btn.alpha = 1f
                }
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    btn.alpha = 1f
                }
            })
            fadeOut.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    btn.text = target
                }
            })
            start()
        }
    }

    private fun fadeSkip(visible: Boolean) {
        val skip = binding.btnSkip
        val target = if (visible) 1f else 0f
        if (skip.alpha == target) return
        if (Motion.reduced(skip.context)) {
            skip.alpha = target
            skip.isClickable = visible
            return
        }
        skipAnimator?.cancel()
        skipAnimator = ObjectAnimator.ofFloat(skip, View.ALPHA, skip.alpha, target).apply {
            duration = Motion.MEDIUM_2
            interpolator = Motion.EmphasizedDecelerate
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: android.animation.Animator) {
                    if (visible) skip.isClickable = true
                }
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (!visible) skip.isClickable = false
                }
            })
            start()
        }
    }

    private fun performTickHaptic(view: View) {
        val constant = if (Build.VERSION.SDK_INT >= 34) {
            HapticFeedbackConstants.SEGMENT_TICK
        } else {
            HapticFeedbackConstants.CONTEXT_CLICK
        }
        view.performHapticFeedback(constant)
    }

    private fun performConfirmHaptic(view: View) {
        val constant = if (Build.VERSION.SDK_INT >= 30) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        view.performHapticFeedback(constant)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        labelAnimator?.cancel()
        skipAnimator?.cancel()
        _binding = null
    }
}
