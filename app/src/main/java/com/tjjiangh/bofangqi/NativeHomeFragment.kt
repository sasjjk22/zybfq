package com.tjjiangh.bofangqi

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.tjjiangh.bofangqi.adapter.BannerAdapter
import com.tjjiangh.bofangqi.adapter.MovieGridAdapter
import com.tjjiangh.bofangqi.source.SourceSelectDialog
import com.tjjiangh.bofangqi.source.SourceSpeedTester
import com.tjjiangh.bofangqi.source.SourceStorage
import com.tjjiangh.bofangqi.subscription.BannerItem
import com.tjjiangh.bofangqi.subscription.CategoryItem
import com.tjjiangh.bofangqi.subscription.SubscriptionEngine
import kotlinx.coroutines.launch

class NativeHomeFragment : Fragment() {

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var tabCategory: TabLayout
    private lateinit var viewPagerBanner: ViewPager2
    private lateinit var rvMovieGrid: RecyclerView

    private lateinit var bannerAdapter: BannerAdapter
    private lateinit var movieAdapter: MovieGridAdapter

    private var currentApiUrl: String = ""
    private var currentTypeId: String = ""
    private var currentPage: Int = 1
    private var isLoadingMore: Boolean = false

    private val categories = mutableListOf<CategoryItem>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_native_home, container, false)
        initViews(view)
        setupRecyclerView()
        setupListeners()
        loadSourceAndData()
        return view
    }

    private fun initViews(view: View) {
        swipeRefreshLayout = view.findViewById(R.id.swipeRefresh)
        tabCategory = view.findViewById(R.id.tabCategory)
        viewPagerBanner = view.findViewById(R.id.viewPagerBanner)
        rvMovieGrid = view.findViewById(R.id.rvMovieGrid)

        view.findViewById<View>(R.id.btnSearchHeader)?.setOnClickListener {
            startActivity(Intent(requireContext(), SearchActivity::class.java))
        }

        tabCategory.setOnClickListener {
            showSourceSelectDialog()
        }

        bannerAdapter = BannerAdapter { banner ->
            openMovieDetail(banner.vodId)
        }
        viewPagerBanner.adapter = bannerAdapter

        movieAdapter = MovieGridAdapter { vodItem ->
            openMovieDetail(vodItem.vodId)
        }
    }

    private fun showSourceSelectDialog() {
        val dialog = SourceSelectDialog {
            loadSourceAndData()
        }
        dialog.show(childFragmentManager, "SourceSelectDialog")
    }

    private fun setupRecyclerView() {
        val gridLayoutManager = GridLayoutManager(requireContext(), 3)
        rvMovieGrid.layoutManager = gridLayoutManager
        rvMovieGrid.adapter = movieAdapter

        rvMovieGrid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy > 0 && !isLoadingMore) {
                    val visibleCount = gridLayoutManager.childCount
                    val totalCount = gridLayoutManager.itemCount
                    val firstVisiblePos = gridLayoutManager.findFirstVisibleItemPosition()

                    if ((visibleCount + firstVisiblePos) >= totalCount - 6) {
                        loadMoreData()
                    }
                }
            }
        })
    }

    private fun setupListeners() {
        swipeRefreshLayout.setOnRefreshListener {
            currentPage = 1
            loadMovieData(isRefresh = true)
        }

        tabCategory.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val typeId = tab?.tag as? String ?: ""
                currentTypeId = typeId
                currentPage = 1
                loadMovieData(isRefresh = true)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun loadSourceAndData() {
        val activeSource = SourceStorage.getActiveSource(requireContext())
        if (activeSource == null) {
            Toast.makeText(context, "请先配置有效影视源", Toast.LENGTH_LONG).show()
            swipeRefreshLayout.isRefreshing = false
            return
        }

        val rawUrl = activeSource.apiUrl.ifEmpty { activeSource.url }

        // 核心突破：检测到当前选中的是 TvBox JSON 订阅源，自动在后台解包并刷新海报墙，彻底解决黑屏！
        if (rawUrl.endsWith(".json") || rawUrl.contains("json")) {
            swipeRefreshLayout.isRefreshing = true
            lifecycleScope.launch {
                val subSources = SourceSpeedTester.parseTvBoxSubscription(rawUrl)
                if (subSources.isNotEmpty()) {
                    SourceStorage.addSources(requireContext(), subSources)
                    val firstSource = subSources[0]
                    SourceStorage.setActiveSource(requireContext(), firstSource.id)
                    Toast.makeText(context, "🎉 已自动解包 TvBox 订阅，为你切换至 [${firstSource.name}]", Toast.LENGTH_LONG).show()
                    loadSourceAndData() // 递归加载解包出的第一个真实源
                } else {
                    swipeRefreshLayout.isRefreshing = false
                    Toast.makeText(context, "⚠️ 当前订阅源无法解析出海报，请点击切换源", Toast.LENGTH_SHORT).show()
                }
            }
            return
        }

        currentApiUrl = rawUrl

        lifecycleScope.launch {
            val list = SubscriptionEngine.fetchCategories(currentApiUrl)
            categories.clear()
            categories.add(CategoryItem("", "推荐"))
            categories.addAll(list)

            tabCategory.removeAllTabs()
            for (cat in categories) {
                val tab = tabCategory.newTab().setText(cat.typeName)
                tab.tag = cat.typeId
                tabCategory.addTab(tab)
            }
        }

        loadMovieData(isRefresh = true)
    }

    private fun loadMovieData(isRefresh: Boolean) {
        swipeRefreshLayout.isRefreshing = true
        lifecycleScope.launch {
            val vodList = SubscriptionEngine.fetchVodList(currentApiUrl, currentTypeId, currentPage)
            swipeRefreshLayout.isRefreshing = false

            if (vodList.isNotEmpty()) {
                if (isRefresh) {
                    val bannerItems = vodList.take(5).map {
                        BannerItem(it.vodId, it.vodName, it.vodPic, it.vodRemarks)
                    }
                    bannerAdapter.updateData(bannerItems)
                    viewPagerBanner.visibility = if (bannerItems.isNotEmpty()) View.VISIBLE else View.GONE

                    movieAdapter.setData(vodList)
                } else {
                    movieAdapter.appendData(vodList)
                }
            } else if (isRefresh) {
                Toast.makeText(context, "当前源暂无海报数据，建议点击搜索框选择【切换影视源】", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadMoreData() {
        isLoadingMore = true
        currentPage++
        lifecycleScope.launch {
            val vodList = SubscriptionEngine.fetchVodList(currentApiUrl, currentTypeId, currentPage)
            isLoadingMore = false
            if (vodList.isNotEmpty()) {
                movieAdapter.appendData(vodList)
            }
        }
    }

    private fun openMovieDetail(vodId: String) {
        val intent = Intent(requireContext(), MovieDetailActivity::class.java).apply {
            putExtra("EXTRA_VOD_ID", vodId)
            putExtra("EXTRA_API_URL", currentApiUrl)
        }
        startActivity(intent)
    }
}