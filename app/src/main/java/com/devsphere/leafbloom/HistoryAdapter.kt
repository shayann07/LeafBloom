package com.devsphere.leafbloom

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.devsphere.leafbloom.databinding.ItemHistoryBinding

class HistoryAdapter(private val items: List<HistoryItem>) :
    RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

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
            tvHistoryDate.text = item.date
            ivHistoryThumb.setImageResource(item.imageResId)
        }
    }

    override fun getItemCount() = items.size
}
