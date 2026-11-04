package com.tjjiangh.bofangqi.source

import java.io.Serializable

data class SourceModel(
    val id: String = System.currentTimeMillis().toString(),
    val name: String = "",
    val url: String = "",
    val apiUrl: String = url,
    val groupName: String = "自定义", // 分组名称，如 "TvBox订阅" 或 "预置"
    val type: Int = TYPE_API,
    var speedMs: Long = -1L,
    var isSelected: Boolean = false,
    var isBatchChecked: Boolean = false // 批量管理选中状态
) : Serializable {

    companion object {
        const val TYPE_API = 0
        const val TYPE_WEB = 1
    }

    val isWebApi: Boolean get() = type == TYPE_WEB || (!url.contains("api.php") && !url.contains(".json") && url.startsWith("http"))
}