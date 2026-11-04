package com.tjjiangh.bofangqi

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.tjjiangh.bofangqi.history.HistoryStorage

class HistoryFragment : Fragment() {

    private lateinit var btnClearHistory: Button
    private lateinit var layoutHistoryContainer: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_history, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnClearHistory = view.findViewById(R.id.btn_clear_history)
        layoutHistoryContainer = view.findViewById(R.id.layout_history_container)

        btnClearHistory.setOnClickListener {
            val context = context ?: return@setOnClickListener
            AlertDialog.Builder(context)
                .setTitle("清空播放历史")
                .setMessage("确定要清空所有观看历史记录吗？")
                .setPositiveButton("清空") { _, _ ->
                    HistoryStorage.clearAllHistory(context)
                    refreshHistoryList()
                    Toast.makeText(context, "已清空记录", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show()
        }

        refreshHistoryList()
    }

    override fun onResume() {
        super.onResume()
        refreshHistoryList()
    }

    private fun refreshHistoryList() {
        layoutHistoryContainer.removeAllViews()
        val context = context ?: return

        val historyList = HistoryStorage.getHistoryList(context)

        if (historyList.isEmpty()) {
            val emptyTv = TextView(context).apply {
                text = "暂无观看历史记录，快去挑选想看的影片吧！"
                textSize = 14f
                setPadding(0, 60, 0, 0)
                setTextColor(resources.getColor(android.R.color.darker_gray, null))
            }
            layoutHistoryContainer.addView(emptyTv)
            return
        }

        for (item in historyList) {
            val cardView = CardView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 20)
                }
                radius = 20f
                cardElevation = 4f
                useCompatPadding = true
            }

            val cardContent = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(32, 24, 32, 24)
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
                text = "《${item.showName}》"
                textSize = 16f
                paint.isFakeBoldText = true
                setTextColor(resources.getColor(android.R.color.holo_blue_dark, null))
            }

            val tvSub = TextView(context).apply {
                text = "${item.sourceName} · ${HistoryStorage.getRelativeTime(item.timestamp)}"
                textSize = 12f
                setPadding(0, 6, 0, 0)
                setTextColor(resources.getColor(android.R.color.darker_gray, null))
            }

            textContainer.addView(tvTitle)
            textContainer.addView(tvSub)

            val arrowTv = TextView(context).apply {
                text = "▶️"
                textSize = 18f
            }

            cardContent.addView(iconTv)
            cardContent.addView(textContainer)
            cardContent.addView(arrowTv)

            cardView.addView(cardContent)

            cardView.setOnClickListener {
                PlayerActivity.start(
                    context = context,
                    videoUrl = item.videoUrl,
                    title = item.title,
                    headers = HashMap(item.headers)
                )
            }

            cardView.setOnLongClickListener {
                AlertDialog.Builder(context)
                    .setTitle("删除历史记录")
                    .setMessage("确定要删除《${item.showName}》的观看记录吗？")
                    .setPositiveButton("删除") { _, _ ->
                        HistoryStorage.deleteHistory(context, item.id)
                        refreshHistoryList()
                        Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("取消", null)
                    .show()
                true
            }

            layoutHistoryContainer.addView(cardView)
        }
    }
}