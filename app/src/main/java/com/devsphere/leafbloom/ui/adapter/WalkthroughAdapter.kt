package com.devsphere.leafbloom.ui.adapter

import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.devsphere.leafbloom.R

class WalkthroughAdapter(
    private val layouts: List<Int>
) : RecyclerView.Adapter<WalkthroughAdapter.PageVH>() {

    inner class PageVH(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageVH {
        val view = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
        return PageVH(view)
    }

    override fun onBindViewHolder(holder: PageVH, position: Int) {
        val page = holder.view
        page.setTag(R.id.tag_walk_entrance_played, null)
        val illustration = page.findViewById<View>(R.id.ivIllustration)
        val textContainer = page.findViewById<View>(R.id.textContainer)
        val title = page.findViewById<View>(R.id.tvTitle)
        val body = page.findViewById<View>(R.id.tvBody)
        page.setTag(R.id.tag_walk_illustration, illustration)
        page.setTag(R.id.tag_walk_text_container, textContainer)
        page.setTag(R.id.tag_walk_title, title)
        page.setTag(R.id.tag_walk_body, body)
        primeForEntrance(page, illustration, title, body)
    }

    override fun getItemCount() = layouts.size

    override fun getItemViewType(position: Int): Int = layouts[position]

    private fun primeForEntrance(page: View, illustration: View?, title: View?, body: View?) {
        val offset = dp(page, 32f)
        illustration?.apply {
            alpha = 0f
            scaleX = 0.82f
            scaleY = 0.82f
        }
        title?.apply {
            alpha = 0f
            translationY = offset
        }
        body?.apply {
            alpha = 0f
            translationY = offset
        }
    }

    private fun dp(view: View, value: Float): Float =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value, view.resources.displayMetrics
        )
}
