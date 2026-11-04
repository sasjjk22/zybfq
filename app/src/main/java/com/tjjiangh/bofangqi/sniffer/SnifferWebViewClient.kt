package com.tjjiangh.bofangqi.sniffer

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

open class SnifferWebViewClient : WebViewClient {

    private var targetWebUrl: String = ""
    private var onMediaFound: ((SnifferMediaInfo) -> Unit)? = null
    private var onVideoIntercepted: ((String, Map<String, String>) -> Unit)? = null

    constructor() : super()

    constructor(targetWebUrl: String, onVideoFound: (SnifferMediaInfo) -> Unit) : super() {
        this.targetWebUrl = targetWebUrl
        this.onMediaFound = onVideoFound
    }

    constructor(onVideoIntercepted: (String, Map<String, String>) -> Unit) : super() {
        this.onVideoIntercepted = onVideoIntercepted
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        request?.let { webRequest ->
            val urlStr = webRequest.url.toString()

            if (AdBlockEngine.isAdUrl(urlStr)) {
                return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
            }

            if (SnifferUtils.isVideoUrl(urlStr)) {
                val headersMap = webRequest.requestHeaders ?: emptyMap()
                val realUrl = SnifferUtils.extractRealVideoUrl(urlStr)
                val mediaInfo = SnifferMediaInfo(
                    url = realUrl,
                    videoUrl = realUrl,
                    headers = headersMap,
                    pageUrl = targetWebUrl
                )
                onMediaFound?.invoke(mediaInfo)
                onVideoIntercepted?.invoke(realUrl, headersMap)
            }
        }
        return super.shouldInterceptRequest(view, request)
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        view?.evaluateJavascript(AdBlockEngine.INJECT_AD_CLEAN_JS, null)
        view?.evaluateJavascript(AdBlockEngine.INJECT_ROUTE_EXTRACTION_JS, null)
    }
}