package com.tjjiangh.bofangqi.source

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

object SourceStorage {
    private const val PREF_NAME = "bofangqi_sources_pref"
    private const val KEY_SOURCES_JSON = "key_sources_json"
    private const val KEY_ACTIVE_SOURCE_ID = "key_active_source_id"

    private val DEFAULT_SOURCES = listOf(
        SourceModel(
            id = "default_1",
            name = "🚀 1-光速资源 (原生极速API)",
            url = "https://api.guangsuapi.com/api.php/provide/vod/",
            apiUrl = "https://api.guangsuapi.com/api.php/provide/vod/",
            groupName = "预置源",
            type = SourceModel.TYPE_API,
            isSelected = true
        ),
        SourceModel(
            id = "default_2",
            name = "🎬 2-非凡资源 (原生高清API)",
            url = "http://cj.ffzyapi.com/api.php/provide/vod/",
            apiUrl = "http://cj.ffzyapi.com/api.php/provide/vod/",
            groupName = "预置源",
            type = SourceModel.TYPE_API,
            isSelected = false
        ),
        SourceModel(
            id = "default_3",
            name = "🌌 3-量子资源 (原生秒播API)",
            url = "https://cj.lziapi.com/api.php/provide/vod/",
            apiUrl = "https://cj.lziapi.com/api.php/provide/vod/",
            groupName = "预置源",
            type = SourceModel.TYPE_API,
            isSelected = false
        ),
        SourceModel(
            id = "default_4",
            name = "🍿 4-大师兄影视 (网页嗅探源)",
            url = "https://www.ruiding3bu.com/",
            apiUrl = "https://www.ruiding3bu.com/",
            groupName = "预置源",
            type = SourceModel.TYPE_WEB,
            isSelected = false
        ),
        SourceModel(
            id = "default_5",
            name = "🌲 5-黑木耳影视 (网页嗅探源)",
            url = "https://www.heimuer.tv/",
            apiUrl = "https://www.heimuer.tv/",
            groupName = "预置源",
            type = SourceModel.TYPE_WEB,
            isSelected = false
        )
    )

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getAllSources(context: Context): List<SourceModel> {
        val jsonStr = getPrefs(context).getString(KEY_SOURCES_JSON, null)

        if (jsonStr.isNullOrBlank()) {
            saveSources(context, DEFAULT_SOURCES)
            return DEFAULT_SOURCES
        }

        val activeId = getPrefs(context).getString(KEY_ACTIVE_SOURCE_ID, DEFAULT_SOURCES[0].id)
        val list = mutableListOf<SourceModel>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id", System.currentTimeMillis().toString())
                val name = obj.optString("name")
                val url = obj.optString("url")
                val apiUrl = obj.optString("apiUrl", url)
                val groupName = obj.optString("groupName", "自定义")
                val type = obj.optInt("type", if (url.contains("api.php") || url.contains(".json")) SourceModel.TYPE_API else SourceModel.TYPE_WEB)
                val speed = obj.optLong("speedMs", -1L)

                list.add(
                    SourceModel(
                        id = id,
                        name = name,
                        url = url,
                        apiUrl = apiUrl,
                        groupName = groupName,
                        type = type,
                        speedMs = speed,
                        isSelected = (id == activeId)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (list.isEmpty()) {
            return DEFAULT_SOURCES
        }

        if (list.none { it.isSelected }) {
            list[0] = list[0].copy(isSelected = true)
        }

        return list
    }

    fun getSources(context: Context): List<SourceModel> = getAllSources(context)

    fun getActiveSource(context: Context): SourceModel? {
        val sources = getAllSources(context)
        return sources.firstOrNull { it.isSelected } ?: sources.firstOrNull()
    }

    fun setActiveSource(context: Context, sourceId: String) {
        getPrefs(context).edit().putString(KEY_ACTIVE_SOURCE_ID, sourceId).apply()
        val sources = getAllSources(context)
        val updated = sources.map {
            it.copy(isSelected = (it.id == sourceId))
        }
        saveSources(context, updated)
    }

    fun addSource(context: Context, source: SourceModel) {
        val list = getAllSources(context).toMutableList()
        list.removeAll { it.apiUrl == source.apiUrl || it.url == source.url }
        list.add(0, source)
        saveSources(context, list)
    }

    fun addSource(context: Context, name: String, url: String) {
        val type = if (url.contains("api.php") || url.contains(".json")) SourceModel.TYPE_API else SourceModel.TYPE_WEB
        val newSource = SourceModel(
            id = System.currentTimeMillis().toString(),
            name = name.ifEmpty { "自定义站点" },
            url = url,
            apiUrl = url,
            type = type
        )
        addSource(context, newSource)
    }

    fun addSource(context: Context, url: String) {
        addSource(context, "自定义站点", url)
    }

    fun addSources(context: Context, newSources: List<SourceModel>) {
        val list = getAllSources(context).toMutableList()
        for (src in newSources) {
            if (list.none { it.apiUrl == src.apiUrl || it.url == src.url }) {
                list.add(src)
            }
        }
        saveSources(context, list)
    }

    fun deleteSource(context: Context, sourceId: String) {
        val list = getAllSources(context).toMutableList()
        list.removeAll { it.id == sourceId }
        saveSources(context, list)
    }

    /**
     * 批量选择性删除指定的源站点 ID 集合 (精准保护用户需要的源)
     */
    fun deleteBatchSources(context: Context, targetIds: Set<String>) {
        if (targetIds.isEmpty()) return
        val list = getAllSources(context).toMutableList()
        list.removeAll { targetIds.contains(it.id) }
        saveSources(context, list)
    }

    fun saveSources(context: Context, sources: List<SourceModel>) {
        val array = JSONArray()
        for (item in sources) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("url", item.url)
                put("apiUrl", item.apiUrl)
                put("groupName", item.groupName)
                put("type", item.type)
                put("speedMs", item.speedMs)
            }
            array.put(obj)
        }
        getPrefs(context).edit().putString(KEY_SOURCES_JSON, array.toString()).apply()
    }

    fun getWebBrowsingUrl(rawUrl: String): String {
        if (rawUrl.isBlank()) return "https://www.ruiding3bu.com/"
        var url = rawUrl.trim()
        if (url.contains("ruiding3bu.com/api.php")) {
            return "https://www.ruiding3bu.com/"
        }
        return url
    }
}