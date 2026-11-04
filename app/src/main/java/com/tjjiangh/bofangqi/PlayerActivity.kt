package com.tjjiangh.bofangqi

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.tjjiangh.bofangqi.download.DownloadItem
import com.tjjiangh.bofangqi.download.DownloadStorage
import com.tjjiangh.bofangqi.download.VideoDownloadManager
import com.tjjiangh.bofangqi.history.HistoryItem
import com.tjjiangh.bofangqi.history.HistoryStorage
import com.tjjiangh.bofangqi.player.AdFilteringDataSource
import com.tjjiangh.bofangqi.sniffer.AdBlockEngine
import com.tjjiangh.bofangqi.sniffer.SnifferUtils
import com.tjjiangh.bofangqi.subscription.EpisodeItem
import com.tjjiangh.bofangqi.subscription.PlayRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class PlayerActivity : AppCompatActivity() {

    private lateinit var playerContainer: FrameLayout
    private lateinit var playerView: PlayerView
    private lateinit var btnBack: ImageButton
    private lateinit var btnFullscreen: ImageButton
    private lateinit var layoutDetails: ScrollView
    private lateinit var tvVideoTitle: TextView
    private lateinit var btnBatchDownload: Button
    private lateinit var chipGroupRoutes: ChipGroup
    private lateinit var chipGroupEpisodes: ChipGroup

    private var exoPlayer: ExoPlayer? = null
    private var hiddenSnifferWebView: WebView? = null

    private var videoUrl = ""
    private var showName = ""
    private var epName = ""
    private var routeName = ""
    private var posterUrl = ""
    private var routesDataRaw = ""

    private var refererHeader = ""
    private var originHeader = ""
    private var cookieHeader = ""
    private var customHeaders: Map<String, String>? = null

    private var playRoutes: MutableList<PlayRoute> = mutableListOf()
    private var currentRouteIndex = 0

    companion object {
        @JvmStatic
        @JvmOverloads
        fun start(
            context: Context,
            videoUrl: String? = null,
            showName: String? = null,
            title: String? = null,
            epName: String? = null,
            routeName: String? = null,
            sourceName: String? = null,
            posterUrl: String? = null,
            referer: String? = null,
            origin: String? = null,
            cookie: String? = null,
            headers: Map<String, String>? = null,
            routesData: String? = null,
            url: String? = null
        ) {
            val finalUrl = (url ?: videoUrl ?: "").trim()
            val rawShow = (title ?: showName ?: "").trim()
            val cleanShow = DownloadStorage.extractShowName(rawShow).ifEmpty { rawShow }
            val finalRoute = (sourceName ?: routeName ?: "默认线路").trim()

            val intent = Intent(context, PlayerActivity::class.java).apply {
                putExtra("VIDEO_URL", finalUrl)
                putExtra("url", finalUrl)
                putExtra("SHOW_NAME", cleanShow)
                putExtra("title", cleanShow)
                putExtra("EPISODE_NAME", epName ?: "")
                putExtra("ROUTE_NAME", finalRoute)
                putExtra("sourceName", finalRoute)
                putExtra("POSTER_URL", posterUrl ?: "")
                putExtra("REFERER", referer ?: "")
                putExtra("ORIGIN", origin ?: "")
                putExtra("COOKIE", cookie ?: "")
                putExtra("ROUTES_DATA", routesData ?: "")
                putExtra("routesData", routesData ?: "")
                headers?.let { putExtra("HEADERS", HashMap(it)) }
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        }

        @JvmStatic
        fun start(context: Context, item: HistoryItem) {
            start(
                context = context,
                videoUrl = item.videoUrl,
                showName = item.cleanShowName,
                epName = item.epName,
                routeName = item.routeName.ifEmpty { item.sourceName },
                posterUrl = item.posterUrl,
                headers = item.headers,
                routesData = item.routesData
            )
        }

        @JvmStatic
        fun start(context: Context, mediaInfo: Any) {
            try {
                var urlVal = ""
                var titleVal = ""
                var headersVal: Map<String, String>? = null

                val cls = mediaInfo.javaClass
                for (m in cls.methods) {
                    if (m.name == "getVideoUrl" || m.name == "getMediaUrl" || m.name == "getUrl") {
                        urlVal = (m.invoke(mediaInfo) as? String) ?: ""
                    }
                    if (m.name == "getTitle" || m.name == "getShowName") {
                        titleVal = (m.invoke(mediaInfo) as? String) ?: ""
                    }
                    if (m.name == "getHeaders") {
                        @Suppress("UNCHECKED_CAST")
                        headersVal = m.invoke(mediaInfo) as? Map<String, String>
                    }
                }

                start(
                    context = context,
                    videoUrl = urlVal,
                    showName = titleVal,
                    headers = headersVal
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        parseIntentData()
        initViews()
        setupListeners()

        if (videoUrl.isNotEmpty()) {
            setupExoPlayer()
        } else {
            tvVideoTitle.text = "暂无视频地址"
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseIntentData() {
        videoUrl = intent.getStringExtra("VIDEO_URL") ?: intent.getStringExtra("url") ?: ""
        val rawShow = intent.getStringExtra("SHOW_NAME") ?: intent.getStringExtra("title") ?: ""
        showName = DownloadStorage.extractShowName(rawShow).ifEmpty { rawShow }
        epName = intent.getStringExtra("EPISODE_NAME") ?: ""
        routeName = intent.getStringExtra("ROUTE_NAME") ?: intent.getStringExtra("sourceName") ?: "默认线路"
        posterUrl = intent.getStringExtra("POSTER_URL") ?: ""

        refererHeader = intent.getStringExtra("REFERER") ?: ""
        originHeader = intent.getStringExtra("ORIGIN") ?: ""
        cookieHeader = intent.getStringExtra("COOKIE") ?: ""
        customHeaders = intent.getSerializableExtra("HEADERS") as? Map<String, String>

        if (!videoUrl.startsWith("http://") && !videoUrl.startsWith("https://") && !videoUrl.startsWith("file://")) {
            val base = refererHeader.ifEmpty { originHeader }
            videoUrl = SnifferUtils.normalizeUrl(videoUrl, base)
        }

        (intent.getSerializableExtra("PLAY_ROUTES") as? List<PlayRoute>)?.let {
            playRoutes.clear()
            playRoutes.addAll(it)
        }

        routesDataRaw = intent.getStringExtra("ROUTES_DATA") ?: intent.getStringExtra("routesData") ?: ""

        if (routesDataRaw.isBlank() && showName.isNotEmpty()) {
            routesDataRaw = HistoryStorage.getSavedRoutesData(this, showName)
        }

        if (routesDataRaw.isNotBlank() && playRoutes.isEmpty()) {
            parseRawRoutesData(routesDataRaw)
        }

        val offlineTasks = DownloadStorage.getTasks(this).filter {
            DownloadStorage.extractShowName(it.showName) == DownloadStorage.extractShowName(showName) && it.status == DownloadItem.STATUS_COMPLETED
        }
        if (offlineTasks.isNotEmpty()) {
            val offlineEps = offlineTasks.map { task ->
                EpisodeItem(name = task.epName, url = task.localPath)
            }
            val offlineRoute = PlayRoute(routeName = "离线缓存", episodes = offlineEps)
            playRoutes.removeAll { it.routeName == "离线缓存" }
            playRoutes.add(0, offlineRoute)
            currentRouteIndex = 0
            routeName = "离线缓存"
        }

        if (playRoutes.isEmpty() && videoUrl.isNotEmpty()) {
            val defaultEpName = if (epName.isNotEmpty()) epName else extractEpNameFromTitle(showName)
            val defaultRouteName = if (routeName.isNotEmpty()) routeName else "默认线路"
            playRoutes.add(
                PlayRoute(
                    routeName = defaultRouteName,
                    episodes = listOf(EpisodeItem(name = defaultEpName, url = videoUrl))
                )
            )
            if (epName.isEmpty()) epName = defaultEpName
        }
    }

    private fun extractEpNameFromTitle(title: String): String {
        val regex = Regex("(第[0-9一二三四五六七八九十]+[集话]|HD|TC|TC中字|HD中字|BD|1080P|4K)")
        val match = regex.find(title)
        return match?.value ?: "正片"
    }

    private fun parseRawRoutesData(rawData: String) {
        val str = rawData.trim()
        try {
            if (str.startsWith("[") || str.startsWith("{")) {
                val jsonArray = if (str.startsWith("[")) JSONArray(str) else JSONObject(str).optJSONArray("routes") ?: JSONArray()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val rName = obj.optString("routeName", obj.optString("name", "线路 ${i + 1}"))
                    val epsArray = obj.optJSONArray("episodes") ?: obj.optJSONArray("list") ?: JSONArray()
                    val episodes = mutableListOf<EpisodeItem>()

                    for (j in 0 until epsArray.length()) {
                        val epObj = epsArray.getJSONObject(j)
                        val eName = epObj.optString("name", epObj.optString("title", "第${j + 1}集"))
                        val eUrl = epObj.optString("url", epObj.optString("link", ""))
                        if (eUrl.isNotEmpty()) {
                            episodes.add(EpisodeItem(eName, eUrl))
                        }
                    }
                    if (episodes.isNotEmpty()) {
                        playRoutes.add(PlayRoute(rName, episodes))
                    }
                }
            } else if (str.contains("$$$") || str.contains("|||") || str.contains("###")) {
                val routeBlocks = str.split("&&&")
                for (block in routeBlocks) {
                    if (block.isBlank()) continue
                    val parts = block.split("$$$")
                    val rName = parts[0].trim()
                    val rawEps = parts.getOrNull(1)?.trim() ?: ""

                    val episodes = mutableListOf<EpisodeItem>()
                    if (rawEps.isNotEmpty()) {
                        val epItems = rawEps.split("###")
                        for (item in epItems) {
                            if (item.isBlank()) continue
                            if (item.contains("|||")) {
                                val p = item.split("|||")
                                val eName = p[0].trim()
                                val eUrl = p.getOrNull(1)?.trim() ?: ""
                                if (eUrl.isNotEmpty()) {
                                    episodes.add(EpisodeItem(eName, eUrl))
                                }
                            }
                        }
                    }
                    if (episodes.isNotEmpty()) {
                        playRoutes.add(PlayRoute(rName, episodes))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun initViews() {
        playerContainer = findViewById(R.id.player_container)
        playerView = findViewById(R.id.player_view)
        btnBack = findViewById(R.id.btn_back)
        btnFullscreen = findViewById(R.id.btn_fullscreen)
        layoutDetails = findViewById(R.id.layout_details)
        tvVideoTitle = findViewById(R.id.tv_video_title)
        btnBatchDownload = findViewById(R.id.btn_batch_download)
        chipGroupRoutes = findViewById(R.id.chip_group_routes)
        chipGroupEpisodes = findViewById(R.id.chip_group_episodes)

        updateTitleText()
        renderRoutesAndEpisodesUi()
    }

    private fun updateTitleText() {
        val cleanShow = DownloadStorage.extractShowName(showName)
        tvVideoTitle.text = when {
            cleanShow.isNotEmpty() && epName.isNotEmpty() -> "《$cleanShow》 $epName ($routeName)"
            cleanShow.isNotEmpty() -> "《$cleanShow》 ($routeName)"
            else -> "视频播放"
        }
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }

        btnFullscreen.setOnClickListener {
            toggleFullScreen()
        }

        playerView.setOnClickListener {
            val nextVis = if (btnBack.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            btnBack.visibility = nextVis
            btnFullscreen.visibility = nextVis
        }

        btnBatchDownload.setOnClickListener {
            showBatchDownloadDialog()
        }
    }

    private fun renderRoutesAndEpisodesUi() {
        if (playRoutes.isEmpty()) return

        chipGroupRoutes.removeAllViews()
        for ((index, route) in playRoutes.withIndex()) {
            val chip = Chip(this).apply {
                text = route.routeName
                isCheckable = true
                isChecked = (index == currentRouteIndex)
                setOnClickListener {
                    currentRouteIndex = index
                    routeName = route.routeName
                    renderEpisodeChips(route.episodes)
                }
            }
            chipGroupRoutes.addView(chip)
        }

        if (currentRouteIndex < playRoutes.size) {
            renderEpisodeChips(playRoutes[currentRouteIndex].episodes)
        }
    }

    private fun renderEpisodeChips(episodes: List<EpisodeItem>) {
        chipGroupEpisodes.removeAllViews()
        for (ep in episodes) {
            val chip = Chip(this).apply {
                text = ep.name
                isCheckable = true
                isChecked = (ep.name == epName || ep.url == videoUrl)
                setOnClickListener {
                    epName = ep.name
                    updateTitleText()

                    if (ep.url.startsWith("http") && !ep.url.contains(".m3u8")) {
                        sniffAndPlayEpisode(ep.url)
                    } else {
                        videoUrl = ep.url
                        setupExoPlayer()
                    }
                }
            }
            chipGroupEpisodes.addView(chip)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun sniffAndPlayEpisode(webUrl: String) {
        Toast.makeText(this, "正在为您切换播放 $epName ...", Toast.LENGTH_SHORT).show()

        if (hiddenSnifferWebView == null) {
            hiddenSnifferWebView = WebView(applicationContext).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = "Mozilla/5.0 (Linux; Android 12; Mobile) AppleWebKit/537.36"
            }
        }

        hiddenSnifferWebView?.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // 页面加载完成后，提取干净的选集数据
                view?.evaluateJavascript(AdBlockEngine.extractPageRoutesJs()) { jsonResult ->
                    if (!jsonResult.isNullOrBlank() && jsonResult != "null" && jsonResult != "[]") {
                        try {
                            val cleanJson = if (jsonResult.startsWith("\"") && jsonResult.endsWith("\"")) {
                                JSONObject("\"data\":$jsonResult").getString("data")
                            } else {
                                jsonResult
                            }
                            routesDataRaw = cleanJson
                            playRoutes.clear()
                            parseRawRoutesData(cleanJson)
                            renderRoutesAndEpisodesUi()
                        } catch (_: Exception) {}
                    }
                }
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                request?.url?.toString()?.let { url ->
                    if (SnifferUtils.isVideoUrl(url)) {
                        val realUrl = SnifferUtils.extractRealVideoUrl(url)
                        runOnUiThread {
                            videoUrl = realUrl
                            setupExoPlayer()
                        }
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }
        }
        hiddenSnifferWebView?.loadUrl(webUrl)
    }

    private suspend fun resolveApiM3u8Url(apiUrl: String, headers: Map<String, String>): String = withContext(Dispatchers.IO) {
        var currentUrl = apiUrl
        var redirectCount = 0

        while (redirectCount < 5) {
            var conn: HttpURLConnection? = null
            try {
                val url = URL(currentUrl)
                conn = url.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
                conn.setRequestProperty("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 12; Mobile)")

                val code = conn.responseCode
                if (code == HttpURLConnection.HTTP_MOVED_TEMP || code == HttpURLConnection.HTTP_MOVED_PERM || code == 307 || code == 308) {
                    val location = conn.getHeaderField("Location")
                    if (!location.isNullOrBlank()) {
                        currentUrl = SnifferUtils.normalizeUrl(location, currentUrl)
                        redirectCount++
                        continue
                    }
                }

                if (code == HttpURLConnection.HTTP_OK) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }.trim()
                    if (text.startsWith("{")) {
                        val json = JSONObject(text)
                        val realUrl = json.optString("url", json.optString("data", json.optString("m3u8", "")))
                        if (realUrl.isNotBlank()) {
                            val extracted = SnifferUtils.extractRealVideoUrl(realUrl)
                            if (extracted != currentUrl && extracted.startsWith("http")) {
                                currentUrl = extracted
                                redirectCount++
                                continue
                            }
                            return@withContext extracted
                        }
                    } else if (text.contains("#EXTM3U") || currentUrl.contains(".m3u8")) {
                        return@withContext currentUrl
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                conn?.disconnect()
            }
            break
        }
        currentUrl
    }

    private fun sanitizeOrigin(origin: String): String {
        if (origin.isBlank()) return ""
        return try {
            val url = URL(origin)
            val portStr = if (url.port != -1 && url.port != url.defaultPort) ":${url.port}" else ""
            "${url.protocol}://${url.host}$portStr"
        } catch (e: Exception) {
            origin.substringBefore("/", origin)
        }
    }

    private fun setupExoPlayer() {
        exoPlayer?.release()

        lifecycleScope.launch(Dispatchers.IO) {
            var finalVideoUrl = videoUrl

            val headerMap = mutableMapOf<String, String>()
            if (refererHeader.isNotEmpty()) headerMap["Referer"] = refererHeader

            val cleanOrigin = sanitizeOrigin(originHeader)
            if (cleanOrigin.isNotEmpty()) {
                headerMap["Origin"] = cleanOrigin
            }

            if (cookieHeader.isNotEmpty()) headerMap["Cookie"] = cookieHeader
            customHeaders?.let { headerMap.putAll(it) }

            if (finalVideoUrl.contains("getM3u8", ignoreCase = true) || finalVideoUrl.contains("nbyjson", ignoreCase = true)) {
                val resolved = resolveApiM3u8Url(finalVideoUrl, headerMap)
                if (resolved.isNotBlank()) {
                    finalVideoUrl = resolved
                }
            }

            val currentReferer = headerMap["Referer"] ?: ""
            if (currentReferer == finalVideoUrl || currentReferer.contains("getM3u8")) {
                try {
                    val urlObj = URL(finalVideoUrl)
                    val portStr = if (urlObj.port != -1) ":${urlObj.port}" else ""
                    headerMap["Referer"] = "${urlObj.protocol}://${urlObj.host}$portStr/"
                } catch (e: Exception) {
                    headerMap["Referer"] = "https://www.netflixgc.com/"
                }
            }

            if (!headerMap.containsKey("Origin")) {
                try {
                    val u = URL(finalVideoUrl)
                    headerMap["Origin"] = "${u.protocol}://${u.host}"
                } catch (e: Exception) {
                    headerMap["Origin"] = "https://www.netflixgc.com"
                }
            }

            headerMap["Accept"] = "*/*"
            headerMap["Sec-Fetch-Mode"] = "cors"
            headerMap["Sec-Fetch-Site"] = "cross-site"

            withContext(Dispatchers.Main) {
                val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(15000)
                    .setReadTimeoutMs(15000)
                    .setUserAgent("Mozilla/5.0 (Linux; Android 12; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36")

                httpDataSourceFactory.setDefaultRequestProperties(headerMap)

                val baseDataSourceFactory = DefaultDataSource.Factory(this@PlayerActivity, httpDataSourceFactory)

                // 核心接入：装载带中插广告切除的净化数据源
                val cleanDataSourceFactory = DataSource.Factory {
                    AdFilteringDataSource(baseDataSourceFactory.createDataSource())
                }

                val mediaItemBuilder = MediaItem.Builder().setUri(finalVideoUrl)
                if (finalVideoUrl.contains(".m3u8", ignoreCase = true) || finalVideoUrl.contains("m3u8") || finalVideoUrl.startsWith("file://")) {
                    mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
                }
                val mediaItem = mediaItemBuilder.build()

                exoPlayer = ExoPlayer.Builder(this@PlayerActivity)
                    .setMediaSourceFactory(DefaultMediaSourceFactory(cleanDataSourceFactory))
                    .build()
                    .apply {
                        playerView.player = this
                        setMediaItem(mediaItem)

                        val savedPos = HistoryStorage.getSavedPosition(this@PlayerActivity, showName, epName)
                        if (savedPos > 3000L) {
                            seekTo(savedPos)
                            Toast.makeText(this@PlayerActivity, "已为您自动续播至 ${formatTime(savedPos)}", Toast.LENGTH_SHORT).show()
                        }

                        prepare()
                        playWhenReady = true

                        addListener(object : Player.Listener {
                            override fun onPlayerError(error: PlaybackException) {
                                showErrorDiagnosisDialog(error, headerMap)
                            }
                        })
                    }
            }
        }
    }

    private fun toggleFullScreen() {
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (isLandscape) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            layoutDetails.visibility = View.GONE
            val lp = playerContainer.layoutParams
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT
            playerContainer.layoutParams = lp
        } else {
            layoutDetails.visibility = View.VISIBLE
            val lp = playerContainer.layoutParams
            lp.height = (210 * resources.displayMetrics.density).toInt()
            playerContainer.layoutParams = lp
        }
    }

    private fun showErrorDiagnosisDialog(error: PlaybackException, headers: Map<String, String>) {
        val headersString = if (headers.isNotEmpty()) {
            headers.entries.joinToString("\n") { "${it.key}: ${it.value}" }
        } else {
            "无特定请求头"
        }

        val diagnosticLog = """
            ❌ 【播放失败诊断日志】
            
            1. 抓取到的视频 URL:
            $videoUrl
            
            2. 请求头 (Headers):
            $headersString
            
            3. 报错类型:
            ${error.errorCodeName} (${error.errorCode})
            
            4. 详细原因:
            ${error.localizedMessage ?: "未知网络/解包错误"}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("播放异常诊断")
            .setMessage(diagnosticLog)
            .setPositiveButton("一键复制日志") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("DiagnosticLog", diagnosticLog)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "诊断日志已复制到剪贴板！", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("网页 Token 无感修复重试") { _, _ ->
                val targetWebPage = refererHeader.ifEmpty { "https://www.netflixgc.com/" }
                sniffAndPlayEpisode(targetWebPage)
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun showBatchDownloadDialog() {
        val currentRoute = playRoutes.getOrNull(currentRouteIndex)
        val episodes = currentRoute?.episodes ?: emptyList()

        if (episodes.isEmpty()) {
            Toast.makeText(this, "暂无可选下载剧集", Toast.LENGTH_SHORT).show()
            return
        }

        val epNames = episodes.map { it.name }.toTypedArray()
        val checkedItems = BooleanArray(episodes.size) { index ->
            episodes[index].name == epName || episodes[index].url == videoUrl
        }

        var isAllSelected = false

        val dialog = AlertDialog.Builder(this)
            .setTitle("批量离线缓存 - ${currentRoute?.routeName ?: "默认线路"}")
            .setMultiChoiceItems(epNames, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("开始缓存") { _, _ ->
                var count = 0
                val cleanShow = DownloadStorage.extractShowName(showName).ifEmpty { showName }
                val routeTitle = currentRoute?.routeName ?: "默认线路"

                val headersMap = (customHeaders ?: emptyMap()).toMutableMap()
                if (!headersMap.containsKey("Referer")) {
                    headersMap["Referer"] = "https://jx.xllmcms.com/"
                    headersMap["Origin"] = "https://www.ruiding3bu.com"
                }

                for (i in episodes.indices) {
                    if (checkedItems[i]) {
                        val ep = episodes[i]

                        val item = DownloadItem(
                            id = "${cleanShow}_${routeTitle}_${ep.name}".hashCode().toString(),
                            showName = cleanShow,
                            epName = ep.name,
                            routeName = routeTitle,
                            title = "$cleanShow ${ep.name}",
                            videoUrl = ep.url,
                            posterUrl = posterUrl,
                            headers = headersMap,
                            status = DownloadItem.STATUS_DOWNLOADING
                        )

                        DownloadStorage.saveOrUpdateTask(applicationContext, item)
                        VideoDownloadManager.startDownload(applicationContext, item)

                        count++
                    }
                }
                if (count > 0) {
                    Toast.makeText(this, "已将 $count 个剧集加入离线缓存列表！", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "未勾选任何剧集", Toast.LENGTH_SHORT).show()
                }
            }
            .setNeutralButton("全选 / 取消", null)
            .setNegativeButton("取消", null)
            .create()

        dialog.setOnShowListener {
            val neutralBtn = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            neutralBtn?.setOnClickListener {
                isAllSelected = !isAllSelected
                val listView = dialog.listView
                for (i in episodes.indices) {
                    checkedItems[i] = isAllSelected
                    listView.setItemChecked(i, isAllSelected)
                }
            }
        }

        dialog.show()
    }

    private fun saveProgress() {
        val player = exoPlayer ?: return
        val currentPos = player.currentPosition
        val duration = player.duration.coerceAtLeast(0L)

        if (currentPos > 0 && showName.isNotEmpty()) {
            val cleanShow = DownloadStorage.extractShowName(showName).ifEmpty { showName }
            val headersMap = (customHeaders ?: emptyMap()).toMutableMap()
            if (!headersMap.containsKey("Referer")) {
                headersMap["Referer"] = "https://jx.xllmcms.com/"
                headersMap["Origin"] = "https://www.ruiding3bu.com"
            }

            HistoryStorage.saveProgress(
                context = applicationContext,
                showName = cleanShow,
                epName = epName,
                routeName = routeName,
                videoUrl = videoUrl,
                posterUrl = posterUrl,
                routesData = routesDataRaw,
                headers = headersMap,
                position = currentPos,
                duration = duration
            )
        }
    }

    override fun onPause() {
        super.onPause()
        saveProgress()
        exoPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        saveProgress()
        exoPlayer?.release()
        exoPlayer = null
        hiddenSnifferWebView?.destroy()
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
}