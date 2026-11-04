package com.tjjiangh.bofangqi

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.tjjiangh.bofangqi.source.SourceModel
import com.tjjiangh.bofangqi.source.SourceStorage

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private var currentTabId: Int = R.id.nav_home

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 100% 保留：恢复深色/夜间模式
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode", false)
        if (isDarkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }

        setContentView(R.layout.activity_main)

        bottomNav = findViewById(R.id.bottom_navigation)

        // 100% 保留：恢复切换主题/夜间模式前选中的 Tab (解决自动重置回首页的 BUG)
        if (savedInstanceState != null) {
            currentTabId = savedInstanceState.getInt("selected_tab_id", R.id.nav_home)
        }

        bottomNav.setOnItemSelectedListener { item ->
            currentTabId = item.itemId
            switchFragment(item.itemId)
            true
        }

        if (savedInstanceState == null) {
            switchFragment(R.id.nav_home)
        } else {
            bottomNav.selectedItemId = currentTabId
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("selected_tab_id", currentTabId)
    }

    /**
     * 核心智能路由分发：
     * - 原生 API / 订阅源 ➔ 加载 NativeHomeFragment (海报墙)
     * - 网页站点 ➔ 加载 HomeFragment (网页嗅探)
     */
    private fun createHomeFragment(): Fragment {
        val activeSource = SourceStorage.getActiveSource(this)
        val isWebSource = activeSource?.isWebApi == true || (activeSource?.type == SourceModel.TYPE_WEB)

        return if (isWebSource) {
            HomeFragment()
        } else {
            NativeHomeFragment()
        }
    }

    private fun switchFragment(itemId: Int) {
        val fragment: Fragment = when (itemId) {
            R.id.nav_home -> createHomeFragment()
            R.id.nav_history -> HistoryFragment()
            R.id.nav_download -> DownloadFragment()
            R.id.nav_setting -> SettingFragment()
            else -> createHomeFragment()
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    /**
     * 公开刷新入口：供 SettingFragment 或 SourceSelectDialog 切源后刷新首页
     */
    fun reloadHomeFragment() {
        if (currentTabId == R.id.nav_home) {
            switchFragment(R.id.nav_home)
        }
    }
}