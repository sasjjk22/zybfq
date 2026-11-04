package com.tjjiangh.bofangqi.download

import android.content.Context
import com.tjjiangh.bofangqi.sniffer.M3u8AdFilter
import com.tjjiangh.bofangqi.sniffer.SnifferUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

object VideoDownloadManager {

    private val downloadScope = CoroutineScope(Dispatchers.IO)
    private const val MAX_CONCURRENT_TS_DOWNLOADS = 8

    private data class TsTask(
        val index: Int,
        val url: String,
        val localFile: File
    )

    fun startDownload(context: Context, item: DownloadItem) {
        val finalHeaders = item.headers.toMutableMap()
        if (!finalHeaders.containsKey("Referer")) {
            finalHeaders["Referer"] = "https://jx.xllmcms.com/"
            finalHeaders["Origin"] = "https://www.ruiding3bu.com"
        }

        val updatedItem = item.copy(headers = finalHeaders, status = DownloadItem.STATUS_DOWNLOADING, progress = 0, errorLog = "")
        DownloadStorage.saveOrUpdateTask(context, updatedItem)

        downloadScope.launch {
            try {
                var realM3u8Url = updatedItem.videoUrl

                if (realM3u8Url.startsWith("http") && !realM3u8Url.contains(".m3u8")) {
                    val pageHtml = fetchText(realM3u8Url, updatedItem.headers)
                    val extracted = extractM3u8FromHtml(pageHtml)
                    if (extracted.isNotEmpty()) {
                        realM3u8Url = extracted
                    } else {
                        realM3u8Url = SnifferUtils.extractRealVideoUrl(realM3u8Url)
                    }
                }

                if (!realM3u8Url.contains(".m3u8") && !realM3u8Url.contains("m3u8")) {
                    val failLog = "下载失败: 无法从网页 [${updatedItem.videoUrl}] 中解包出真实的 m3u8 视频直链"
                    DownloadStorage.saveOrUpdateTask(context, updatedItem.copy(status = DownloadItem.STATUS_FAILED, errorLog = failLog))
                    return@launch
                }

                val safeShow = DownloadStorage.cleanTitle(updatedItem.showName)
                val safeEp = updatedItem.epName
                val baseDir = File(context.getExternalFilesDir(null), "offline_cache/$safeShow/$safeEp")
                if (!baseDir.exists()) baseDir.mkdirs()

                var m3u8Content = fetchText(realM3u8Url, updatedItem.headers)
                if (m3u8Content.isBlank()) {
                    val failLog = "下载失败: 请求 m3u8 文本返回空 (URL: $realM3u8Url)"
                    DownloadStorage.saveOrUpdateTask(context, updatedItem.copy(status = DownloadItem.STATUS_FAILED, errorLog = failLog))
                    return@launch
                }

                // Master 双层 m3u8 穿透处理
                if (m3u8Content.contains("#EXT-X-STREAM-INF") || (m3u8Content.contains(".m3u8") && !m3u8Content.contains("#EXTINF"))) {
                    val lines = m3u8Content.split("\n")
                    for (line in lines) {
                        val trimmed = line.trim()
                        if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                            val subUrl = if (trimmed.startsWith("http")) trimmed else realM3u8Url.substringBeforeLast("/") + "/" + trimmed
                            val subContent = fetchText(subUrl, updatedItem.headers)
                            if (subContent.contains("#EXTINF")) {
                                m3u8Content = subContent
                                realM3u8Url = subUrl
                                break
                            }
                        }
                    }
                }

                // 核心净化：剥离片头与 5 分钟处的中插赌博广告
                m3u8Content = M3u8AdFilter.filterM3u8Content(m3u8Content, realM3u8Url)

                val localM3u8Lines = mutableListOf<String>()
                val tsTaskList = mutableListOf<TsTask>()
                val baseUrl = realM3u8Url.substringBeforeLast("/") + "/"

                val lines = m3u8Content.split("\n")
                var tsIndex = 0

                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("#")) {
                        localM3u8Lines.add(trimmed)
                    } else if (trimmed.isNotBlank()) {
                        val tsUrl = if (trimmed.startsWith("http")) trimmed else baseUrl + trimmed
                        val localFileName = String.format("slice_%04d.ts", tsIndex)
                        val localFile = File(baseDir, localFileName)

                        localM3u8Lines.add(localFileName)
                        tsTaskList.add(TsTask(tsIndex, tsUrl, localFile))
                        tsIndex++
                    }
                }

                if (tsTaskList.isEmpty()) {
                    DownloadStorage.saveOrUpdateTask(context, updatedItem.copy(status = DownloadItem.STATUS_FAILED, errorLog = "解析到 0 个切片文件"))
                    return@launch
                }

                val semaphore = Semaphore(MAX_CONCURRENT_TS_DOWNLOADS)
                val completedCount = AtomicInteger(0)
                val totalDownloadedBytes = AtomicLong(0L)
                var lastProgressWriteTime = 0L

                val jobs = tsTaskList.map { tsTask ->
                    launch {
                        semaphore.withPermit {
                            val downloadedBytes = downloadFile(tsTask.url, tsTask.localFile, updatedItem.headers)
                            totalDownloadedBytes.addAndGet(downloadedBytes)
                            val finished = completedCount.incrementAndGet()

                            val currentProgress = ((finished.toDouble() / tsTaskList.size) * 100).toInt().coerceIn(1, 99)
                            val now = System.currentTimeMillis()

                            if (now - lastProgressWriteTime > 500 || finished == tsTaskList.size) {
                                lastProgressWriteTime = now
                                DownloadStorage.saveOrUpdateTask(
                                    context,
                                    updatedItem.copy(
                                        progress = currentProgress,
                                        fileSize = totalDownloadedBytes.get(),
                                        status = DownloadItem.STATUS_DOWNLOADING
                                    )
                                )
                            }
                        }
                    }
                }

                jobs.forEach { it.join() }

                val localIndexFile = File(baseDir, "index.m3u8")
                FileOutputStream(localIndexFile).use { out ->
                    out.write(localM3u8Lines.joinToString("\n").toByteArray())
                }

                val finalItem = updatedItem.copy(
                    status = DownloadItem.STATUS_COMPLETED,
                    progress = 100,
                    fileSize = totalDownloadedBytes.get(),
                    localPath = localIndexFile.absolutePath,
                    errorLog = "下载成功"
                )
                DownloadStorage.saveOrUpdateTask(context, finalItem)

            } catch (e: Exception) {
                e.printStackTrace()
                val failLog = "下载异常: ${e.localizedMessage ?: e.message}"
                DownloadStorage.saveOrUpdateTask(context, updatedItem.copy(status = DownloadItem.STATUS_FAILED, errorLog = failLog))
            }
        }
    }

    private fun extractM3u8FromHtml(rawHtml: String): String {
        if (rawHtml.isBlank()) return ""

        var processed = rawHtml.replace("\\/", "/")
        try {
            processed = URLDecoder.decode(processed, "UTF-8")
        } catch (_: Exception) { }

        val m3u8Regex = Regex("(https?://[^\"'\\s<>]+\\.m3u8[^\"'\\s<>]*)", RegexOption.IGNORE_CASE)
        val match = m3u8Regex.find(processed)
        if (match != null) {
            return SnifferUtils.extractRealVideoUrl(match.value.trim())
        }

        try {
            if (processed.contains("player_aaaa") || processed.contains("mac_player")) {
                val jsonRegex = Regex("(?:player_aaaa|mac_player)\\s*=\\s*(\\{[^\\}]+\\})")
                val jsonMatch = jsonRegex.find(processed)
                if (jsonMatch != null) {
                    val jsonStr = jsonMatch.groupValues[1]
                    val jsonObj = JSONObject(jsonStr)
                    val urlInJson = jsonObj.optString("url")
                    if (urlInJson.isNotEmpty()) {
                        return SnifferUtils.extractRealVideoUrl(urlInJson.replace("\\/", "/"))
                    }
                }
            }
        } catch (_: Exception) { }

        try {
            val iframeRegex = Regex("<iframe[^>]+src=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
            val iframeMatch = iframeRegex.find(processed)
            if (iframeMatch != null) {
                val iframeSrc = iframeMatch.groupValues[1]
                val realFromIframe = SnifferUtils.extractRealVideoUrl(iframeSrc)
                if (realFromIframe.contains("m3u8")) {
                    return realFromIframe
                }
            }
        } catch (_: Exception) { }

        return ""
    }

    fun startDownload(context: Context, videoUrl: String, showName: String, epName: String) {
        val cleanShow = DownloadStorage.extractShowName(showName).ifEmpty { showName }
        val item = DownloadItem(
            id = "${cleanShow}_${epName}".hashCode().toString(),
            showName = cleanShow,
            epName = epName,
            routeName = "默认线路",
            title = "$cleanShow $epName",
            videoUrl = videoUrl
        )
        startDownload(context, item)
    }

    private fun fetchText(urlStr: String, headers: Map<String, String>): String {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 12000
            conn.readTimeout = 12000
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (!headers.containsKey("User-Agent")) {
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36")
            }
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else ""
        } catch (e: Exception) {
            ""
        } finally {
            conn?.disconnect()
        }
    }

    private fun downloadFile(urlStr: String, destFile: File, headers: Map<String, String>): Long {
        var conn: HttpURLConnection? = null
        var bytesCount = 0L
        try {
            val url = URL(urlStr)
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 12000
            conn.readTimeout = 12000
            conn.setRequestProperty("Connection", "keep-alive")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (!headers.containsKey("User-Agent")) {
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36")
            }
            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                conn.inputStream.use { input ->
                    FileOutputStream(destFile).use { output ->
                        val buffer = ByteArray(65536)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            bytesCount += bytesRead
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            conn?.disconnect()
        }
        return bytesCount
    }
}