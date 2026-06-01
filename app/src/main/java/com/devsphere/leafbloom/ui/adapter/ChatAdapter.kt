package com.devsphere.leafbloom.ui.adapter

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.devsphere.leafbloom.data.model.ChatMessage
import com.devsphere.leafbloom.databinding.ItemChatMessageAiBinding
import com.devsphere.leafbloom.databinding.ItemChatMessageUserBinding

class ChatAdapter : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    override fun getItemViewType(position: Int): Int =
        if (getItem(position).isUser) VIEW_TYPE_USER else VIEW_TYPE_AI

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_USER) {
            UserVH(ItemChatMessageUserBinding.inflate(inflater, parent, false))
        } else {
            AiVH(ItemChatMessageAiBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = getItem(position)
        when (holder) {
            is UserVH -> {
                holder.binding.tvMessage.text = msg.text
                holder.binding.bubbleCard.setOnLongClickListener { v ->
                    animateBubble(v)
                    copyToClipboard(v.context, msg.text)
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    true
                }
            }
            is AiVH -> {
                holder.binding.tvMessage.text = msg.text
                holder.binding.bubbleCard.setOnLongClickListener { v ->
                    animateBubble(v)
                    copyToClipboard(v.context, msg.text)
                    v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    true
                }
            }
        }
    }

    private fun animateBubble(v: View) {
        v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
        }.start()
    }

    private fun copyToClipboard(context: Context, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Chat message", text))
        // Android 13+ shows its own toast for clipboard copies
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    private class UserVH(val binding: ItemChatMessageUserBinding) :
        RecyclerView.ViewHolder(binding.root)

    private class AiVH(val binding: ItemChatMessageAiBinding) :
        RecyclerView.ViewHolder(binding.root)

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_AI = 2

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ChatMessage>() {
            override fun areItemsTheSame(old: ChatMessage, new: ChatMessage) =
                old.timestamp == new.timestamp && old.isUser == new.isUser

            override fun areContentsTheSame(old: ChatMessage, new: ChatMessage) =
                old == new
        }
    }
}
