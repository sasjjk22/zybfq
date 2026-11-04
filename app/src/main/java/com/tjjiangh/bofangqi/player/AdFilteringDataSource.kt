package com.tjjiangh.bofangqi.player

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.tjjiangh.bofangqi.sniffer.M3u8AdFilter
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * 自动净化 M3U8 片头与中插切片广告的流媒体拦截数据源
 */
class AdFilteringDataSource(private val upstream: DataSource) : DataSource by upstream {

    private var interceptedStream: InputStream? = null
    private var currentUri: Uri? = null

    override fun getUri(): Uri? {
        return currentUri ?: upstream.uri
    }

    override fun open(dataSpec: DataSpec): Long {
        currentUri = dataSpec.uri
        val uriStr = dataSpec.uri.toString()
        val length = upstream.open(dataSpec)

        val isM3u8 = uriStr.contains(".m3u8", ignoreCase = true) ||
                uriStr.contains("mime=m3u8", ignoreCase = true) ||
                dataSpec.uri.path?.endsWith(".m3u8", ignoreCase = true) == true

        if (isM3u8 && !uriStr.startsWith("file://")) {
            val maxRead = if (length in 1..4194304) length.toInt() else 2097152
            val buffer = ByteArray(maxRead)
            var totalRead = 0
            var bytesRead: Int
            while (totalRead < buffer.size) {
                bytesRead = upstream.read(buffer, totalRead, buffer.size - totalRead)
                if (bytesRead <= 0) break
                totalRead += bytesRead
            }

            if (totalRead > 0) {
                val originalContent = String(buffer, 0, totalRead, StandardCharsets.UTF_8)
                val cleanContent = M3u8AdFilter.filterM3u8Content(originalContent, uriStr)
                val cleanBytes = cleanContent.toByteArray(StandardCharsets.UTF_8)

                interceptedStream = ByteArrayInputStream(cleanBytes)
                return cleanBytes.size.toLong()
            }
        }

        return length
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return interceptedStream?.read(buffer, offset, length) ?: upstream.read(buffer, offset, length)
    }

    override fun close() {
        interceptedStream?.close()
        interceptedStream = null
        upstream.close()
    }
}