package com.tjjiangh.bofangqi

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.tjjiangh.bofangqi.download.DownloadItem
import com.tjjiangh.bofangqi.download.DownloadStorage
import com.tjjiangh.bofangqi.download.VideoDownloadManager
import java.io.File

class DownloadShowDetailActivity : AppCompatActivity() {

    private lateinit var btnBack: ImageButton
    private lateinit var tvShowTitle: TextView
    private lateinit var btnDeleteShowAll: Button
    private lateinit var layoutEpisodesContainer: LinearLayout

    private var showName: String = ""
    private var cleanShowName: String = ""

    private val mainHandler = Handler(Looper.getMainLooper())

    // 正在下载时的 2 秒实时自动刷新 UI 轮询器
    private val autoRefreshRunnable = object : Runnable {
        override fun run() {
            refreshEpisodeList()
            val hasDownloading = DownloadStorage.getTasks(this@DownloadShowDetailActivity)
                .any { DownloadStorage.extractShowName(it.showName) == cleanShowName && it.status == DownloadItem.STATUS_DOWNLOADING }
            if (hasDownloading) {
                mainHandler.postDelayed(this, 2000)
            }
        }
    }

    companion object {
        private const val EXTRA_SHOW_NAME = "extra_show_name"

        fun start(context: Context, showName: String) {
            val intent = Intent(context, DownloadShowDetailActivity::class.java).apply {
                putExtra(EXTRA_SHOW_NAME, showName)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_download_show_detail)

        val rawShow = intent.getStringExtra(EXTRA_SHOW_NAME) ?: "影视缓存"
        cleanShowName = DownloadStorage.extractShowName(rawShow).ifEmpty { rawShow }
        showName = cleanShowName

        btnBack = findViewById(R.id.btn_back)
        tvShowTitle = findViewById(R.id.tv_show_title)
        btnDeleteShowAll = findViewById(R.id.btn_delete_show_all)
        layoutEpisodesContainer = findViewById(R.id.layout_episodes_container)

        tvShowTitle.text = "《$cleanShowName》"
        btnBack.setOnClickListener { finish() }

        btnDeleteShowAll.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("清空全剧缓存")
                .setMessage("确定要彻底物理删除《$cleanShowName》的所有离线切片文件吗？")
                .setPositiveButton("彻底物理删除") { _, _ ->
                    DownloadStorage.deleteShowGroup(this, cleanShowName)
                    Toast.makeText(this, "已彻底删除全剧缓存", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .setNegativeButton("取消", null)
                .show()
        }

        refreshEpisodeList()
    }

    override fun onResume() {
        super.onResume()
        mainHandler.removeCallbacks(autoRefreshRunnable)
        mainHandler.post(autoRefreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        mainHandler.removeCallbacks(autoRefreshRunnable)
    }

    /**
     * 当用户轻触某条下载记录时调用：打通诊断弹窗与本地播放
     */
    private fun onDownloadItemClick(item: DownloadItem) {
        when (item.status) {
            DownloadItem.STATUS_FAILED -> {
                val errorMsg = if (item.errorLog.isNotBlank()) item.errorLog else "未知下载网络异常或防盗链 403 拦截"
                val logDetails = """
                    ❌ 【离线缓存失败诊断报告】
                    
                    1. 剧集名称: 《${item.showName}》 ${item.epName}
                    2. 目标 URL: ${item.videoUrl}
                    3. 详细失败原因:
                    $errorMsg
                """.trimIndent()

                AlertDialog.Builder(this)
                    .setTitle("缓存失败诊断")
                    .setMessage(logDetails)
                    .setPositiveButton("一键复制日志") { _, _ ->
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("DownloadErrorLog", logDetails)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(this, "日志已复制到剪贴板！", Toast.LENGTH_SHORT).show()
                    }
                    .setNeutralButton("重新尝试下载") { _, _ ->
                        VideoDownloadManager.startDownload(applicationContext, item)
                        Toast.makeText(this, "已重新发起缓存...", Toast.LENGTH_SHORT).show()
                        refreshEpisodeList()
                    }
                    .setNegativeButton("关闭", null)
                    .show()
            }
            DownloadItem.STATUS_COMPLETED -> {
                if (item.localPath.isNotEmpty()) {
                    PlayerActivity.start(
                        context = this,
                        videoUrl = item.localPath,
                        showName = item.showName,
                        epName = item.epName,
                        routeName = "离线缓存"
                    )
                } else {
                    Toast.makeText(this, "本地切片路径无效，请尝试重新下载", Toast.LENGTH_SHORT).show()
                }
            }
            else -> {
                Toast.makeText(this, "${item.epName} 正在后台切片下载中 (${item.progress}%)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshEpisodeList() {
        layoutEpisodesContainer.removeAllViews()

        val allTasks = DownloadStorage.getTasks(this)
        val epTasks = allTasks.filter { DownloadStorage.extractShowName(it.showName) == cleanShowName }

        if (epTasks.isEmpty()) {
            Toast.makeText(this, "该剧集缓存已被清理", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        var totalShowBytes = 0L
        epTasks.forEach { task ->
            if (task.localPath.isNotEmpty()) {
                val file = File(task.localPath)
                if (file.exists()) {
                    totalShowBytes += if (file.isDirectory) file.walkTopDown().sumOf { f -> f.length() } else file.length()
                }
            } else if (task.fileSize > 0) {
                totalShowBytes += task.fileSize
            }
        }

        // 头部详情卡片 Banner
        val headerTv = TextView(this).apply {
            text = "🎬 《$cleanShowName》\n已缓存: ${epTasks.size} 集 | 空间占用: ${DownloadStorage.formatFileSize(totalShowBytes)}"
            textSize = 14f
            setPadding(0, 0, 0, 24)
            setTextColor(resources.getColor(android.R.color.holo_blue_dark, null))
        }
        layoutEpisodesContainer.addView(headerTv)

        for (task in epTasks) {
            val epView = LayoutInflater.from(this).inflate(
                android.R.layout.simple_list_item_2,
                layoutEpisodesContainer,
                false
            )

            val text1 = epView.findViewById<TextView>(android.R.id.text1)
            val text2 = epView.findViewById<TextView>(android.R.id.text2)

            text1.text = task.epName
            text1.textSize = 16f
            text1.paint.isFakeBoldText = true

            var epBytes = task.fileSize
            if (epBytes <= 0L && task.localPath.isNotEmpty()) {
                val file = File(task.localPath)
                if (file.exists()) {
                    epBytes = if (file.isDirectory) file.walkTopDown().sumOf { f -> f.length() } else file.length()
                }
            }

            val statusStr = when (task.status) {
                DownloadItem.STATUS_COMPLETED -> "✅ 缓存完成: ${DownloadStorage.formatFileSize(epBytes)} (点击播放/长按删除)"
                DownloadItem.STATUS_FAILED -> "❌ 缓存失败 (点击诊断日志/长按删除)"
                else -> "⏳ 正在缓存 ${task.progress}% (${DownloadStorage.formatFileSize(epBytes)})"
            }

            text2.text = statusStr
            text2.textSize = 13f

            // 核心修复：点击调起 onDownloadItemClick 诊断/播放逻辑
            epView.setOnClickListener {
                onDownloadItemClick(task)
            }

            epView.setOnLongClickListener {
                AlertDialog.Builder(this)
                    .setTitle("物理删除该集")
                    .setMessage("确定要物理粉碎《$cleanShowName》${task.epName} 的视频切片文件吗？")
                    .setPositiveButton("删除文件") { _, _ ->
                        DownloadStorage.deleteTask(this, task.id)
                        refreshEpisodeList()
                        Toast.makeText(this, "文件已粉碎删除", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("取消", null)
                    .show()
                true
            }

            layoutEpisodesContainer.addView(epView)
        }
    }
}