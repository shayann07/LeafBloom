package com.devsphere.leafbloom.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class WalkthroughAdapter(
    private val layouts: List<Int>
) : RecyclerView.Adapter<WalkthroughAdapter.PageVH>() {

    inner class PageVH(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageVH {
        val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
        return PageVH(view)
    }

    override fun onBindViewHolder(holder: PageVH, position: Int) {}

    override fun getItemCount() = layouts.size

    override fun getItemViewType(position: Int): Int = layouts[position]
}