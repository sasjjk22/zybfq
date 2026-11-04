package com.tjjiangh.bofangqi.subscription

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object SubscriptionEngine {

    /**
     * 原生 HTTP GET 请求（零第三方依赖）
     */
    private suspend fun fetchJsonString(urlString: String): String = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 12000
            connection.readTimeout = 12000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36")

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()
                response.toString()
            } else {
                ""
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun fetchCategories(apiUrl: String): List<CategoryItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<CategoryItem>()
        try {
            val jsonStr = fetchJsonString(apiUrl)
            if (jsonStr.isNotBlank()) {
                val json = JSONObject(jsonStr)
                val classArray = json.optJSONArray("class") ?: JSONArray()

                for (i in 0 until classArray.length()) {
                    val obj = classArray.getJSONObject(i)
                    val typeId = obj.optString("type_id")
                    val typeName = obj.optString("type_name")
                    if (typeId.isNotEmpty() && typeName.isNotEmpty()) {
                        list.add(CategoryItem(typeId, typeName))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    suspend fun fetchVodList(apiUrl: String, typeId: String = "", page: Int = 1): List<VodItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<VodItem>()
        try {
            var reqUrl = if (apiUrl.contains("?")) "$apiUrl&ac=detail&pg=$page" else "$apiUrl?ac=detail&pg=$page"
            if (typeId.isNotEmpty()) {
                reqUrl += "&t=$typeId"
            }
            val jsonStr = fetchJsonString(reqUrl)
            if (jsonStr.isNotBlank()) {
                val json = JSONObject(jsonStr)
                val vodArray = json.optJSONArray("list") ?: JSONArray()

                for (i in 0 until vodArray.length()) {
                    val obj = vodArray.getJSONObject(i)
                    list.add(parseVodItem(obj))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    suspend fun searchVod(apiUrl: String, keyword: String, page: Int = 1): List<VodItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<VodItem>()
        try {
            val reqUrl = if (apiUrl.contains("?")) "$apiUrl&ac=detail&wd=$keyword&pg=$page" else "$apiUrl?ac=detail&wd=$keyword&pg=$page"
            val jsonStr = fetchJsonString(reqUrl)
            if (jsonStr.isNotBlank()) {
                val json = JSONObject(jsonStr)
                val vodArray = json.optJSONArray("list") ?: JSONArray()

                for (i in 0 until vodArray.length()) {
                    val obj = vodArray.getJSONObject(i)
                    list.add(parseVodItem(obj))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }

    suspend fun fetchVodDetail(apiUrl: String, vodId: String): VodDetail? = withContext(Dispatchers.IO) {
        try {
            val reqUrl = if (apiUrl.contains("?")) "$apiUrl&ac=detail&ids=$vodId" else "$apiUrl?ac=detail&ids=$vodId"
            val jsonStr = fetchJsonString(reqUrl)
            if (jsonStr.isBlank()) return@withContext null

            val json = JSONObject(jsonStr)
            val vodArray = json.optJSONArray("list") ?: return@withContext null
            if (vodArray.length() == 0) return@withContext null

            val obj = vodArray.getJSONObject(0)
            val vodItem = parseVodItem(obj)

            val year = obj.optString("vod_year", "")
            val area = obj.optString("vod_area", "")
            val actor = obj.optString("vod_actor", "")
            val director = obj.optString("vod_director", "")
            val content = obj.optString("vod_content", "").replace(Regex("<[^>]*>"), "").trim()

            val playFromStr = obj.optString("vod_play_from", "")
            val playUrlStr = obj.optString("vod_play_url", "")

            val routes = parsePlayRoutes(playFromStr, playUrlStr)

            VodDetail(
                vodId = vodItem.vodId,
                vodName = vodItem.vodName,
                vodPic = vodItem.vodPic,
                vodRemarks = vodItem.vodRemarks,
                typeName = vodItem.typeName,
                vodYear = year,
                vodArea = area,
                vodActor = actor,
                vodDirector = director,
                vodContent = content,
                routes = routes
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseVodItem(obj: JSONObject): VodItem {
        return VodItem(
            vodId = obj.optString("vod_id"),
            vodName = obj.optString("vod_name"),
            vodPic = obj.optString("vod_pic"),
            vodRemarks = obj.optString("vod_remarks", obj.optString("vod_remarks_custom", "")),
            typeName = obj.optString("type_name", "")
        )
    }

    private fun parsePlayRoutes(playFrom: String, playUrl: String): List<PlayRoute> {
        if (playUrl.isEmpty()) return emptyList()

        val routeNames = playFrom.split("$$$")
        val routeUrls = playUrl.split("$$$")
        val resultRoutes = mutableListOf<PlayRoute>()

        for (i in routeUrls.indices) {
            val name = if (i < routeNames.size && routeNames[i].isNotBlank()) {
                formatRouteName(routeNames[i])
            } else {
                "线路 ${i + 1}"
            }

            val rawEpString = routeUrls[i]
            val episodes = mutableListOf<EpisodeItem>()
            val epArray = rawEpString.split("#")

            for (epStr in epArray) {
                if (epStr.isBlank()) continue
                if (epStr.contains("$")) {
                    val parts = epStr.split("$")
                    val epName = parts[0].trim()
                    val url = parts.getOrNull(1)?.trim() ?: ""
                    if (url.isNotEmpty()) {
                        episodes.add(EpisodeItem(epName, url))
                    }
                } else {
                    episodes.add(EpisodeItem("播放", epStr.trim()))
                }
            }

            if (episodes.isNotEmpty()) {
                resultRoutes.add(PlayRoute(routeName = name, episodes = episodes))
            }
        }

        return resultRoutes
    }

    private fun formatRouteName(raw: String): String {
        return when {
            raw.contains("w", ignoreCase = true) -> "线路W"
            raw.contains("f", ignoreCase = true) -> "线路F"
            raw.contains("z", ignoreCase = true) -> "线路Z"
            raw.contains("m3u8", ignoreCase = true) -> "极速极清"
            else -> raw.uppercase()
        }
    }
}