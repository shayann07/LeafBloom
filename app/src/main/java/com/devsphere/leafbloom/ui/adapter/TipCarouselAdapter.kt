package com.devsphere.leafbloom.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.devsphere.leafbloom.databinding.ItemTipCardBinding

data class CarouselTip(
    @StringRes val tagRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    @DrawableRes val iconRes: Int,
    @ColorRes val cardBgRes: Int,
    @ColorRes val tagColorRes: Int
)

class TipCarouselAdapter(
    private val tips: List<CarouselTip>
) : RecyclerView.Adapter<TipCarouselAdapter.TipViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TipViewHolder {
        val binding = ItemTipCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return TipViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TipViewHolder, position: Int) {
        holder.bind(tips[position])
    }

    override fun getItemCount(): Int = tips.size

    class TipViewHolder(private val binding: ItemTipCardBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(tip: CarouselTip) {
            val ctx = binding.root.context
            binding.tvTipTag.setText(tip.tagRes)
            binding.tvTipTitle.setText(tip.titleRes)
            binding.tvTipBody.setText(tip.bodyRes)
            binding.ivTipIcon.setImageResource(tip.iconRes)
            binding.cardTip.setCardBackgroundColor(ContextCompat.getColor(ctx, tip.cardBgRes))
            binding.tvTipTag.setTextColor(ContextCompat.getColor(ctx, tip.tagColorRes))
        }
    }
}
