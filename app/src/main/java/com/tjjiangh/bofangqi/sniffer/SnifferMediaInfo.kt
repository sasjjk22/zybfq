package com.tjjiangh.bofangqi.sniffer

import java.io.Serializable

data class SnifferMediaInfo(
    val url: String = "",
    val videoUrl: String = url,
    val mediaUrl: String = url,
    val title: String = "",
    val headers: Map<String, String> = emptyMap(),
    val referer: String = headers["Referer"] ?: "",
    val origin: String = headers["Origin"] ?: "",
    val userAgent: String = headers["User-Agent"] ?: "",
    val cookie: String = headers["Cookie"] ?: "",
    val pageUrl: String = ""
) : Serializable {
    val showName: String get() = title
}