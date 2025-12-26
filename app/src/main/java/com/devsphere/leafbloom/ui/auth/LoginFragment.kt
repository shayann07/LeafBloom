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
                // Handle login button click
                findNavController().navigate(R.id.homeFragment)
            }

            btnContinueWithGoogle.setOnClickListener {
                // Handle continue with Google button click
                findNavController().navigate(R.id.homeFragment)
            }

            tvSignUp.setOnClickListener {
                // Handle continue with Google button click
                findNavController().navigate(R.id.signupFragment)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}