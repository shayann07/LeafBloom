package com.devsphere.leafbloom.data.repository

import android.util.Log
import com.devsphere.leafbloom.BuildConfig
import com.google.ai.client.generativeai.Chat
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

object ChatRepository {

    private const val TAG = "ChatRepository"
    private const val MODEL_NAME = "gemini-3.1-flash-lite"

    private const val MAX_RETRIES = 3
    private const val INITIAL_BACKOFF_MS = 1200L

    fun startChat(systemPrompt: String): Chat {
        val model = GenerativeModel(
            modelName = MODEL_NAME,
            apiKey = BuildConfig.GEMINI_API_KEY,
            systemInstruction = content { text(systemPrompt) },
            generationConfig = generationConfig {
                temperature = 0.7f
                maxOutputTokens = 512
            }
        )
        return model.startChat()
    }

    suspend fun sendMessage(chat: Chat, userMessage: String): Result<String> =
        withContext(Dispatchers.IO) {
            var lastError: Throwable? = null
            var backoff = INITIAL_BACKOFF_MS

            repeat(MAX_RETRIES) { attempt ->
                try {
                    val response = chat.sendMessage(userMessage)
                    val text = response.text
                    return@withContext if (text != null) {
                        Result.success(text)
                    } else {
                        Result.failure(Exception("Empty response from AI"))
                    }
                } catch (e: Exception) {
                    lastError = e
                    if (!isTransient(e) || attempt == MAX_RETRIES - 1) {
                        Log.e(TAG, "sendMessage failed (attempt ${attempt + 1})", e)
                        return@withContext Result.failure(e)
                    }
                    Log.w(TAG, "Transient failure, retrying in ${backoff}ms (attempt ${attempt + 1})", e)
                    delay(backoff)
                    backoff *= 2
                }
            }
            Result.failure(lastError ?: Exception("Unknown error"))
        }

    private fun isTransient(e: Throwable): Boolean {
        val msg = (e.message ?: "").lowercase()
        return "503" in msg || "unavailable" in msg || "overloaded" in msg ||
            "high demand" in msg || "500" in msg || "internal" in msg ||
            "timeout" in msg || "deadline" in msg
    }
}
