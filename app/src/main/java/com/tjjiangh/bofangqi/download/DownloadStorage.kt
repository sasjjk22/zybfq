package com.tjjiangh.bofangqi.download

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class ShowGroup(
    val showName: String,
    val items: List<DownloadItem>,
    val totalSize: Long,
    val sourceName: String
)

object DownloadStorage {

    private const val PREF_NAME = "download_tasks_pref"
    private const val KEY_TASKS_JSON = "key_tasks_json"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getTasks(context: Context): List<DownloadItem> {
        val jsonStr = getPrefs(context).getString(KEY_TASKS_JSON, null) ?: return emptyList()
        val list = mutableListOf<DownloadItem>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val headersMap = mutableMapOf<String, String>()
                val headersJson = obj.optJSONObject("headers")
                headersJson?.keys()?.forEach { key ->
                    headersMap[key] = headersJson.getString(key)
                }

                val rawTitle = obj.optString("title")
                val showName = obj.optString("showName", extractShowName(rawTitle))
                val epName = obj.optString("epName", extractEpisodeName(rawTitle))
                val routeName = obj.optString("routeName", "默认线路")

                list.add(
                    DownloadItem(
                        id = obj.optString("id"),
                        showName = showName,
                        epName = epName,
                        routeName = routeName,
                        title = rawTitle,
                        videoUrl = obj.optString("videoUrl"),
                        localPath = obj.optString("localPath"),
                        progress = obj.optInt("progress"),
                        status = obj.optInt("status"),
                        headers = headersMap,
                        fileSize = obj.optLong("fileSize", 0L),
                        sourceName = obj.optString("sourceName", "第三方资源"),
                        posterUrl = obj.optString("posterUrl"),
                        errorLog = obj.optString("errorLog", "")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun getDownloadList(context: Context): List<DownloadItem> = getTasks(context)

    fun saveTasks(context: Context, tasks: List<DownloadItem>) {
        val jsonArray = JSONArray()
        for (item in tasks) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("showName", item.showName)
            obj.put("epName", item.epName)
            obj.put("routeName", item.routeName)
            obj.put("title", item.title)
            obj.put("videoUrl", item.videoUrl)
            obj.put("localPath", item.localPath)
            obj.put("progress", item.progress)
            obj.put("status", item.status)
            obj.put("fileSize", item.fileSize)
            obj.put("sourceName", item.sourceName)
            obj.put("posterUrl", item.posterUrl)
            obj.put("errorLog", item.errorLog)

            val headersJson = JSONObject()
            item.headers.forEach { (k, v) -> headersJson.put(k, v) }
            obj.put("headers", headersJson)

            jsonArray.put(obj)
        }
        getPrefs(context).edit().putString(KEY_TASKS_JSON, jsonArray.toString()).apply()
    }

    fun saveOrUpdateTask(context: Context, task: DownloadItem) {
        val tasks = getTasks(context).toMutableList()
        val index = tasks.indexOfFirst { it.id == task.id }
        if (index >= 0) {
            tasks[index] = task
        } else {
            tasks.add(0, task)
        }
        saveTasks(context, tasks)
    }

    fun saveDownloadItem(context: Context, item: DownloadItem) = saveOrUpdateTask(context, item)

    fun deleteTask(context: Context, id: String) {
        val tasks = getTasks(context).toMutableList()
        val target = tasks.find { it.id == id }
        target?.let {
            if (it.localPath.isNotEmpty()) {
                val file = File(it.localPath)
                if (file.exists()) {
                    if (file.isDirectory) {
                        file.deleteRecursively()
                    } else {
                        file.parentFile?.deleteRecursively()
                        file.delete()
                    }
                }
            }
        }
        tasks.removeAll { it.id == id }
        saveTasks(context, tasks)
    }

    fun deleteDownload(context: Context, id: String) = deleteTask(context, id)

    fun deleteShowGroup(context: Context, showName: String) {
        val tasks = getTasks(context).toMutableList()
        val targets = tasks.filter { it.showName == showName }
        targets.forEach { deleteTask(context, it.id) }
    }

    fun clearShowDownloads(context: Context, showName: String) = deleteShowGroup(context, showName)

    fun getShowGroups(context: Context): List<ShowGroup> {
        val tasks = getTasks(context)
        val grouped = tasks.groupBy { it.showName }
        return grouped.map { (showName, items) ->
            var groupSize = 0L
            items.forEach { task ->
                if (task.localPath.isNotEmpty()) {
                    val file = File(task.localPath)
                    if (file.exists()) {
                        groupSize += if (file.isDirectory) file.walkTopDown().sumOf { f -> f.length() } else file.length()
                    }
                } else if (task.fileSize > 0) {
                    groupSize += task.fileSize
                }
            }
            ShowGroup(
                showName = showName,
                items = items,
                totalSize = groupSize,
                sourceName = items.firstOrNull()?.sourceName ?: "第三方资源"
            )
        }
    }

    fun getTotalCacheSizeMb(context: Context): String {
        val tasks = getTasks(context)
        val totalBytes = tasks.sumOf { it.fileSize }
        return formatFileSize(totalBytes)
    }

    fun extractShowName(rawTitle: String): String {
        if (rawTitle.contains("《") && rawTitle.contains("》")) {
            return rawTitle.substringAfter("《").substringBefore("》").trim()
        }
        if (rawTitle.contains("-")) {
            return rawTitle.split("-")[0].trim()
        }
        return rawTitle.split("第")[0].trim().ifEmpty { rawTitle }
    }

    fun extractEpisodeName(rawTitle: String): String {
        if (rawTitle.contains("第") && (rawTitle.contains("集") || rawTitle.contains("话"))) {
            val epNumber = rawTitle.substringAfter("第").substringBefore("集").substringBefore("话")
            return "第${epNumber}集"
        }
        return cleanTitle(rawTitle)
    }

    fun cleanTitle(rawTitle: String): String {
        var title = rawTitle
        val junkSuffixes = listOf(
            "高清完整版在线观看", "在线观看", "高清在线", "免费在线观看",
            "完整版", "大师兄影视", "百度影音", "全集在线"
        )
        for (junk in junkSuffixes) {
            title = title.replace(junk, "")
        }
        return title.replace("-", "").trim()
    }

    fun formatFileSize(sizeBytes: Long): String {
        if (sizeBytes <= 0) return "0 MB"
        val mb = sizeBytes.toDouble() / (1024 * 1024)
        return if (mb >= 1024) {
            String.format("%.2f GB", mb / 1024)
        } else {
            String.format("%.1f MB", mb)
        }
    }
}