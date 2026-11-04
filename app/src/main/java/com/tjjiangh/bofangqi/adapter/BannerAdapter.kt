package com.tjjiangh.bofangqi.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.tjjiangh.bofangqi.R
import com.tjjiangh.bofangqi.subscription.BannerItem

class BannerAdapter(
    private var items: List<BannerItem> = emptyList(),
    private val onItemClick: (BannerItem) -> Unit
) : RecyclerView.Adapter<BannerAdapter.BannerViewHolder>() {

    fun updateData(newItems: List<BannerItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BannerViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_home_banner, parent, false)
        return BannerViewHolder(view)
    }

    override fun onBindViewHolder(holder: BannerViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, onItemClick)
    }

    override fun getItemCount(): Int = items.size

    class BannerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivBanner: ImageView = itemView.findViewById(R.id.ivBanner)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvBannerTitle)
        private val tvTag: TextView = itemView.findViewById(R.id.tvBannerTag)

        fun bind(item: BannerItem, onClick: (BannerItem) -> Unit) {
            tvTitle.text = item.title
            tvTag.text = item.remarks.ifEmpty { "热播推荐" }

            // 使用 Glide 加载圆角 Banner 封面 (若项目使用的是 Coil，亦可直接替换)
            Glide.with(itemView.context)
                .load(item.picUrl)
                .transform(CenterCrop(), RoundedCorners(24))
                .into(ivBanner)

            itemView.setOnClickListener { onClick(item) }
        }
    }
}