package com.devsphere.leafbloom.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.devsphere.leafbloom.data.model.ChatMessage
import com.devsphere.leafbloom.data.repository.ChatRepository
import com.google.ai.client.generativeai.Chat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ChatUiState {
    data object Initializing : ChatUiState()
    data object Ready : ChatUiState()
    data object Sending : ChatUiState()
    data class Error(val message: String) : ChatUiState()
}

class ChatViewModel(systemPrompt: String) : ViewModel() {

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Initializing)
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val chat: Chat = ChatRepository.startChat(systemPrompt)

    init {
        fetchOpeningMessage()
    }

    private fun fetchOpeningMessage() {
        viewModelScope.launch {
            val prompt = "Based on the context provided in your instructions, " +
                "give a brief, friendly opening message. " +
                "Keep it to 1-2 short sentences — just acknowledge the plant situation and invite the user to ask anything."

            ChatRepository.sendMessage(chat, prompt)
                .onSuccess { response ->
                    _messages.value = listOf(ChatMessage(text = response, isUser = false))
                    _uiState.value = ChatUiState.Ready
                }
                .onFailure { e ->
                    _uiState.value = ChatUiState.Error(friendlyErrorMessage(e, isInit = true))
                }
        }
    }

    fun sendMessage(userText: String) {
        if (userText.isBlank()) return
        _messages.value = _messages.value + ChatMessage(text = userText.trim(), isUser = true)
        _uiState.value = ChatUiState.Sending

        viewModelScope.launch {
            ChatRepository.sendMessage(chat, userText.trim())
                .onSuccess { response ->
                    _messages.value = _messages.value + ChatMessage(text = response, isUser = false)
                    _uiState.value = ChatUiState.Ready
                }
                .onFailure { e ->
                    _uiState.value = ChatUiState.Error(friendlyErrorMessage(e, isInit = false))
                }
        }
    }

    private fun friendlyErrorMessage(e: Throwable, isInit: Boolean): String {
        val raw = generateSequence(e) { it.cause }
            .mapNotNull { it.message }.joinToString(" ").lowercase()
        return when {
            "503" in raw || "unavailable" in raw || "overloaded" in raw || "high demand" in raw ->
                "The AI is overloaded right now. Please try again in a moment."
            "429" in raw || "quota" in raw || "rate" in raw || "resource_exhausted" in raw ->
                "You're chatting too fast or have hit today's limit. Please wait a minute and try again."
            "api key" in raw || "permission" in raw || "401" in raw || "403" in raw ->
                "Authentication problem with the AI service. Please contact support."
            "network" in raw || "unable to resolve host" in raw || "timeout" in raw ||
                "unreachable" in raw || "enetunreach" in raw || "connectexception" in raw ||
                "failed to connect" in raw ->
                "No internet connection. Check your network and try again."
            "safety" in raw || "blocked" in raw ->
                "That message couldn't be processed due to content safety rules. Try rephrasing."
            else -> if (isInit) "Failed to start the chat. Please try again."
                    else "Something went wrong. Please try again."
        }
    }

    fun clearError() {
        _uiState.value = ChatUiState.Ready
    }

    class Factory(private val systemPrompt: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ChatViewModel(systemPrompt) as T
    }
}
