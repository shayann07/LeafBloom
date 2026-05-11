/*
 * AUTH DISABLED — This screen is not in the current nav graph.
 * Kept for future Firebase Auth integration.
 * To re-enable: add loginFragment destination back to nav_graph.xml
 * and wire the navigation from the appropriate entry point.
 */

package com.devsphere.leafbloom.ui.auth

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.doOnLayout
import androidx.navigation.fragment.findNavController
import com.devsphere.leafbloom.R
import com.devsphere.leafbloom.databinding.FragmentLoginBinding
import com.devsphere.leafbloom.ui.common.BaseFragment

class LoginFragment : BaseFragment() {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupAdaptiveHeader(binding.headerContainer, binding.ivHeader)

        binding.apply {
            btnLogin.setOnClickListener {
                // TODO: Wire Firebase Auth login, then navigate
                // findNavController().navigate(R.id.homeFragment)
            }

            btnContinueWithGoogle.setOnClickListener {
                // TODO: Wire Google Sign-In, then navigate
                // findNavController().navigate(R.id.homeFragment)
            }

            tvSignUp.setOnClickListener {
                // TODO: Re-add signupFragment to nav_graph.xml first
                // findNavController().navigate(R.id.signupFragment)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}