package com.devsphere.leafbloom.ui.modelDownload

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.devsphere.leafbloom.R
import com.devsphere.leafbloom.databinding.FragmentModelDownloadBinding
import com.devsphere.leafbloom.prefs.UserPrefs
import com.devsphere.leafbloom.ui.common.BaseFragment
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ModelDownloadFragment : BaseFragment() {

    private var _binding: FragmentModelDownloadBinding? = null
    private val binding get() = _binding!!

    private var isCompleted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentModelDownloadBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupPhase1()
        startEnterAnimation()
        startPreparationFlow()
    }

    // -------------------------------------------------------
    // Phase Setup — each phase updates title, subtitle, step
    // -------------------------------------------------------

    private fun setupPhase1() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvTitle.text = getString(R.string.model_download_title)
        binding.tvSubtitle.text = getString(R.string.model_download_subtitle)
        binding.tvStep.text = getString(R.string.model_prep_step_format, 1, 3)
        binding.tvStep.visibility = View.VISIBLE
    }

    private fun transitionToPhase2() {
        crossfadeText(
            newTitle = getString(R.string.model_prep_phase2_title),
            newSubtitle = getString(R.string.model_prep_phase2_subtitle),
            newStep = getString(R.string.model_prep_step_format, 2, 3)
        )
    }

    private fun transitionToPhase3() {
        crossfadeText(
            newTitle = getString(R.string.model_prep_done_title),
            newSubtitle = getString(R.string.model_download_done_subtitle),
            newStep = null // hide step indicator
        )
        binding.progressBar.animate()
            .alpha(0f)
            .setDuration(300L)
            .withEndAction { binding.progressBar.visibility = View.GONE }
            .start()
    }

    // -------------------------------------------------------
    // Crossfade Text Transition
    // -------------------------------------------------------

    private fun crossfadeText(newTitle: String, newSubtitle: String, newStep: String?) {
        val fadeDuration = 250L

        // Fade out
        binding.tvTitle.animate().alpha(0f).setDuration(fadeDuration).start()
        binding.tvSubtitle.animate().alpha(0f).setDuration(fadeDuration).start()
        binding.tvStep.animate().alpha(0f).setDuration(fadeDuration)
            .withEndAction {
                if (!isAdded) return@withEndAction

                // Swap text
                binding.tvTitle.text = newTitle
                binding.tvSubtitle.text = newSubtitle

                if (newStep != null) {
                    binding.tvStep.text = newStep
                    binding.tvStep.visibility = View.VISIBLE
                } else {
                    binding.tvStep.visibility = View.GONE
                }

                // Fade in
                binding.tvTitle.animate().alpha(1f).setDuration(fadeDuration).start()
                binding.tvSubtitle.animate().alpha(1f).setDuration(fadeDuration).start()
                if (newStep != null) {
                    binding.tvStep.animate().alpha(0.7f).setDuration(fadeDuration).start()
                }
            }
            .start()
    }

    // -------------------------------------------------------
    // Enter Animation (existing LeafBloomLoadingView)
    // -------------------------------------------------------

    private fun startEnterAnimation() {
        val v = binding.leafBloom

        v.alpha = 0f
        v.scaleX = 0.9f
        v.scaleY = 0.9f

        v.animate().alpha(1f).scaleX(1.02f).scaleY(1.02f).setDuration(650L)
            .setInterpolator(DecelerateInterpolator()).withEndAction {
                // Tiny settle-back for a premium feel
                v.animate().scaleX(1f).scaleY(1f).setDuration(180L).start()

                binding.leafBloom.startLoop()   // start custom loop with its own entrance fade
            }.start()
    }

    // -------------------------------------------------------
    // 3-Phase Preparation Flow
    // -------------------------------------------------------

    private fun startPreparationFlow() {
        viewLifecycleOwner.lifecycleScope.launch {
            // Phase 1: Unpacking — already visible from setupPhase1()
            delay(3000L)
            if (!isAdded) return@launch

            // Phase 2: Optimizing
            transitionToPhase2()
            delay(3000L)
            if (!isAdded) return@launch

            // Phase 3: Done
            transitionToPhase3()
            delay(1500L)
            if (!isAdded) return@launch

            onPreparationCompleted()
        }
    }

    // -------------------------------------------------------
    // Completion → auto-navigate to Home
    // -------------------------------------------------------

    private fun onPreparationCompleted() {
        if (!isAdded || isCompleted) return
        isCompleted = true

        // Play the premium exit animation on the LeafBloomLoadingView,
        // then auto-navigate to Home
        binding.leafBloom.playCompletionAndStop {
            if (!isAdded) return@playCompletionAndStop

            // Mark first run as done so we skip this screen on next launch
            UserPrefs.getInstance(requireContext()).isFirstRun = false

            findNavController().navigate(R.id.action_modelDownload_to_home)
        }
    }

    override fun onDestroyView() {
        // HARD STOP to avoid orphan animators when the view hierarchy goes away
        binding.leafBloom.stopLoop()
        _binding = null
        super.onDestroyView()
    }
}
