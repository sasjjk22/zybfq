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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class NativeHomeFragment : Fragment() {

    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var tabCategory: TabLayout
    private lateinit var viewPagerBanner: ViewPager2
    private lateinit var rvMovieGrid: RecyclerView

    private lateinit var bannerAdapter: BannerAdapter
    private lateinit var movieAdapter: MovieGridAdapter

    private var currentApiUrl = ""
    private var currentTypeId = ""
    private var currentPage = 1
    private var isLoadingMore = false
    private var loadJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
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

        tabCategory.setOnClickListener { showSourceSelectDialog() }

        bannerAdapter = BannerAdapter { openMovieDetail(it.vodId) }
        viewPagerBanner.adapter = bannerAdapter
        movieAdapter = MovieGridAdapter { openMovieDetail(it.vodId) }
    }

    private fun showSourceSelectDialog() {
        SourceSelectDialog { loadSourceAndData() }
            .show(childFragmentManager, "SourceSelectDialog")
    }

    private fun setupRecyclerView() {
        val layoutManager = GridLayoutManager(requireContext(), 3)
        rvMovieGrid.layoutManager = layoutManager
        rvMovieGrid.adapter = movieAdapter
        rvMovieGrid.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0 || isLoadingMore) return
                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                val thresholdReached = firstVisible + layoutManager.childCount >= layoutManager.itemCount - 6
                if (thresholdReached) loadMoreData()
            }
        })
    }

    private fun setupListeners() {
        swipeRefreshLayout.setOnRefreshListener {
            currentPage = 1
            loadMovieData(isRefresh = true)
        }

        tabCategory.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (!viewLifecycleOwner.lifecycle.currentState.isAtLeast(
                        androidx.lifecycle.Lifecycle.State.STARTED
                    )) return
                currentTypeId = tab.tag as? String ?: ""
                currentPage = 1
                loadMovieData(isRefresh = true)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    private fun loadSourceAndData() {
        val activeSource = SourceStorage.getActiveSource(requireContext())
        if (activeSource == null) {
            Toast.makeText(requireContext(), "请先配置有效影视源", Toast.LENGTH_LONG).show()
            swipeRefreshLayout.isRefreshing = false
            return
        }

        val rawUrl = activeSource.apiUrl.ifEmpty { activeSource.url }
        if (rawUrl.endsWith(".json", ignoreCase = true) || rawUrl.contains("json", ignoreCase = true)) {
            swipeRefreshLayout.isRefreshing = true
            lifecycleScope.launch {
                val subSources = SourceSpeedTester.parseTvBoxSubscription(rawUrl)
                if (!isAdded) return@launch
                if (subSources.isNotEmpty()) {
                    SourceStorage.addSources(requireContext(), subSources)
                    val firstSource = subSources.first()
                    SourceStorage.setActiveSource(requireContext(), firstSource.id)
                    Toast.makeText(requireContext(), "已解包订阅：${firstSource.name}", Toast.LENGTH_LONG).show()
                    loadSourceAndData()
                } else {
                    swipeRefreshLayout.isRefreshing = false
                    Toast.makeText(requireContext(), "订阅源无法解析，请切换其他源", Toast.LENGTH_SHORT).show()
                }
            }
            return
        }

        currentApiUrl = rawUrl
        loadCategories()
        loadMovieData(isRefresh = true)
    }

    private fun loadCategories() {
        lifecycleScope.launch {
            val categories = buildList {
                add(CategoryItem("", "推荐"))
                addAll(SubscriptionEngine.fetchCategories(currentApiUrl))
            }
            if (!isAdded) return@launch

            tabCategory.removeAllTabs()
            categories.forEach { category ->
                tabCategory.newTab()
                    .setText(category.typeName)
                    .apply {
                        tag = category.typeId
                        tabCategory.addTab(this)
                    }
            }
        }
    }

    private fun loadMovieData(isRefresh: Boolean) {
        if (currentApiUrl.isBlank()) return
        loadJob?.cancel()
        swipeRefreshLayout.isRefreshing = true
        loadJob = lifecycleScope.launch {
            val items = SubscriptionEngine.fetchVodList(currentApiUrl, currentTypeId, currentPage)
            if (!isAdded) return@launch
            swipeRefreshLayout.isRefreshing = false

            if (items.isEmpty()) {
                if (isRefresh) {
                    movieAdapter.setData(emptyList())
                    bannerAdapter.updateData(emptyList())
                    viewPagerBanner.visibility = View.GONE
                    Toast.makeText(requireContext(), "当前源暂无数据，请切换影视源", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            if (isRefresh) {
                val banners = items.take(5).map {
                    BannerItem(it.vodId, it.vodName, it.vodPic, it.vodRemarks)
                }
                bannerAdapter.updateData(banners)
                viewPagerBanner.visibility = if (banners.isEmpty()) View.GONE else View.VISIBLE
                movieAdapter.setData(items)
            } else {
                movieAdapter.appendData(items)
            }
        }
    }

    private fun loadMoreData() {
        if (isLoadingMore || currentApiUrl.isBlank()) return
        isLoadingMore = true
        val requestedPage = currentPage + 1
        lifecycleScope.launch {
            val items = SubscriptionEngine.fetchVodList(currentApiUrl, currentTypeId, requestedPage)
            if (!isAdded) return@launch
            isLoadingMore = false
            if (items.isNotEmpty()) {
                currentPage = requestedPage
                movieAdapter.appendData(items)
            }
        }
    }

    private fun openMovieDetail(vodId: String) {
        startActivity(Intent(requireContext(), MovieGridAdapter::class.java).apply {
            // The destination activity is resolved explicitly below in a separate intent.
            putExtra("EXTRA_VOD_ID", vodId)
            putExtra("EXTRA_API_URL", currentApiUrl)
        }.apply {
            setClass(requireContext(), MovieDetailActivity::class.java)
        })
    }

    override fun onDestroyView() {
        loadJob?.cancel()
        super.onDestroyView()
    }
}
