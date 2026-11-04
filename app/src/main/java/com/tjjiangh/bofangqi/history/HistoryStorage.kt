package com.tjjiangh.bofangqi.history

import android.content.Context
import com.tjjiangh.bofangqi.download.DownloadStorage
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object HistoryStorage {
    private const val PREF_NAME = "bofangqi_history_pref"
    private const val KEY_HISTORY_LIST = "history_list"

    fun getRelativeTime(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val minutes = diff / (1000 * 60)
        val hours = minutes / 60
        val days = hours / 24

        return when {
            minutes < 1 -> "刚刚"
            minutes < 60 -> "${minutes}分钟前"
            hours < 24 -> "${hours}小时前"
            days < 30 -> "${days}天前"
            else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(timestamp))
        }
    }

    /**
     * 核心：根据剧名智能检索以往保存的全套 30 集选集数据
     */
    fun getSavedRoutesData(context: Context, showName: String): String {
        val cleanShow = DownloadStorage.extractShowName(showName)
        if (cleanShow.isBlank()) return ""
        val list = getHistoryList(context)
        val item = list.firstOrNull { DownloadStorage.extractShowName(it.showName) == cleanShow && it.routesData.isNotBlank() }
        return item?.routesData ?: ""
    }

    fun saveProgress(
        context: Context,
        showName: String,
        epName: String,
        routeName: String,
        videoUrl: String,
        posterUrl: String,
        routesData: String = "",
        headers: Map<String, String> = emptyMap(),
        position: Long,
        duration: Long
    ) {
        if (showName.isBlank()) return
        val cleanShow = DownloadStorage.extractShowName(showName)
        val list = getHistoryList(context).toMutableList()
        val index = list.indexOfFirst { DownloadStorage.extractShowName(it.showName) == cleanShow }

        // 优先使用当前传入的 routesData，若为空则自动提取以往保存过的 30 集选集数据！
        val finalRoutesData = if (routesData.isNotBlank()) {
            routesData
        } else {
            getSavedRoutesData(context, cleanShow)
        }

        val newItem = HistoryItem(
            id = if (index != -1) list[index].id else System.currentTimeMillis().toString(),
            title = cleanShow,
            showName = cleanShow,
            epName = epName,
            sourceName = routeName,
            routeName = routeName,
            videoUrl = videoUrl,
            posterUrl = posterUrl,
            headers = headers,
            routesData = finalRoutesData,
            timestamp = System.currentTimeMillis(),
            currentPosition = position,
            duration = duration
        )

        if (index != -1) list.removeAt(index)
        list.add(0, newItem)
        saveListToPref(context, list)
    }

    fun addOrUpdateHistory(context: Context, item: HistoryItem) {
        val cleanShow = DownloadStorage.extractShowName(item.showName.ifEmpty { item.title })
        val list = getHistoryList(context).toMutableList()
        val index = list.indexOfFirst { DownloadStorage.extractShowName(it.showName) == cleanShow }
        if (index != -1) list.removeAt(index)

        val routes = if (item.routesData.isNotBlank()) item.routesData else getSavedRoutesData(context, cleanShow)
        val cleanItem = item.copy(showName = cleanShow, title = cleanShow, routesData = routes)
        list.add(0, cleanItem)
        saveListToPref(context, list)
    }

    fun getSavedPosition(context: Context, showName: String, epName: String): Long {
        val cleanShow = DownloadStorage.extractShowName(showName)
        val list = getHistoryList(context)
        val item = list.firstOrNull { DownloadStorage.extractShowName(it.showName) == cleanShow && it.epName == epName }
        return item?.currentPosition ?: 0L
    }

    fun getHistoryList(context: Context): List<HistoryItem> {
        val pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonStr = pref.getString(KEY_HISTORY_LIST, "") ?: ""
        if (jsonStr.isBlank()) return emptyList()

        val list = mutableListOf<HistoryItem>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val rawTitle = obj.optString("showName", obj.optString("title", ""))
                val cleanShow = DownloadStorage.extractShowName(rawTitle)
                val routeVal = obj.optString("routeName", obj.optString("sourceName", ""))

                val headersMap = mutableMapOf<String, String>()
                val headersJson = obj.optJSONObject("headers")
                headersJson?.keys()?.forEach { k ->
                    headersMap[k] = headersJson.getString(k)
                }

                list.add(
                    HistoryItem(
                        id = obj.optString("id", System.currentTimeMillis().toString()),
                        title = cleanShow,
                        showName = cleanShow,
                        epName = obj.optString("epName"),
                        sourceName = routeVal,
                        routeName = routeVal,
                        videoUrl = obj.optString("videoUrl"),
                        posterUrl = obj.optString("posterUrl"),
                        headers = headersMap,
                        routesData = obj.optString("routesData", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        currentPosition = obj.optLong("currentPosition"),
                        duration = obj.optLong("duration")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun deleteHistory(context: Context, id: String) {
        val list = getHistoryList(context).filter { it.id != id }
        saveListToPref(context, list)
    }

    fun clearAllHistory(context: Context) {
        val pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        pref.edit().remove(KEY_HISTORY_LIST).apply()
    }

    private fun saveListToPref(context: Context, list: List<HistoryItem>) {
        val trimmedList = if (list.size > 50) list.subList(0, 50) else list
        val array = JSONArray()
        for (item in trimmedList) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.showName)
                put("showName", item.showName)
                put("epName", item.epName)
                put("sourceName", item.routeName)
                put("routeName", item.routeName)
                put("videoUrl", item.videoUrl)
                put("posterUrl", item.posterUrl)
                put("routesData", item.routesData)
                put("timestamp", item.timestamp)
                put("currentPosition", item.currentPosition)
                put("duration", item.duration)

                val headersJson = JSONObject()
                item.headers.forEach { (k, v) -> headersJson.put(k, v) }
                put("headers", headersJson)
            }
            array.put(obj)
        }
        val pref = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        pref.edit().putString(KEY_HISTORY_LIST, array.toString()).apply()
    }
}