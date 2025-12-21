package com.devsphere.leafbloom

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.devsphere.leafbloom.databinding.FragmentModelDownloadBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ModelDownloadFragment : BaseFragment() {

    private var _binding: FragmentModelDownloadBinding? = null
    private val binding get() = _binding!!

    private var isDownloadCompleted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Enable edge-to-edge for this screen
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

        setupTexts()
        startEnterAnimation()

        // *** Temporary testing flow ***
        startModelDownloadFlowForTesting()
    }

    private fun setupTexts() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvTitle.text = getString(R.string.model_download_title)
        binding.tvSubtitle.text = getString(R.string.model_download_subtitle)
    }

    /**
     * Intro fade-in + slight overshoot scale-in animation.
     * When done → starts the LeafBloomLoadingView animation loop.
     */
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

    /**
     * TEMP: Fake 20s download to demo animation.
     * Replace this with real model download logic.
     */
    private fun startModelDownloadFlowForTesting() {
        viewLifecycleOwner.lifecycleScope.launch {
            delay(20_000L)
            onModelDownloadCompleted()
        }
    }

    private fun onModelDownloadCompleted() {
        if (!isAdded || isDownloadCompleted) return
        isDownloadCompleted = true

        binding.progressBar.visibility = View.GONE
        binding.tvSubtitle.text = getString(R.string.model_download_done_subtitle)

        // Let the custom view play its own premium exit animation,
        // then navigate once it's fully faded out.
        binding.leafBloom.playCompletionAndStop {
//            navigateToNextScreen()
        }

        binding.leafBloom.setOnClickListener {
            navigateToNextScreen()
        }
    }

    private fun navigateToNextScreen() {
        // TODO: replace with your real nav target
        findNavController().navigate(R.id.loginFragment)
    }

    override fun onDestroyView() {
        // HARD STOP to avoid orphan animators when the view hierarchy goes away
        binding.leafBloom.stopLoop()
        _binding = null
        super.onDestroyView()
    }
}
