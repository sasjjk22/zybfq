package com.tjjiangh.bofangqi.download

import java.io.Serializable

data class DownloadItem(
    val id: String = System.currentTimeMillis().toString(),
    val showName: String = "",
    val epName: String = "",
    val routeName: String = "默认线路",
    val title: String = "",
    val videoUrl: String = "",
    val localPath: String = "",
    val progress: Int = 0,
    val status: Int = STATUS_DOWNLOADING, // 0: 下载中, 1: 已完成, -1: 失败
    val headers: Map<String, String> = emptyMap(),
    val fileSize: Long = 0L,
    val sourceName: String = "第三方资源",
    val posterUrl: String = "",
    val errorLog: String = "" // 新增：离线缓存诊断日志
) : Serializable {

    companion object {
        const val STATUS_DOWNLOADING = 0
        const val STATUS_COMPLETED = 1
        const val STATUS_FAILED = -1
    }

    val isCompleted: Boolean get() = status == STATUS_COMPLETED
    val localM3u8Path: String get() = localPath

    fun getFormattedSize(): String {
        return DownloadStorage.formatFileSize(fileSize)
    }
}