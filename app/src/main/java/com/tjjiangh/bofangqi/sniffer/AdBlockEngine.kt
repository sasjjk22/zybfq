package com.tjjiangh.bofangqi.sniffer

import android.webkit.WebView

object AdBlockEngine {

    // 广告域名与关键字黑名单
    val AD_HOST_KEYWORDS = listOf(
        "pos.baidu.com", "cpro.baidustatic.com", "tanx.com", "alimama.com",
        "googlesyndication.com", "googleads", "doubleclick.net", "adservice",
        "adsmogo", "umeng.com", "cnzz.com", "hm.baidu.com", "tongji",
        "union", "pstatp.com/bytecom", "pangolin", "openx", "popads",
        "158158", "xinpujing", "gamble", "bet365", "qp777"
    )

    // 网页去广告 JS 注入常量（提供给 HomeFragment / SnifferWebViewClient 调用）
    const val INJECT_AD_CLEAN_JS: String = """
        (function() {
            var adSelectors = [
                'div[class*="ad-"]', 'div[id*="ad-"]', 'div[class*="advert"]',
                'div[id*="advert"]', 'div[class*="popup"]', 'div[class*="float"]',
                'iframe[src*="ad"]', 'iframe[src*="union"]', '.fed-pops-layer',
                '#popup', '.popup', '#float-ad', '.float-ad'
            ];
            adSelectors.forEach(function(sel) {
                var els = document.querySelectorAll(sel);
                els.forEach(function(el) { el.remove(); });
            });

            var allDivs = document.getElementsByTagName('div');
            for (var i = 0; i < allDivs.length; i++) {
                var style = window.getComputedStyle(allDivs[i]);
                if (parseInt(style.zIndex) > 9999 && (style.position === 'fixed' || style.position === 'absolute')) {
                    allDivs[i].style.display = 'none';
                }
            }
        })();
    """

    // 精准选集提取 JS 常量（彻底屏蔽侧边栏最近播放 history 与猜你喜欢 tuijian 杂质）
    const val INJECT_ROUTE_EXTRACTION_JS: String = """
        (function() {
            var junkKeywords = ['history', 'recente', 'recommend', 'tuijian', 'hot', 'like', 'relate', 'side', 'fav', 'rank'];
            
            function isJunk(el) {
                var cur = el;
                while (cur && cur !== document.body) {
                    var idClass = ((cur.id || '') + ' ' + (cur.className || '')).toLowerCase();
                    if (junkKeywords.some(function(k) { return idClass.indexOf(k) !== -1; })) {
                        return true;
                    }
                    cur = cur.parentElement;
                }
                return false;
            }

            var routes = [];
            var listContainers = document.querySelectorAll('.module-play-list, .module-player-list, .playlist, .stui-content__playlist, .anthology-list, .play-list, [id*="playlist"]');
            var tabItems = document.querySelectorAll('.module-tab-item, .tab-item, .nav-tabs li, [data-dropdown] .dropdown-item');

            if (listContainers.length > 0) {
                for (var i = 0; i < listContainers.length; i++) {
                    var cont = listContainers[i];
                    if (isJunk(cont)) continue;

                    var routeName = "线路 " + (i + 1);
                    if (tabItems.length > i && !isJunk(tabItems[i])) {
                        var tText = tabItems[i].innerText.trim();
                        if (tText) routeName = tText;
                    }

                    var epLinks = cont.querySelectorAll('a');
                    var episodes = [];
                    for (var j = 0; j < epLinks.length; j++) {
                        var a = epLinks[j];
                        var epName = a.innerText.trim();
                        var epHref = a.getAttribute('href') || a.getAttribute('data-href') || '';
                        if (epName && epHref) {
                            episodes.push({ name: epName, url: epHref });
                        }
                    }

                    if (episodes.length > 0) {
                        routes.push({ name: routeName, episodes: episodes });
                    }
                }
            }

            if (routes.length === 0) {
                var mainLinks = document.querySelectorAll('a[href*="play"], a[href*="vodplay"]');
                var validEps = [];
                for (var k = 0; k < mainLinks.length; k++) {
                    var link = mainLinks[k];
                    if (isJunk(link)) continue;
                    var txt = link.innerText.trim();
                    var href = link.getAttribute('href') || '';
                    if (txt && href && (txt.indexOf('第') !== -1 || txt.indexOf('集') !== -1 || txt.indexOf('TC') !== -1 || txt.indexOf('HD') !== -1 || txt.indexOf('正片') !== -1)) {
                        validEps.push({ name: txt, url: href });
                    }
                }
                if (validEps.length > 0) {
                    routes.push({ name: "默认线路", episodes: validEps });
                }
            }

            return JSON.stringify(routes);
        })();
    """

    fun isAdUrl(url: String): Boolean {
        val lower = url.lowercase()
        return AD_HOST_KEYWORDS.any { lower.contains(it) }
    }

    fun injectAdBlockJs(webView: WebView) {
        webView.evaluateJavascript(INJECT_AD_CLEAN_JS, null)
    }

    fun extractPageRoutesJs(): String = INJECT_ROUTE_EXTRACTION_JS
}