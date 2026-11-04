package com.tjjiangh.bofangqi.source

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object SourceSpeedTester {

    suspend fun testSpeed(urlStr: String): Long = withContext(Dispatchers.IO) {
        if (urlStr.isBlank()) return@withContext -1L
        var conn: HttpURLConnection? = null
        val startTime = System.currentTimeMillis()
        try {
            val targetUrl = SourceStorage.getWebBrowsingUrl(urlStr)
            val url = URL(targetUrl)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "HEAD"
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36")

            val code = conn.responseCode
            val endTime = System.currentTimeMillis()

            if (code in 200..399) {
                (endTime - startTime)
            } else {
                -1L
            }
        } catch (e: Exception) {
            -1L
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * 智能解析全类型 TvBox 订阅 JSON (同时支持 urls 列表 与 sites 站点列表)
     */
    suspend fun parseTvBoxSubscription(jsonUrl: String): List<SourceModel> = withContext(Dispatchers.IO) {
        val result = mutableListOf<SourceModel>()
        var conn: HttpURLConnection? = null
        try {
            val url = URL(jsonUrl)
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36")

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val jsonStr = reader.readText()
                reader.close()

                val json = JSONObject(jsonStr)

                // 1. 尝试解析 urls 节点 (聚合索引类型)
                val urlsArray = json.optJSONArray("urls")
                if (urlsArray != null && urlsArray.length() > 0) {
                    for (i in 0 until urlsArray.length()) {
                        val obj = urlsArray.getJSONObject(i)
                        val name = obj.optString("name", "站点 ${i + 1}")
                        val siteUrl = obj.optString("url")
                        if (siteUrl.isNotBlank() && siteUrl.startsWith("http")) {
                            result.add(
                                SourceModel(
                                    id = System.currentTimeMillis().toString() + "_u_" + i,
                                    name = name,
                                    url = siteUrl,
                                    apiUrl = siteUrl
                                )
                            )
                        }
                    }
                }

                // 2. 尝试解析 sites 节点 (FongMI / 单源线路类型，如泥巴、星星、玩偶)
                val sitesArray = json.optJSONArray("sites")
                if (sitesArray != null && sitesArray.length() > 0) {
                    for (i in 0 until sitesArray.length()) {
                        val obj = sitesArray.getJSONObject(i)
                        val name = obj.optString("name", "站点 ${i + 1}")
                        val api = obj.optString("api")
                        val ext = obj.optString("ext")

                        var targetUrl = ""
                        if (api.startsWith("http")) {
                            targetUrl = api
                        } else if (ext.startsWith("http")) {
                            targetUrl = ext
                        }

                        if (targetUrl.isNotBlank()) {
                            result.add(
                                SourceModel(
                                    id = System.currentTimeMillis().toString() + "_s_" + i,
                                    name = name,
                                    url = targetUrl,
                                    apiUrl = targetUrl
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            conn?.disconnect()
        }
        result
    }
}