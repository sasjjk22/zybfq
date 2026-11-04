package com.tjjiangh.bofangqi

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.tjjiangh.bofangqi.download.DownloadStorage

class DownloadFragment : Fragment() {

    private lateinit var tvTotalStats: TextView
    private lateinit var layoutShowsContainer: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_download, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvTotalStats = view.findViewById(R.id.tv_total_stats)
        layoutShowsContainer = view.findViewById(R.id.layout_shows_container)

        refreshShowList()
    }

    override fun onResume() {
        super.onResume()
        refreshShowList()
    }

    private fun refreshShowList() {
        layoutShowsContainer.removeAllViews()
        val context = context ?: return

        val showGroups = DownloadStorage.getShowGroups(context)

        var totalBytes = 0L
        showGroups.forEach { totalBytes += it.totalSize }

        tvTotalStats.text = "空间占用: ${DownloadStorage.formatFileSize(totalBytes)} | 缓存剧集: ${showGroups.size} 部"

        if (showGroups.isEmpty()) {
            val emptyTv = TextView(context).apply {
                text = "暂无离线缓存任务，快去播放页一键离线吧！"
                textSize = 14f
                setPadding(0, 60, 0, 0)
                setTextColor(resources.getColor(android.R.color.darker_gray, null))
            }
            layoutShowsContainer.addView(emptyTv)
            return
        }

        for (group in showGroups) {
            val cardView = CardView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 20)
                }
                radius = 24f
                cardElevation = 4f
                useCompatPadding = true
            }

            val cardContent = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(32, 28, 32, 28)
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val iconTv = TextView(context).apply {
                text = "🎬"
                textSize = 28f
                setPadding(0, 0, 24, 0)
            }

            val textContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val tvTitle = TextView(context).apply {
                text = "《${group.showName}》"
                textSize = 16f
                paint.isFakeBoldText = true
                setTextColor(resources.getColor(android.R.color.holo_blue_dark, null))
            }

            val tvSub = TextView(context).apply {
                text = "空间占用: ${DownloadStorage.formatFileSize(group.totalSize)} | 来源: ${group.sourceName}"
                textSize = 12f
                setPadding(0, 8, 0, 0)
                setTextColor(resources.getColor(android.R.color.darker_gray, null))
            }

            textContainer.addView(tvTitle)
            textContainer.addView(tvSub)

            val arrowTv = TextView(context).apply {
                text = "❯"
                textSize = 18f
                setTextColor(resources.getColor(android.R.color.darker_gray, null))
            }

            cardContent.addView(iconTv)
            cardContent.addView(textContainer)
            cardContent.addView(arrowTv)

            cardView.addView(cardContent)

            cardView.setOnClickListener {
                DownloadShowDetailActivity.start(context, group.showName)
            }

            layoutShowsContainer.addView(cardView)
        }
    }
}