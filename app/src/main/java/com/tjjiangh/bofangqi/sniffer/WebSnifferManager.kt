package com.tjjiangh.bofangqi.sniffer

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

class WebSnifferManager(private val context: Context) {

    private var webView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun startSniffing(targetWebUrl: String, onVideoFound: (SnifferMediaInfo) -> Unit) {
        destroy()

        webView = WebView(context.applicationContext).apply {
            val currentWebView = this
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                setSupportMultipleWindows(true)
                javaScriptCanOpenWindowsAutomatically = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString = "Mozilla/5.0 (Linux; Android 12; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Mobile Safari/537.36"
                mediaPlaybackRequiresUserGesture = false
            }

            webChromeClient = SnifferWebChromeClient().apply {
                targetWebView = currentWebView
            }
            webViewClient = SnifferWebViewClient(targetWebUrl, onVideoFound)

            loadUrl(targetWebUrl)
        }
    }

    fun destroy() {
        webView?.apply {
            stopLoading()
            loadUrl("about:blank")
            webChromeClient = null
            webViewClient = WebViewClient()
            destroy()
        }
        webView = null
    }
}