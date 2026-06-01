package com.devsphere.leafbloom.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.devsphere.leafbloom.R
import com.devsphere.leafbloom.data.model.HistoryItem
import com.devsphere.leafbloom.databinding.ItemHistoryBinding
import java.io.File

/**
 * History adapter with date-grouped section headers ("Today", "Yesterday", "12 May 2026").
 * Sealed [ListEntry] represents either a header row or an item row. Uses [ListAdapter] +
 * [DiffUtil] so chip switches and Room emissions only rebind the rows that actually changed.
 */
class HistoryAdapter(
    private val onItemClick: (HistoryItem) -> Unit,
    private val onDeleteClick: ((HistoryItem) -> Unit)? = null
) : ListAdapter<HistoryAdapter.ListEntry, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    sealed class ListEntry {
        data class Header(val label: String) : ListEntry()
        data class Item(val historyItem: HistoryItem) : ListEntry()
    }

    fun submitGroupedList(items: List<HistoryItem>, sectionLabels: Map<Long, String>) {
        val newEntries = mutableListOf<ListEntry>()
        var lastSection = ""
        for (item in items) {
            val section = sectionLabels[item.id] ?: ""
            if (section != lastSection) {
                newEntries.add(ListEntry.Header(section))
                lastSection = section
            }
            newEntries.add(ListEntry.Item(item))
        }
        submitList(newEntries)
    }

    fun submitEntries(entries: List<ListEntry>) = submitList(entries)

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is ListEntry.Header -> VIEW_TYPE_HEADER
        is ListEntry.Item -> VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_history_header, parent, false)
            HeaderViewHolder(view as TextView)
        } else {
            val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            ItemViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val entry = getItem(position)) {
            is ListEntry.Header -> (holder as HeaderViewHolder).bind(entry.label)
            is ListEntry.Item -> (holder as ItemViewHolder).bind(entry.historyItem)
        }
    }

    inner class HeaderViewHolder(private val textView: TextView) : RecyclerView.ViewHolder(textView) {
        fun bind(label: String) {
            textView.text = label
        }
    }

    inner class ItemViewHolder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: HistoryItem) {
            with(binding) {
                tvHistoryPlant.text = item.plantName
                tvHistoryStatus.text = item.status
                tvHistoryConfidence.text = "${item.confidence}%"
                tvHistoryDate.text = item.date

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
                btnDelete.setOnClickListener { onDeleteClick?.invoke(item) }
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ListEntry>() {
            override fun areItemsTheSame(oldItem: ListEntry, newItem: ListEntry): Boolean =
                when {
                    oldItem is ListEntry.Header && newItem is ListEntry.Header ->
                        oldItem.label == newItem.label
                    oldItem is ListEntry.Item && newItem is ListEntry.Item ->
                        oldItem.historyItem.id == newItem.historyItem.id
                    else -> false
                }

            override fun areContentsTheSame(oldItem: ListEntry, newItem: ListEntry): Boolean =
                oldItem == newItem
        }
    }
}
