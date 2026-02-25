package com.devsphere.leafbloom.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.devsphere.leafbloom.databinding.ItemHistoryBinding
import com.devsphere.leafbloom.data.model.HistoryItem

class HistoryAdapter(
    private val items: List<HistoryItem>,
    private val onItemClick: (HistoryItem) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        with(holder.binding) {
            tvHistoryPlant.text = item.plantName
            tvHistoryStatus.text = item.status
            tvHistoryConfidence.text = "${item.confidence}%"
            tvHistoryDate.text = item.date
            ivHistoryThumb.setImageResource(item.imageResId)
            
            root.setOnClickListener {
                onItemClick(item)
            }
        }
    }

    override fun getItemCount() = items.size
}
