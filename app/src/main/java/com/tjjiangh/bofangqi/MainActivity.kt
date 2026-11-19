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

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DARK_MODE, false)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }

        setContentView(R.layout.activity_main)
        bottomNav = findViewById(R.id.bottom_navigation)

        currentTabId = savedInstanceState?.getInt(STATE_SELECTED_TAB, R.id.nav_home)
            ?: R.id.nav_home

        bottomNav.setOnItemSelectedListener { item ->
            if (item.itemId != currentTabId) {
                currentTabId = item.itemId
                showTab(item.itemId)
            }
            true
        }

        if (savedInstanceState == null) {
            showTab(currentTabId)
        } else {
            // FragmentManager restores the existing fragment. Updating selection here must not
            // replace it and discard its scroll position or in-progress state.
            bottomNav.selectedItemId = currentTabId
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_SELECTED_TAB, currentTabId)
        super.onSaveInstanceState(outState)
    }

    private fun createHomeFragment(): Fragment {
        val activeSource = SourceStorage.getActiveSource(this)
        val isWebSource = activeSource?.isWebApi == true || activeSource?.type == SourceModel.TYPE_WEB
        return if (isWebSource) HomeFragment() else NativeHomeFragment()
    }

    private fun showTab(itemId: Int) {
        val fragment = when (itemId) {
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

    /** Refresh the home screen after the active source changes. */
    fun reloadHomeFragment() {
        if (currentTabId == R.id.nav_home) {
            showTab(R.id.nav_home)
        }
    }

    private companion object {
        const val PREFS_NAME = "app_settings"
        const val KEY_DARK_MODE = "dark_mode"
        const val STATE_SELECTED_TAB = "selected_tab_id"
    }
}
