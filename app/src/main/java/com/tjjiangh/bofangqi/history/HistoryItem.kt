package com.tjjiangh.bofangqi.history

import com.tjjiangh.bofangqi.download.DownloadStorage
import java.io.Serializable

data class HistoryItem(
    val id: String = System.currentTimeMillis().toString(),
    val title: String = "",
    val showName: String = title,
    val epName: String = "",
    val sourceName: String = "",
    val routeName: String = sourceName,
    val videoUrl: String = "",
    val posterUrl: String = "",
    val headers: Map<String, String> = emptyMap(),
    val routesData: String = "", // 记忆全套 30 集选集数据
    val timestamp: Long = System.currentTimeMillis(),
    val currentPosition: Long = 0L,
    val duration: Long = 0L
) : Serializable {

    val cleanShowName: String get() = DownloadStorage.extractShowName(showName.ifEmpty { title })
    val cleanTitleText: String get() = "《$cleanShowName》 ${epName.ifEmpty { "正片" }}"

    fun getRelativeTime(): String {
        return HistoryStorage.getRelativeTime(timestamp)
    }

    fun getProgressText(): String {
        if (duration <= 0) return getRelativeTime()
        val percent = (currentPosition * 100 / duration).coerceIn(0, 100)
        return "已看 $percent%"
    }
}