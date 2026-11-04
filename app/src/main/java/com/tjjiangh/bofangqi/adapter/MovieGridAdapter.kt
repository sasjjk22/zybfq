package com.tjjiangh.bofangqi.adapter

import android.graphics.drawable.ColorDrawable
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
import com.tjjiangh.bofangqi.subscription.VodItem

class MovieGridAdapter(
    private var vodList: MutableList<VodItem> = mutableListOf(),
    private val onItemClick: (VodItem) -> Unit
) : RecyclerView.Adapter<MovieGridAdapter.MovieViewHolder>() {

    fun setData(newList: List<VodItem>) {
        vodList.clear()
        vodList.addAll(newList)
        notifyDataSetChanged()
    }

    fun appendData(moreList: List<VodItem>) {
        val startPos = vodList.size
        vodList.addAll(moreList)
        notifyItemRangeInserted(startPos, moreList.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MovieViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_movie_card, parent, false)
        return MovieViewHolder(view)
    }

    override fun onBindViewHolder(holder: MovieViewHolder, position: Int) {
        holder.bind(vodList[position], onItemClick)
    }

    override fun getItemCount(): Int = vodList.size

    class MovieViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivPoster: ImageView = itemView.findViewById(R.id.ivPoster)
        private val tvRemarks: TextView = itemView.findViewById(R.id.tvRemarks)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)

        fun bind(item: VodItem, onClick: (VodItem) -> Unit) {
            tvTitle.text = item.vodName

            if (item.vodRemarks.isNotEmpty()) {
                tvRemarks.visibility = View.VISIBLE
                tvRemarks.text = item.vodRemarks
            } else {
                tvRemarks.visibility = View.GONE
            }

            Glide.with(itemView.context)
                .load(item.vodPic)
                .placeholder(ColorDrawable(0xFF2A2A2E.toInt()))
                .transform(CenterCrop(), RoundedCorners(16))
                .into(ivPoster)

            itemView.setOnClickListener { onClick(item) }
        }
    }
}