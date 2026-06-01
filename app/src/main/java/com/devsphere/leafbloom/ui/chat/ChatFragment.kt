package com.devsphere.leafbloom.ui.chat

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.devsphere.leafbloom.R
import com.devsphere.leafbloom.databinding.FragmentChatBinding
import com.devsphere.leafbloom.ui.adapter.ChatAdapter
import com.devsphere.leafbloom.ui.common.BaseFragment
import com.devsphere.leafbloom.ui.motion.Motion
import com.devsphere.leafbloom.ui.motion.bounceOnPress
import com.devsphere.leafbloom.ui.motion.entranceFadeUp
import com.devsphere.leafbloom.ui.motion.primeFadeUp
import com.devsphere.leafbloom.ui.motion.snapVisible
import com.devsphere.leafbloom.util.SnackbarUtils
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ChatFragment : BaseFragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ChatViewModel
    private lateinit var chatAdapter: ChatAdapter

    private var typingAnimator: AnimatorSet? = null
    private var hasPlayedEntrance: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        postponeEnterTransition()
        applyChatInsets(binding.root)

        val systemPrompt = arguments?.getString("system_prompt") ?: ""
        val contextTitle = arguments?.getString("context_title") ?: getString(R.string.chat_with_ai)

        viewModel = ViewModelProvider(
            this, ChatViewModel.Factory(systemPrompt)
        )[ChatViewModel::class.java]

        binding.tvTitle.text = contextTitle

        setupRecyclerView()
        setupInputBar()
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.btnBack.bounceOnPress()
        playEntrance()
        observeState()
    }

    private fun playEntrance() {
        val targets = listOf(
            binding.btnBack, binding.tvTitle, binding.rvChat, binding.inputBarCard
        )
        if (hasPlayedEntrance || Motion.reduced(requireContext())) {
            targets.forEach { it.snapVisible() }
            startPostponedEnterTransition()
            return
        }
        binding.btnBack.primeFadeUp(12f)
        binding.tvTitle.primeFadeUp(16f)
        binding.rvChat.primeFadeUp(20f)
        binding.inputBarCard.primeFadeUp(24f)

        androidx.core.view.OneShotPreDrawListener.add(binding.root) {
            if (_binding == null) return@add
            startPostponedEnterTransition()
            binding.btnBack.entranceFadeUp(delay = 0L, duration = Motion.LONG_2)
            binding.tvTitle.entranceFadeUp(delay = 80L, duration = Motion.LONG_2)
            binding.rvChat.entranceFadeUp(delay = 180L, duration = Motion.LONG_2)
            binding.inputBarCard.entranceFadeUp(delay = 280L, duration = Motion.LONG_2)
            hasPlayedEntrance = true
        }
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter()
        binding.rvChat.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply {
                stackFromEnd = true
            }
            adapter = chatAdapter
        }
    }

    private fun setupInputBar() {
        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text?.toString() ?: return@setOnClickListener
            if (text.isNotBlank()) {
                animateSendBounce()
                viewModel.sendMessage(text)
                binding.etMessage.text?.clear()
            }
        }
    }

    private fun animateSendBounce() {
        val target = binding.btnSend
        val scaleDown = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(target, View.SCALE_X, 1f, 0.85f),
                ObjectAnimator.ofFloat(target, View.SCALE_Y, 1f, 0.85f)
            )
            duration = 90
        }
        val scaleUp = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(target, View.SCALE_X, 0.85f, 1f),
                ObjectAnimator.ofFloat(target, View.SCALE_Y, 0.85f, 1f)
            )
            duration = 150
        }
        AnimatorSet().apply {
            playSequentially(scaleDown, scaleUp)
            start()
        }
    }

    private fun startTypingAnimation() {
        if (typingAnimator?.isRunning == true) return
        val icon = binding.ivTypingIcon
        val scaleX = ObjectAnimator.ofFloat(icon, View.SCALE_X, 1f, 1.2f, 1f).apply {
            duration = 1000
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
        }
        val scaleY = ObjectAnimator.ofFloat(icon, View.SCALE_Y, 1f, 1.2f, 1f).apply {
            duration = 1000
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.RESTART
        }
        typingAnimator = AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            start()
        }
    }

    private fun stopTypingAnimation() {
        typingAnimator?.cancel()
        typingAnimator = null
        binding.ivTypingIcon.scaleX = 1f
        binding.ivTypingIcon.scaleY = 1f
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.messages.collectLatest { messages ->
                chatAdapter.submitList(messages) {
                    if (messages.isNotEmpty()) {
                        binding.rvChat.smoothScrollToPosition(messages.size - 1)
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is ChatUiState.Initializing -> {
                        binding.typingIndicator.visibility = View.VISIBLE
                        startTypingAnimation()
                        binding.btnSend.isEnabled = false
                    }
                    is ChatUiState.Ready -> {
                        binding.typingIndicator.visibility = View.GONE
                        stopTypingAnimation()
                        binding.btnSend.isEnabled = true
                    }
                    is ChatUiState.Sending -> {
                        binding.typingIndicator.visibility = View.VISIBLE
                        startTypingAnimation()
                        binding.btnSend.isEnabled = false
                    }
                    is ChatUiState.Error -> {
                        binding.typingIndicator.visibility = View.GONE
                        stopTypingAnimation()
                        binding.btnSend.isEnabled = true
                        SnackbarUtils.showSnackbar(
                            requireView(), state.message,
                            Snackbar.LENGTH_LONG, SnackbarUtils.Type.ERROR
                        )
                        viewModel.clearError()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopTypingAnimation()
        _binding = null
    }

    private fun applyChatInsets(target: View) {
        ViewCompat.setOnApplyWindowInsetsListener(target) { v, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottom = maxOf(sysBars.bottom, ime.bottom)
            v.setPadding(v.paddingLeft, sysBars.top, v.paddingRight, bottom)
            insets
        }
    }
}
