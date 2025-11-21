package com.devsphere.leafbloom

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.vectordrawable.graphics.drawable.AnimatedVectorDrawableCompat
import com.devsphere.leafbloom.databinding.FragmentModelDownloadBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ModelDownloadFragment : Fragment() {

    private var _binding: FragmentModelDownloadBinding? = null
    private val binding get() = _binding!!

    private var leafAvd: AnimatedVectorDrawableCompat? = null
    private var isDownloadCompleted = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentModelDownloadBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTexts()
        setupAnimatedLeaf()
        startEnterAnimationAndLeaf()

        // Testing hook – replace with real model download flow
        startModelDownloadFlowForTesting()
    }

    private fun setupTexts() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvTitle.text = getString(R.string.model_download_title)
        binding.tvSubtitle.text = getString(R.string.model_download_subtitle)
    }

    private fun setupAnimatedLeaf() {
        leafAvd = AnimatedVectorDrawableCompat.create(
            requireContext(),
            R.drawable.loading_leaf_animated
        )
        binding.ivIllustration.setImageDrawable(leafAvd)
    }

    /**
     * One-time enter animation: fade + gentle scale in, then start the leaf AVD.
     */
    private fun startEnterAnimationAndLeaf() {
        val imageView = binding.ivIllustration

        imageView.apply {
            alpha = 0f
            scaleX = 0.9f
            scaleY = 0.9f
        }

        imageView.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(700L)
            .withEndAction {
                // Hero intro (segments) + continuous pulses/rock handled by the AVD itself
                leafAvd?.start()
            }
            .start()
    }

    /**
     * Test: long-running download so you can see the idle loop.
     * Replace this with your real model download logic.
     */
    private fun startModelDownloadFlowForTesting() {
        viewLifecycleOwner.lifecycleScope.launch {
            // Long enough to see the hero intro + idle motion
            delay(20_000L)
            onModelDownloadCompleted()
        }
    }

    private fun onModelDownloadCompleted() {
        if (!isAdded || isDownloadCompleted) return

        isDownloadCompleted = true

        // Stop the drawable so it doesn't keep animating under the exit fade
        leafAvd?.stop()

        binding.progressBar.visibility = View.GONE
        binding.tvSubtitle.text = getString(R.string.model_download_done_subtitle)

        // Single, graceful exit → then navigate
        runExitAnimation {
            navigateToNextScreen()
        }
    }

    /**
     * EXIT fade: slow fade-out + slight scale-down.
     */
    private fun runExitAnimation(onEnd: () -> Unit) {
        binding.ivIllustration.animate()
            .alpha(0f)
            .scaleX(0.85f)
            .scaleY(0.85f)
            .setDuration(700L)
            .withEndAction { onEnd() }
            .start()
    }

    private fun navigateToNextScreen() {
        // TODO: hook up to your actual navigation graph
        // findNavController().navigate(R.id.action_modelDownloadFragment_to_onboardingFragment)
        findNavController().navigateUp()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        leafAvd?.stop()
        leafAvd = null

        _binding = null
    }
}
