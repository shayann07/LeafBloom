package com.devsphere.leafbloom

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.devsphere.leafbloom.databinding.FragmentSignupBinding

class SignupFragment : BaseFragment() {

    private var _binding: FragmentSignupBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSignupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.apply {
            btnSignup.setOnClickListener {
                // Handle login button click
                findNavController().navigate(R.id.homeFragment)
            }

            btnContinueWithGoogle.setOnClickListener {
                // Handle continue with Google button click
                findNavController().navigate(R.id.homeFragment)
            }

            tvLogin.setOnClickListener {
                // Handle continue with Google button click
                findNavController().navigate(R.id.loginFragment)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}