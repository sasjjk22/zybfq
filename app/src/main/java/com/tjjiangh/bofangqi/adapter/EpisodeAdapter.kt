package com.tjjiangh.bofangqi.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.tjjiangh.bofangqi.R
import com.tjjiangh.bofangqi.subscription.EpisodeItem

class EpisodeAdapter(
    private var episodes: List<EpisodeItem> = emptyList(),
    private val onEpisodeClick: (EpisodeItem, Int) -> Unit
) : RecyclerView.Adapter<EpisodeAdapter.EpisodeViewHolder>() {

    private var selectedIndex = -1

    fun updateEpisodes(newEpisodes: List<EpisodeItem>) {
        this.episodes = newEpisodes
        this.selectedIndex = -1
        notifyDataSetChanged()
    }

    fun setSelection(index: Int) {
        val oldPos = selectedIndex
        selectedIndex = index
        notifyItemChanged(oldPos)
        notifyItemChanged(selectedIndex)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_episode_chip, parent, false)
        return EpisodeViewHolder(view)
    }

    override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int) {
        val item = episodes[position]
        holder.bind(item, position == selectedIndex) {
            setSelection(position)
            onEpisodeClick(item, position)
        }
    }

    override fun getItemCount(): Int = episodes.size

    class EpisodeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvEpName: TextView = itemView.findViewById(R.id.tvEpName)

        fun bind(item: EpisodeItem, isSelected: Boolean, onClick: () -> Unit) {
            tvEpName.text = item.name

            if (isSelected) {
                tvEpName.setBackgroundResource(R.drawable.bg_episode_selected)
                tvEpName.setTextColor(ContextCompat.getColor(itemView.context, android.R.color.white))
            } else {
                tvEpName.setBackgroundResource(R.drawable.bg_episode_normal)
                tvEpName.setTextColor(0xCC000000.toInt())
            }

            itemView.setOnClickListener { onClick() }
        }
    }
}