package com.tjjiangh.bofangqi

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tjjiangh.bofangqi.adapter.MovieGridAdapter
import com.tjjiangh.bofangqi.source.SourceModel
import com.tjjiangh.bofangqi.source.SourceSpeedTester
import com.tjjiangh.bofangqi.source.SourceStorage
import com.tjjiangh.bofangqi.subscription.SubscriptionEngine
import com.tjjiangh.bofangqi.subscription.VodItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

class SearchActivity : AppCompatActivity() {

    private lateinit var ivBack: ImageView
    private lateinit var etSearchInput: EditText
    private lateinit var tvSearchBtn: TextView
    private lateinit var rvSearchResults: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmptyHint: TextView

    private lateinit var movieAdapter: MovieGridAdapter
    private var allSources = mutableListOf<SourceModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        initViews()
        setupRecyclerView()
        setupListeners()
    }

    private fun initViews() {
        ivBack = findViewById(R.id.ivBack)
        etSearchInput = findViewById(R.id.etSearchInput)
        tvSearchBtn = findViewById(R.id.tvSearchBtn)
        rvSearchResults = findViewById(R.id.rvSearchResults)
        progressBar = findViewById(R.id.progressBar)
        tvEmptyHint = findViewById(R.id.tvEmptyHint)

        ivBack.setOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        rvSearchResults.layoutManager = GridLayoutManager(this, 3)
        movieAdapter = MovieGridAdapter { vodItem ->
            // 点击搜索卡片，携带对应的源 API 地址与 VOD_ID 跳转至原生影视详情页
            val intent = Intent(this, MovieDetailActivity::class.java).apply {
                putExtra("EXTRA_VOD_ID", vodItem.vodId)
                putExtra("EXTRA_API_URL", vodItem.typeName) // typeName 里保存了对应的源 API 地址
            }
            startActivity(intent)
        }
        rvSearchResults.adapter = movieAdapter
    }

    private fun setupListeners() {
        tvSearchBtn.setOnClickListener { performSearch() }

        etSearchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }
    }

    /**
     * 核心：后台全网多源并发搜索引擎
     */
    private fun performSearch() {
        val keyword = etSearchInput.text.toString().trim()
        if (keyword.isEmpty()) {
            Toast.makeText(this, "请输入搜索关键词", Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        tvEmptyHint.visibility = View.GONE
        movieAdapter.setData(emptyList())

        lifecycleScope.launch {
            allSources.clear()
            allSources.addAll(SourceStorage.getAllSources(this@SearchActivity))

            val aggregateApiList = mutableListOf<Pair<String, String>>() // Pair<站点名称, API地址>

            // 1. 遍历所有配置的源，如果是 TvBox JSON 订阅，后台自动解包其所有子站点！
            for (source in allSources) {
                val targetUrl = source.apiUrl.ifEmpty { source.url }
                if (targetUrl.contains(".json") || targetUrl.contains("json")) {
                    val subSources = SourceSpeedTester.parseTvBoxSubscription(targetUrl)
                    for (sub in subSources) {
                        if (sub.apiUrl.contains("api.php") || sub.apiUrl.contains("vod")) {
                            aggregateApiList.add(Pair(sub.name, sub.apiUrl))
                        }
                    }
                } else if (targetUrl.contains("api.php") || targetUrl.contains("vod")) {
                    aggregateApiList.add(Pair(source.name, targetUrl))
                }
            }

            // 兜底补全通用 API 源
            if (aggregateApiList.isEmpty()) {
                aggregateApiList.add(Pair("大师兄影视", "https://www.ruiding3bu.com/api.php/provide/vod/"))
                aggregateApiList.add(Pair("光速资源", "https://api.guangsuapi.com/api.php/provide/vod/"))
                aggregateApiList.add(Pair("非凡资源", "http://cj.ffzyapi.com/api.php/provide/vod/"))
            }

            // 2. 并发协程同时对所有 API 站点发起后台异步检索
            val searchTasks = aggregateApiList.map { (siteName, apiUrl) ->
                async {
                    try {
                        val results = SubscriptionEngine.searchVod(apiUrl, keyword)
                        results.map { item ->
                            // 将角标标注为来源站点，并在 typeName 中储存 API 地址供详情页调取
                            item.copy(
                                vodRemarks = if (item.vodRemarks.isNotBlank()) "${item.vodRemarks} [$siteName]" else "[$siteName]",
                                typeName = apiUrl
                            )
                        }
                    } catch (e: Exception) {
                        emptyList<VodItem>()
                    }
                }
            }

            val allResults = searchTasks.awaitAll().flatten()
            progressBar.visibility = View.GONE

            if (allResults.isNotEmpty()) {
                movieAdapter.setData(allResults)
                tvEmptyHint.visibility = View.GONE
            } else {
                tvEmptyHint.visibility = View.VISIBLE
                tvEmptyHint.text = "全网未检索到与“$keyword”相关的影视资源"
            }
        }
    }
}