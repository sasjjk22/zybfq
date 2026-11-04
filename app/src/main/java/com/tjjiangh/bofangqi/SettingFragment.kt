package com.tjjiangh.bofangqi

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.tjjiangh.bofangqi.source.SourceModel
import com.tjjiangh.bofangqi.source.SourceSelectDialog
import com.tjjiangh.bofangqi.source.SourceSpeedTester
import com.tjjiangh.bofangqi.source.SourceStorage
import kotlinx.coroutines.launch

class SettingFragment : Fragment() {

    private lateinit var switchDarkMode: SwitchMaterial
    private lateinit var rvSettingSources: RecyclerView
    private lateinit var btnSettingBatchToggle: MaterialButton
    private lateinit var btnSettingAddSource: MaterialButton

    private lateinit var layoutSettingNormalBar: LinearLayout
    private lateinit var layoutSettingBatchBar: LinearLayout
    private lateinit var tvSettingBatchCount: TextView
    private lateinit var btnSettingSelectAll: MaterialButton
    private lateinit var btnSettingConfirmDelete: MaterialButton
    private lateinit var btnSettingCancelBatch: MaterialButton

    private lateinit var adapter: SourceSelectDialog.SourceAdapter
    private var sourcesList = mutableListOf<SourceModel>()

    private var isBatchMode = false
    private var isAllSelected = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_setting, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupListeners()
        loadSources()
    }

    private fun initViews(view: View) {
        switchDarkMode = view.findViewById(R.id.switchDarkMode)
        rvSettingSources = view.findViewById(R.id.rvSettingSources)
        btnSettingBatchToggle = view.findViewById(R.id.btnSettingBatchToggle)
        btnSettingAddSource = view.findViewById(R.id.btnSettingAddSource)

        layoutSettingNormalBar = view.findViewById(R.id.layoutSettingNormalBar)
        layoutSettingBatchBar = view.findViewById(R.id.layoutSettingBatchBar)
        tvSettingBatchCount = view.findViewById(R.id.tvSettingBatchCount)
        btnSettingSelectAll = view.findViewById(R.id.btnSettingSelectAll)
        btnSettingConfirmDelete = view.findViewById(R.id.btnSettingConfirmDelete)
        btnSettingCancelBatch = view.findViewById(R.id.btnSettingCancelBatch)

        // 初始化深色模式状态
        val prefs = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean("dark_mode", false)
        switchDarkMode.isChecked = isDarkMode

        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dark_mode", isChecked).apply()
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }

        rvSettingSources.layoutManager = LinearLayoutManager(requireContext())
        adapter = SourceSelectDialog.SourceAdapter(
            items = sourcesList,
            isBatchModeProvider = { isBatchMode },
            onSelect = { source ->
                if (!isBatchMode) {
                    SourceStorage.setActiveSource(requireContext(), source.id)
                    loadSources()
                    (activity as? MainActivity)?.reloadHomeFragment()
                    Toast.makeText(context, "已切换主源至: ${source.name}", Toast.LENGTH_SHORT).show()
                } else {
                    source.isBatchChecked = !source.isBatchChecked
                    updateBatchCountText()
                    adapter.notifyDataSetChanged()
                }
            },
            onDelete = { source ->
                SourceStorage.deleteSource(requireContext(), source.id)
                loadSources()
                (activity as? MainActivity)?.reloadHomeFragment()
            }
        )
        rvSettingSources.adapter = adapter
    }

    private fun setupListeners() {
        btnSettingBatchToggle.setOnClickListener { setBatchMode(true) }
        btnSettingCancelBatch.setOnClickListener { setBatchMode(false) }

        btnSettingSelectAll.setOnClickListener {
            isAllSelected = !isAllSelected
            for (item in sourcesList) {
                item.isBatchChecked = isAllSelected
            }
            updateBatchCountText()
            adapter.notifyDataSetChanged()
        }

        btnSettingConfirmDelete.setOnClickListener {
            val selectedIds = sourcesList.filter { it.isBatchChecked }.map { it.id }.toSet()
            if (selectedIds.isEmpty()) {
                Toast.makeText(context, "请先勾选要删除的源", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            AlertDialog.Builder(requireContext())
                .setTitle("确认批量删除")
                .setMessage("确定要删除选中的 ${selectedIds.size} 个源站点吗？")
                .setPositiveButton("删除选中") { _, _ ->
                    SourceStorage.deleteBatchSources(requireContext(), selectedIds)
                    setBatchMode(false)
                    loadSources()
                    (activity as? MainActivity)?.reloadHomeFragment()
                    Toast.makeText(context, "已批量删除 ${selectedIds.size} 个源！", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show()
        }

        btnSettingAddSource.setOnClickListener { showAddSourceDialog() }
    }

    private fun setBatchMode(enabled: Boolean) {
        isBatchMode = enabled
        layoutSettingNormalBar.visibility = if (enabled) View.GONE else View.VISIBLE
        layoutSettingBatchBar.visibility = if (enabled) View.VISIBLE else View.GONE
        if (!enabled) {
            for (item in sourcesList) item.isBatchChecked = false
        }
        updateBatchCountText()
        adapter.notifyDataSetChanged()
    }

    private fun updateBatchCountText() {
        val count = sourcesList.count { it.isBatchChecked }
        tvSettingBatchCount.text = if (count > 0) "已勾选 $count 个源" else "勾选要删除的源"
        btnSettingConfirmDelete.text = if (count > 0) "🗑️ 删除($count)" else "🗑️ 删除选中"
    }

    private fun loadSources() {
        sourcesList.clear()
        sourcesList.addAll(SourceStorage.getAllSources(requireContext()))
        adapter.notifyDataSetChanged()
    }

    private fun showAddSourceDialog() {
        val input = EditText(requireContext()).apply {
            hint = "粘贴 苹果CMS 接口 或 TvBox json 订阅链接..."
            setPadding(32, 32, 32, 32)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("添加影视源 / 导入订阅")
            .setView(input)
            .setPositiveButton("导入添加") { _, _ ->
                val urlText = input.text.toString().trim()
                if (urlText.isNotBlank()) {
                    if (urlText.endsWith(".json") || urlText.contains("json")) {
                        lifecycleScope.launch {
                            Toast.makeText(context, "正在解析 TvBox 订阅源...", Toast.LENGTH_SHORT).show()
                            val subSources = SourceSpeedTester.parseTvBoxSubscription(urlText)
                            if (subSources.isNotEmpty()) {
                                SourceStorage.addSources(requireContext(), subSources)
                                loadSources()
                                Toast.makeText(context, "成功导入 ${subSources.size} 个源站点！", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "订阅源解析失败，请检查链接", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        val newSource = SourceModel(
                            name = "自定义站点",
                            url = urlText,
                            apiUrl = urlText
                        )
                        SourceStorage.addSource(requireContext(), newSource)
                        loadSources()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}