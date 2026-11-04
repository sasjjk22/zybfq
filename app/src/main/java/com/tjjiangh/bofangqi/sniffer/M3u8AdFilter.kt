package com.tjjiangh.bofangqi.sniffer

object M3u8AdFilter {

    // 常见切片级赌博、插播广告特征关键词
    private val AD_KEYWORDS = listOf(
        "adjump", "xinpujing", "158158", "guanggao", "ad.ts", "-ad-",
        "mac_player", "gamble", "777", "bet", "qp", "video_ad",
        "adv", "advert", "tg_", "ty_", "dp_", "ad_slice", "dsp"
    )

    private data class M3u8Block(
        val lines: MutableList<String> = mutableListOf(),
        var duration: Double = 0.0,
        var isAd: Boolean = false,
        var hasDiscontinuity: Boolean = false
    )

    /**
     * 实时清洗 M3U8 文本，剔除片头及 5~6 分钟处的中插切片广告
     */
    fun filterM3u8Content(m3u8Content: String, baseUrl: String): String {
        if (!m3u8Content.contains("#EXTINF")) {
            return m3u8Content
        }

        val lines = m3u8Content.lines()
        val blocks = mutableListOf<M3u8Block>()
        var currentBlock = M3u8Block()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed == "#EXT-X-DISCONTINUITY") {
                if (currentBlock.lines.isNotEmpty()) {
                    blocks.add(currentBlock)
                }
                currentBlock = M3u8Block(hasDiscontinuity = true)
                currentBlock.lines.add(line)
                continue
            }

            currentBlock.lines.add(line)

            if (trimmed.startsWith("#EXTINF:")) {
                val durStr = trimmed.substringAfter("#EXTINF:").substringBefore(",")
                currentBlock.duration += durStr.toDoubleOrNull() ?: 0.0
            } else if (!trimmed.startsWith("#")) {
                val lower = trimmed.lowercase()
                if (AD_KEYWORDS.any { lower.contains(it) }) {
                    currentBlock.isAd = true
                }
            }
        }

        if (currentBlock.lines.isNotEmpty()) {
            blocks.add(currentBlock)
        }

        // 识别并标记广告块：
        // 1. 命中关键词的块
        // 2. 被 DISCONTINUITY 分隔且时长 <= 45 秒的短小插播块（典型的 5 分钟中插广告）
        for (i in blocks.indices) {
            val b = blocks[i]
            if (b.isAd) continue

            val isShortDiscontinuityBlock = b.hasDiscontinuity && b.duration in 2.0..45.0
            // 如果不是全片唯一的正片块，且具备短时插播特征，则判定为广告
            if (isShortDiscontinuityBlock && blocks.size > 1) {
                b.isAd = true
            }
        }

        val cleanLines = ArrayList<String>()
        var keptDuration = 0.0

        for (b in blocks) {
            if (b.isAd) {
                // 剔除广告块
                continue
            }
            keptDuration += b.duration
            for (line in b.lines) {
                val trimmed = line.trim()
                // 过滤掉非广告块内个别命中了广告词的独立切片
                if (!trimmed.startsWith("#") && AD_KEYWORDS.any { trimmed.lowercase().contains(it) }) {
                    if (cleanLines.isNotEmpty() && cleanLines.last().startsWith("#EXTINF")) {
                        cleanLines.removeAt(cleanLines.size - 1)
                    }
                    continue
                }
                cleanLines.add(line)
            }
        }

        // 兜底保护：若误杀导致总时长小于 60 秒，无条件回退原文件
        if (keptDuration < 60.0 && cleanLines.count { !it.trim().startsWith("#") } < 5) {
            return m3u8Content
        }

        val result = StringBuilder()
        var hasExtm3u = false
        for (l in cleanLines) {
            if (l.trim() == "#EXTM3U") {
                hasExtm3u = true
            }
            result.append(l).append("\n")
        }

        return if (!hasExtm3u) "#EXTM3U\n$result" else result.toString()
    }
}