package com.tjjiangh.bofangqi

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.tjjiangh.bofangqi.sniffer.AdBlockEngine
import com.tjjiangh.bofangqi.sniffer.SnifferUtils
import com.tjjiangh.bofangqi.sniffer.SnifferWebChromeClient
import com.tjjiangh.bofangqi.source.SourceStorage
import java.io.ByteArrayInputStream

class HomeFragment : Fragment() {

    private lateinit var btnWebBack: ImageButton
    private lateinit var btnWebForward: ImageButton
    private lateinit var btnWebRefresh: ImageButton
    private lateinit var tvSourceTitle: TextView
    private lateinit var progressWeb: ProgressBar
    private lateinit var webViewHome: WebView

    private var isLaunchingPlayer = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentLoadedUrl: String = ""
    private var routesDataRaw: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupWebView()
        loadActiveSource()
    }

    private fun initViews(view: View) {
        btnWebBack = view.findViewById(R.id.btn_web_back)
        btnWebForward = view.findViewById(R.id.btn_web_forward)
        btnWebRefresh = view.findViewById(R.id.btn_web_refresh)
        tvSourceTitle = view.findViewById(R.id.tv_source_title)
        progressWeb = view.findViewById(R.id.progress_web)
        webViewHome = view.findViewById(R.id.web_view_home)

        btnWebBack.setOnClickListener {
            if (webViewHome.canGoBack()) webViewHome.goBack()
        }

        btnWebForward.setOnClickListener {
            if (webViewHome.canGoForward()) webViewHome.goForward()
        }

        btnWebRefresh.setOnClickListener {
            webViewHome.reload()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webViewHome, true)

        webViewHome.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = "Mozilla/5.0 (Linux; Android 12; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36"
            mediaPlaybackRequiresUserGesture = false
        }

        webViewHome.addJavascriptInterface(object {
            @JavascriptInterface
            fun onVideoFound(url: String) {
                mainHandler.post {
                    triggerPlayerLaunch(url)
                }
            }

            @JavascriptInterface
            fun onRoutesFound(data: String) {
                mainHandler.post {
                    if (data.length > routesDataRaw.length) {
                        routesDataRaw = data
                    }
                }
            }
        }, "AndroidSniffer")

        webViewHome.webChromeClient = object : SnifferWebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress == 100) {
                    progressWeb.visibility = View.GONE
                } else {
                    progressWeb.visibility = View.VISIBLE
                    progressWeb.progress = newProgress
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                title?.let {
                    if (it.isNotBlank() && !it.contains(".json") && !it.contains("{")) {
                        tvSourceTitle.text = it
                    }
                }
            }
        }

        webViewHome.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                request?.let { webRequest ->
                    val url = webRequest.url.toString()

                    if (AdBlockEngine.isAdUrl(url)) {
                        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
                    }

                    if (!isLaunchingPlayer && SnifferUtils.isVideoUrl(url)) {
                        mainHandler.post {
                            triggerPlayerLaunch(url)
                        }
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    val host = request.url?.host ?: ""
                    Toast.makeText(context, "⚠️ 站点 [$host] 域名已失效，请切换其他站点", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                url?.let { currentLoadedUrl = it }
                view?.evaluateJavascript(AdBlockEngine.INJECT_AD_CLEAN_JS, null)
                view?.evaluateJavascript(AdBlockEngine.INJECT_ROUTE_EXTRACTION_JS, null)
            }
        }
    }

    private fun triggerPlayerLaunch(rawUrl: String) {
        if (rawUrl.startsWith("blob:") || isLaunchingPlayer) return

        val realVideoUrl = SnifferUtils.extractRealVideoUrl(rawUrl)
        if (!SnifferUtils.isVideoUrl(realVideoUrl)) return

        isLaunchingPlayer = true

        val headers = mutableMapOf<String, String>()
        headers["Referer"] = if (rawUrl.startsWith("http")) rawUrl else currentLoadedUrl
        headers["Origin"] = currentLoadedUrl
        headers["User-Agent"] = webViewHome.settings.userAgentString

        val cookie = CookieManager.getInstance().getCookie(realVideoUrl) ?: CookieManager.getInstance().getCookie(currentLoadedUrl)
        if (!cookie.isNullOrEmpty()) {
            headers["Cookie"] = cookie
        }

        Toast.makeText(context, "🎬 嗅探成功，正在打开播放器...", Toast.LENGTH_SHORT).show()

        context?.let { ctx ->
            PlayerActivity.start(
                context = ctx,
                videoUrl = realVideoUrl,
                title = tvSourceTitle.text.toString(),
                headers = HashMap(headers),
                routesData = routesDataRaw
            )
        }
    }

    private fun loadActiveSource() {
        val context = context ?: return
        val activeSource = SourceStorage.getActiveSource(context) ?: return

        var targetWebUrl = activeSource.url.ifEmpty { activeSource.apiUrl }

        // 核心保护：如果是代码/API 链接，首页强制转换为精美人类网页主页，绝不展示纯代码！
        if (targetWebUrl.contains(".json") || targetWebUrl.contains("/api.php")) {
            targetWebUrl = "https://www.ruiding3bu.com/"
        }

        tvSourceTitle.text = activeSource.name.ifEmpty { "大师兄影视" }
        currentLoadedUrl = targetWebUrl
        routesDataRaw = ""
        webViewHome.loadUrl(targetWebUrl)
    }

    override fun onResume() {
        super.onResume()
        isLaunchingPlayer = false

        val context = context ?: return
        val activeSource = SourceStorage.getActiveSource(context)
        if (activeSource != null) {
            var targetUrl = activeSource.url.ifEmpty { activeSource.apiUrl }
            if (targetUrl.contains(".json") || targetUrl.contains("/api.php")) {
                targetUrl = "https://www.ruiding3bu.com/"
            }
            if (targetUrl != currentLoadedUrl && !webViewHome.canGoBack()) {
                loadActiveSource()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        webViewHome.stopLoading()
        webViewHome.destroy()
    }
}