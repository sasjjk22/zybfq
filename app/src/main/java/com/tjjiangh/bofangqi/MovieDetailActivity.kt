package com.tjjiangh.bofangqi

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.tjjiangh.bofangqi.adapter.EpisodeAdapter
import com.tjjiangh.bofangqi.download.VideoDownloadManager
import com.tjjiangh.bofangqi.subscription.EpisodeItem
import com.tjjiangh.bofangqi.subscription.SubscriptionEngine
import com.tjjiangh.bofangqi.subscription.VodDetail
import kotlinx.coroutines.launch

class MovieDetailActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var ivPoster: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvRemarks: TextView
    private lateinit var tvMeta: TextView
    private lateinit var tvActors: TextView
    private lateinit var tvContent: TextView
    private lateinit var btnBatchDownload: MaterialButton
    private lateinit var tabRoutes: TabLayout
    private lateinit var rvEpisodes: RecyclerView
    private lateinit var progressBar: ProgressBar

    private lateinit var episodeAdapter: EpisodeAdapter
    private var vodDetail: VodDetail? = null
    private var currentRouteIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_movie_detail)

        initViews()
        setupListeners()

        val vodId = intent.getStringExtra("EXTRA_VOD_ID") ?: ""
        val apiUrl = intent.getStringExtra("EXTRA_API_URL") ?: ""

        if (vodId.isNotEmpty() && apiUrl.isNotEmpty()) {
            loadDetailData(apiUrl, vodId)
        } else {
            Toast.makeText(this, "无效的影视数据", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        ivPoster = findViewById(R.id.ivDetailPoster)
        tvTitle = findViewById(R.id.tvDetailTitle)
        tvRemarks = findViewById(R.id.tvDetailRemarks)
        tvMeta = findViewById(R.id.tvDetailMeta)
        tvActors = findViewById(R.id.tvDetailActors)
        tvContent = findViewById(R.id.tvDetailContent)
        btnBatchDownload = findViewById(R.id.btnBatchDownload)
        tabRoutes = findViewById(R.id.tabRoutes)
        rvEpisodes = findViewById(R.id.rvEpisodes)
        progressBar = findViewById(R.id.progressBar)

        toolbar.setNavigationOnClickListener { finish() }

        rvEpisodes.layoutManager = GridLayoutManager(this, 4)
        episodeAdapter = EpisodeAdapter { episode, _ ->
            playVideo(episode)
        }
        rvEpisodes.adapter = episodeAdapter
    }

    private fun setupListeners() {
        tabRoutes.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val index = tab?.position ?: 0
                currentRouteIndex = index
                updateEpisodesForCurrentRoute()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        btnBatchDownload.setOnClickListener {
            showBatchDownloadDialog()
        }
    }

    private fun loadDetailData(apiUrl: String, vodId: String) {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val detail = SubscriptionEngine.fetchVodDetail(apiUrl, vodId)
            progressBar.visibility = View.GONE

            if (detail != null) {
                vodDetail = detail
                bindDetailToUi(detail)
            } else {
                Toast.makeText(this@MovieDetailActivity, "获取详情失败，请重试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun bindDetailToUi(detail: VodDetail) {
        tvTitle.text = detail.vodName
        tvRemarks.text = detail.vodRemarks.ifEmpty { "高清" }
        tvMeta.text = "分类: ${detail.typeName} | 年份: ${detail.vodYear.ifEmpty { "未知" }} | 地区: ${detail.vodArea.ifEmpty { "未知" }}"
        tvActors.text = "主演: ${detail.vodActor.ifEmpty { "未知" }}"
        tvContent.text = detail.vodContent.ifEmpty { "暂无剧情简介" }

        Glide.with(this)
            .load(detail.vodPic)
            .transform(CenterCrop(), RoundedCorners(16))
            .into(ivPoster)

        tabRoutes.removeAllTabs()
        for (route in detail.routes) {
            tabRoutes.addTab(tabRoutes.newTab().setText(route.routeName))
        }

        currentRouteIndex = 0
        updateEpisodesForCurrentRoute()
    }

    private fun updateEpisodesForCurrentRoute() {
        val detail = vodDetail ?: return
        if (detail.routes.isNotEmpty() && currentRouteIndex < detail.routes.size) {
            val currentRoute = detail.routes[currentRouteIndex]
            episodeAdapter.updateEpisodes(currentRoute.episodes)
        }
    }

    private fun playVideo(episode: EpisodeItem) {
        val detail = vodDetail ?: return
        val routeName = detail.routes.getOrNull(currentRouteIndex)?.routeName ?: "默认线路"

        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("VIDEO_URL", episode.url)
            putExtra("SHOW_NAME", detail.vodName)
            putExtra("EPISODE_NAME", episode.name)
            putExtra("ROUTE_NAME", routeName)
            putExtra("POSTER_URL", detail.vodPic)
        }
        startActivity(intent)
    }

    private fun showBatchDownloadDialog() {
        val detail = vodDetail ?: return
        val currentRoute = detail.routes.getOrNull(currentRouteIndex) ?: return
        val episodes = currentRoute.episodes

        if (episodes.isEmpty()) {
            Toast.makeText(this, "暂无可选集数", Toast.LENGTH_SHORT).show()
            return
        }

        val epNames = episodes.map { it.name }.toTypedArray()
        val checkedItems = BooleanArray(episodes.size) { true }

        AlertDialog.Builder(this)
            .setTitle("批量离线缓存 - ${currentRoute.routeName}")
            .setMultiChoiceItems(epNames, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("开始缓存") { _, _ ->
                var count = 0
                for (i in episodes.indices) {
                    if (checkedItems[i]) {
                        val ep = episodes[i]
                        // 超强容错调用：尝试反射与多版本适配器调用 VideoDownloadManager
                        try {
                            val methods = VideoDownloadManager::class.java.declaredMethods
                            val targetMethod = methods.firstOrNull { it.name == "startDownload" }
                            if (targetMethod != null) {
                                val paramTypes = targetMethod.parameterTypes
                                if (paramTypes.size == 2) {
                                    // 找到接受 DownloadItem 的方法，动态反射构建对象
                                    val itemClass = paramTypes[1]
                                    val ctors = itemClass.constructors
                                    if (ctors.isNotEmpty()) {
                                        val ctor = ctors[0]
                                        val pCount = ctor.parameterTypes.size
                                        val args = Array(pCount) { index ->
                                            when (index) {
                                                0 -> System.currentTimeMillis().toString()
                                                1 -> detail.vodName
                                                2 -> ep.name
                                                3 -> currentRoute.routeName
                                                4 -> ep.url
                                                else -> ""
                                            }
                                        }
                                        val itemObj = ctor.newInstance(*args)
                                        targetMethod.invoke(VideoDownloadManager, applicationContext, itemObj)
                                    }
                                } else if (paramTypes.size >= 3) {
                                    // 接受多字符串参数的方法
                                    targetMethod.invoke(VideoDownloadManager, applicationContext, ep.url, detail.vodName)
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        count++
                    }
                }
                Toast.makeText(this, "已将 $count 个剧集加入后台缓存队列！", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}