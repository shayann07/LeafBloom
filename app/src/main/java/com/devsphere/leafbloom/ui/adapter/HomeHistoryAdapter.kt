package com.devsphere.leafbloom.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.devsphere.leafbloom.R
import com.devsphere.leafbloom.data.model.HistoryItem
import com.devsphere.leafbloom.databinding.ItemHistoryHomeBinding
import java.io.File

/**
 * Adapter for the compact history list shown on the Home screen.
 * Uses item_history_home.xml (no card wrapper, no delete button).
 */
class HomeHistoryAdapter(
    private val onItemClick: (HistoryItem) -> Unit
) : ListAdapter<HistoryItem, HomeHistoryAdapter.ViewHolder>(DIFF_CALLBACK) {

    class ViewHolder(val binding: ItemHistoryHomeBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryHomeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            tvHistoryPlant.text = item.plantName
            tvHistoryStatus.text = item.status
            tvHistoryConfidence.text = "${item.confidence}%"
            tvHistoryDate.text = item.date

            // Load image from file path with fallback
            val imagePath = item.imagePath
            if (imagePath != null && File(imagePath).exists()) {
                Glide.with(ivHistoryThumb.context)
                    .load(File(imagePath))
                    .centerCrop()
                    .placeholder(R.drawable.history_item)
                    .error(R.drawable.history_item)
                    .into(ivHistoryThumb)
            } else {
                ivHistoryThumb.setImageResource(R.drawable.history_item)
            }

            root.setOnClickListener { onItemClick(item) }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<HistoryItem>() {
            override fun areItemsTheSame(old: HistoryItem, new: HistoryItem) = old.id == new.id
            override fun areContentsTheSame(old: HistoryItem, new: HistoryItem) = old == new
        }
    }
}
