package com.tjjiangh.bofangqi.subscription

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object SubscriptionEngine {

    private suspend fun fetchJsonString(urlString: String): String = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = URL(urlString).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext ""
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ""
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun fetchCategories(apiUrl: String): List<CategoryItem> = withContext(Dispatchers.IO) {
        try {
            val jsonString = fetchJsonString(apiUrl)
            if (jsonString.isBlank()) return@withContext emptyList()
            val classes = JSONObject(jsonString).optJSONArray("class") ?: JSONArray()
            buildList {
                for (index in 0 until classes.length()) {
                    val item = classes.optJSONObject(index) ?: continue
                    val id = item.optString("type_id")
                    val name = item.optString("type_name")
                    if (id.isNotEmpty() && name.isNotEmpty()) add(CategoryItem(id, name))
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun fetchVodList(apiUrl: String, typeId: String = "", page: Int = 1): List<VodItem> =
        fetchVodItems(buildApiUrl(apiUrl, page = page, typeId = typeId))

    suspend fun searchVod(apiUrl: String, keyword: String, page: Int = 1): List<VodItem> =
        fetchVodItems(buildApiUrl(apiUrl, page = page, keyword = keyword))

    suspend fun fetchVodDetail(apiUrl: String, vodId: String): VodDetail? = withContext(Dispatchers.IO) {
        try {
            val jsonString = fetchJsonString(buildApiUrl(apiUrl, vodId = vodId))
            if (jsonString.isBlank()) return@withContext null
            val vod = JSONObject(jsonString).optJSONArray("list")?.optJSONObject(0)
                ?: return@withContext null
            val item = parseVodItem(vod)
            VodDetail(
                vodId = item.vodId,
                vodName = item.vodName,
                vodPic = item.vodPic,
                vodRemarks = item.vodRemarks,
                typeName = item.typeName,
                vodYear = vod.optString("vod_year"),
                vodArea = vod.optString("vod_area"),
                vodActor = vod.optString("vod_actor"),
                vodDirector = vod.optString("vod_director"),
                vodContent = vod.optString("vod_content").replace(HTML_TAG_REGEX, "").trim(),
                routes = parsePlayRoutes(vod.optString("vod_play_from"), vod.optString("vod_play_url"))
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun fetchVodItems(url: String): List<VodItem> = withContext(Dispatchers.IO) {
        try {
            val jsonString = fetchJsonString(url)
            if (jsonString.isBlank()) return@withContext emptyList()
            val items = JSONObject(jsonString).optJSONArray("list") ?: return@withContext emptyList()
            buildList {
                for (index in 0 until items.length()) {
                    items.optJSONObject(index)?.let { add(parseVodItem(it)) }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Append escaped API parameters without changing existing query strings or fragments. */
    internal fun buildApiUrl(
        apiUrl: String,
        page: Int? = null,
        typeId: String? = null,
        keyword: String? = null,
        vodId: String? = null
    ): String {
        val parameters = linkedMapOf("ac" to "detail")
        page?.let { parameters["pg"] = it.toString() }
        typeId?.takeIf { it.isNotEmpty() }?.let { parameters["t"] = it }
        keyword?.let { parameters["wd"] = it }
        vodId?.let { parameters["ids"] = it }

        val fragmentIndex = apiUrl.indexOf('#')
        val base = if (fragmentIndex >= 0) apiUrl.substring(0, fragmentIndex) else apiUrl
        val fragment = if (fragmentIndex >= 0) apiUrl.substring(fragmentIndex) else ""
        val separator = when {
            '?' !in base -> "?"
            base.endsWith('?') || base.endsWith('&') -> ""
            else -> "&"
        }
        val encodedParameters = parameters.entries.joinToString("&") { (key, value) ->
            "${encodeQueryComponent(key)}=${encodeQueryComponent(value)}"
        }
        return base + separator + encodedParameters + fragment
    }

    private fun encodeQueryComponent(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun parseVodItem(obj: JSONObject) = VodItem(
        vodId = obj.optString("vod_id"),
        vodName = obj.optString("vod_name"),
        vodPic = obj.optString("vod_pic"),
        vodRemarks = obj.optString("vod_remarks", obj.optString("vod_remarks_custom", "")),
        typeName = obj.optString("type_name", "")
    )

    private fun parsePlayRoutes(playFrom: String, playUrl: String): List<PlayRoute> {
        if (playUrl.isEmpty()) return emptyList()
        val routeNames = playFrom.split("$$$")
        return playUrl.split("$$$").mapIndexedNotNull { index, routeUrl ->
            val name = routeNames.getOrNull(index)?.takeIf { it.isNotBlank() }
                ?.let(::formatRouteName) ?: "线路 ${index + 1}"
            val episodes = routeUrl.split('#').mapNotNull { episode ->
                if (episode.isBlank()) return@mapNotNull null
                val parts = episode.split('$', limit = 2)
                val episodeName = parts.first().trim()
                val url = parts.getOrNull(1)?.trim() ?: episodeName
                if (url.isEmpty()) null else EpisodeItem(
                    name = if (parts.size == 1) "播放" else episodeName,
                    url = url
                )
            }
            if (episodes.isEmpty()) null else PlayRoute(name, episodes)
        }
    }

    private fun formatRouteName(raw: String): String = when {
        raw.contains("w", ignoreCase = true) -> "线路W"
        raw.contains("f", ignoreCase = true) -> "线路F"
        raw.contains("z", ignoreCase = true) -> "线路Z"
        raw.contains("m3u8", ignoreCase = true) -> "极速极清"
        else -> raw.uppercase()
    }

    private const val CONNECT_TIMEOUT_MS = 12_000
    private const val READ_TIMEOUT_MS = 12_000
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36"
    private val HTML_TAG_REGEX = Regex("<[^>]*>")
}
