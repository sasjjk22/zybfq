package com.tjjiangh.bofangqi.sniffer

import java.net.URLDecoder

object SnifferUtils {

    private val VIDEO_EXTENSIONS = listOf(".m3u8", ".mp4", ".flv", ".vtt")

    fun isVideoUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase()

        if (lower.contains(".js") || lower.contains(".css") || lower.contains(".jpg") ||
            lower.contains(".png") || lower.contains(".gif") || lower.contains(".ico")) {
            return false
        }

        if (VIDEO_EXTENSIONS.any { lower.contains(it) }) {
            return true
        }

        if (lower.contains("m3u8") || lower.contains("mp4") || lower.contains("video/stream") || lower.contains("getm3u8")) {
            return true
        }

        return false
    }

    fun normalizeUrl(rawUrl: String, baseUrl: String = ""): String {
        if (rawUrl.isBlank()) return ""
        var url = rawUrl.trim()

        if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("file://")) {
            if (baseUrl.startsWith("http://") || baseUrl.startsWith("https://")) {
                val cleanBase = if (baseUrl.contains("?")) baseUrl.substringBefore("?") else baseUrl
                val baseDir = cleanBase.substringBeforeLast("/") + "/"
                url = if (url.startsWith("/")) {
                    val scheme = if (cleanBase.startsWith("https")) "https://" else "http://"
                    val domain = cleanBase.removePrefix("https://").removePrefix("http://").substringBefore("/")
                    scheme + domain + url
                } else {
                    baseDir + url
                }
            }
        }
        return url
    }

    /**
     * 核心修复：精准剥离 JX 壳，防止将 getM3u8?url=NBY-xxx 误剪切为裸参数
     */
    fun extractRealVideoUrl(rawUrl: String): String {
        if (rawUrl.isBlank()) return ""
        var url = rawUrl.trim()

        try {
            url = URLDecoder.decode(url, "UTF-8")
        } catch (e: Exception) { }

        if (url.contains("url=")) {
            val param = url.substringAfter("url=").substringBefore("&").trim()
            // 只有当 url= 参数本身是完整的 http:// 或 https:// 协议链接时，才进行剥离
            if (param.startsWith("http://") || param.startsWith("https://")) {
                return param
            }
        }

        val m3u8Regex = Regex("(https?://[^\"'\\s<>]+\\.m3u8[^\"'\\s<>]*)", RegexOption.IGNORE_CASE)
        val match = m3u8Regex.find(url)
        if (match != null) {
            val matchedUrl = match.value.trim()
            if (matchedUrl.startsWith("http://") || matchedUrl.startsWith("https://")) {
                return matchedUrl
            }
        }

        return url
    }
}