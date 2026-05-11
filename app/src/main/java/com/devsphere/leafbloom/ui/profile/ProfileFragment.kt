package com.devsphere.leafbloom.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.devsphere.leafbloom.R
import com.devsphere.leafbloom.databinding.FragmentProfileBinding
import com.devsphere.leafbloom.prefs.UserPrefs
import com.devsphere.leafbloom.ui.common.BaseFragment
import com.devsphere.leafbloom.util.SnackbarUtils
import com.google.android.material.snackbar.Snackbar

class ProfileFragment : BaseFragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Lightweight layout setup — runs immediately
        setupAdaptiveHeader(binding.headerContainer, binding.ivHeader)

        // Defer click listener setup until after the first frame draws
        view.post {
            if (!isAdded) return@post
            setupUserInfo()
            setupClickListeners()
        }
    }

    private fun setupUserInfo() {
        binding.apply {
            tvUserName.text = getString(R.string.profile_placeholder_name)
            tvUserEmail.text = getString(R.string.profile_placeholder_email)

            val versionName = requireContext().packageManager
                .getPackageInfo(requireContext().packageName, 0).versionName
            tvVersion.text = getString(R.string.version, versionName)

            // Developer mode: long-press version text to enable
            tvVersion.setOnLongClickListener {
                val prefs = UserPrefs.getInstance(requireContext())
                prefs.isDevMode = true
                prefs.resetOnboarding()
                SnackbarUtils.showSnackbar(
                    binding.root,
                    "\uD83D\uDEE0\uFE0F Dev Mode ON — Onboarding reset. Restart the app to replay.",
                    Snackbar.LENGTH_LONG,
                    SnackbarUtils.Type.WARNING
                )
                true
            }
        }
    }

    private fun setupClickListeners() {
        binding.apply {
            // Back button
            btnBack.setOnClickListener {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }

            // Header camera + Profile picture camera — Coming Soon
            btnHeaderCamera.setOnClickListener { showComingSoon() }
            btnProfileCamera.setOnClickListener { showComingSoon() }

            // Account section
            rowEditProfile.setOnClickListener { showComingSoon() }
            rowChangePassword.setOnClickListener { showComingSoon() }

            // Model Management section
            rowStorageUsed.setOnClickListener { showComingSoon() }
            rowCheckUpdates.setOnClickListener { showComingSoon() }
            rowClearCache.setOnClickListener { showComingSoon() }

            // App Settings section
            rowLanguage.setOnClickListener { showComingSoon() }
            rowNotifications.setOnClickListener { showComingSoon() }
            rowDarkMode.setOnClickListener { showComingSoon() }
            rowClearHistory.setOnClickListener { showComingSoon() }

            // Legal section
            rowTerms.setOnClickListener { showComingSoon() }
            rowPrivacy.setOnClickListener { showComingSoon() }
            rowHelp.setOnClickListener { showComingSoon() }

            // Logout
            btnLogout.setOnClickListener { showComingSoon() }
        }
    }

    private fun showComingSoon() {
        SnackbarUtils.showSnackbar(
            binding.root,
            getString(R.string.coming_soon),
            Snackbar.LENGTH_SHORT,
            SnackbarUtils.Type.INFO
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
