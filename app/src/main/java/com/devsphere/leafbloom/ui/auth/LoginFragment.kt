package com.devsphere.leafbloom.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
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