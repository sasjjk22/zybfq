package com.tjjiangh.bofangqi.source

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.tjjiangh.bofangqi.R
import kotlinx.coroutines.launch

class SourceSelectDialog(
    private val onSourceChanged: () -> Unit
) : BottomSheetDialogFragment() {

    private lateinit var rvSources: RecyclerView
    private lateinit var btnTestSpeed: MaterialButton
    private lateinit var btnBatchToggle: MaterialButton
    private lateinit var btnAddSource: MaterialButton

    private lateinit var layoutNormalBar: LinearLayout
    private lateinit var layoutBatchBar: LinearLayout
    private lateinit var tvBatchCount: TextView
    private lateinit var btnSelectAll: MaterialButton
    private lateinit var btnConfirmDelete: MaterialButton
    private lateinit var btnCancelBatch: MaterialButton

    private lateinit var adapter: SourceAdapter
    private var sourcesList = mutableListOf<SourceModel>()

    private var isBatchMode = false
    private var isAllSelected = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_source_select, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupListeners()
        loadSources()
    }

    private fun initViews(view: View) {
        rvSources = view.findViewById(R.id.rvSources)
        btnTestSpeed = view.findViewById(R.id.btnTestSpeed)
        btnBatchToggle = view.findViewById(R.id.btnBatchToggle)
        btnAddSource = view.findViewById(R.id.btnAddSource)

        layoutNormalBar = view.findViewById(R.id.layoutNormalBar)
        layoutBatchBar = view.findViewById(R.id.layoutBatchBar)
        tvBatchCount = view.findViewById(R.id.tvBatchCount)
        btnSelectAll = view.findViewById(R.id.btnSelectAll)
        btnConfirmDelete = view.findViewById(R.id.btnConfirmDelete)
        btnCancelBatch = view.findViewById(R.id.btnCancelBatch)

        rvSources.layoutManager = LinearLayoutManager(requireContext())
        adapter = SourceAdapter(
            items = sourcesList,
            isBatchModeProvider = { isBatchMode },
            onSelect = { source ->
                if (!isBatchMode) {
                    SourceStorage.setActiveSource(requireContext(), source.id)
                    loadSources()
                    onSourceChanged()
                    dismiss()
                } else {
                    source.isBatchChecked = !source.isBatchChecked
                    updateBatchCountText()
                    adapter.notifyDataSetChanged()
                }
            },
            onDelete = { source ->
                SourceStorage.deleteSource(requireContext(), source.id)
                loadSources()
            }
        )
        rvSources.adapter = adapter
    }

    private fun setupListeners() {
        btnTestSpeed.setOnClickListener { runSpeedTest() }

        // 进入批量管理模式
        btnBatchToggle.setOnClickListener {
            setBatchMode(true)
        }

        // 退出批量管理模式
        btnCancelBatch.setOnClickListener {
            setBatchMode(false)
        }

        // 全选 / 取消全选
        btnSelectAll.setOnClickListener {
            isAllSelected = !isAllSelected
            for (item in sourcesList) {
                item.isBatchChecked = isAllSelected
            }
            updateBatchCountText()
            adapter.notifyDataSetChanged()
        }

        // 确认批量删除选中的源
        btnConfirmDelete.setOnClickListener {
            val selectedIds = sourcesList.filter { it.isBatchChecked }.map { it.id }.toSet()
            if (selectedIds.isEmpty()) {
                Toast.makeText(context, "请先勾选要删除的源", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            AlertDialog.Builder(requireContext())
                .setTitle("确认批量删除")
                .setMessage("确定要物理删除选中的 ${selectedIds.size} 个源站点吗？")
                .setPositiveButton("删除选中") { _, _ ->
                    SourceStorage.deleteBatchSources(requireContext(), selectedIds)
                    setBatchMode(false)
                    loadSources()
                    onSourceChanged()
                    Toast.makeText(context, "已删除 ${selectedIds.size} 个源！", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show()
        }

        btnAddSource.setOnClickListener { showAddSourceDialog() }
    }

    private fun setBatchMode(enabled: Boolean) {
        isBatchMode = enabled
        layoutNormalBar.visibility = if (enabled) View.GONE else View.VISIBLE
        layoutBatchBar.visibility = if (enabled) View.VISIBLE else View.GONE
        if (!enabled) {
            for (item in sourcesList) item.isBatchChecked = false
        }
        updateBatchCountText()
        adapter.notifyDataSetChanged()
    }

    private fun updateBatchCountText() {
        val count = sourcesList.count { it.isBatchChecked }
        tvBatchCount.text = if (count > 0) "已勾选 $count 个源" else "勾选要删除的源"
        btnConfirmDelete.text = if (count > 0) "🗑️ 删除($count)" else "🗑️ 删除选中"
    }

    private fun loadSources() {
        sourcesList.clear()
        sourcesList.addAll(SourceStorage.getAllSources(requireContext()))
        adapter.notifyDataSetChanged()
    }

    private fun runSpeedTest() {
        Toast.makeText(context, "正在对所有站点测速中...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            for (source in sourcesList) {
                val speed = SourceSpeedTester.testSpeed(source.apiUrl)
                source.speedMs = speed
            }
            SourceStorage.saveSources(requireContext(), sourcesList)
            adapter.notifyDataSetChanged()
        }
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

    class SourceAdapter(
        private val items: List<SourceModel>,
        private val isBatchModeProvider: () -> Boolean,
        private val onSelect: (SourceModel) -> Unit,
        private val onDelete: (SourceModel) -> Unit
    ) : RecyclerView.Adapter<SourceAdapter.SourceViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SourceViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_source_card, parent, false)
            return SourceViewHolder(view)
        }

        override fun onBindViewHolder(holder: SourceViewHolder, position: Int) {
            holder.bind(items[position], isBatchModeProvider(), onSelect, onDelete)
        }

        override fun getItemCount(): Int = items.size

        class SourceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val cardSource: MaterialCardView = itemView.findViewById(R.id.cardSource)
            private val cbBatch: CheckBox = itemView.findViewById(R.id.cbBatch)
            private val rbSelected: RadioButton = itemView.findViewById(R.id.rbSelected)
            private val tvSourceName: TextView = itemView.findViewById(R.id.tvSourceName)
            private val tvSourceUrl: TextView = itemView.findViewById(R.id.tvSourceUrl)
            private val tvSpeedBadge: TextView = itemView.findViewById(R.id.tvSpeedBadge)
            private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)

            fun bind(
                item: SourceModel,
                isBatchMode: Boolean,
                onSelect: (SourceModel) -> Unit,
                onDelete: (SourceModel) -> Unit
            ) {
                tvSourceName.text = item.name
                tvSourceUrl.text = item.apiUrl

                // 批量管理模式 UI 处理
                if (isBatchMode) {
                    cbBatch.visibility = View.VISIBLE
                    rbSelected.visibility = View.GONE
                    btnDelete.visibility = View.GONE
                    cbBatch.isChecked = item.isBatchChecked
                } else {
                    cbBatch.visibility = View.GONE
                    rbSelected.visibility = View.VISIBLE
                    btnDelete.visibility = View.VISIBLE
                    rbSelected.isChecked = item.isSelected
                }

                if (item.isSelected && !isBatchMode) {
                    cardSource.strokeColor = 0xFFFF6200.toInt()
                    cardSource.setCardBackgroundColor(0xFFFFF8F3.toInt())
                } else if (item.isBatchChecked && isBatchMode) {
                    cardSource.strokeColor = 0xFFE53935.toInt()
                    cardSource.setCardBackgroundColor(0xFFFFEBEE.toInt())
                } else {
                    cardSource.strokeColor = 0xFFE0E0E0.toInt()
                    cardSource.setCardBackgroundColor(0xFFFFFFFF.toInt())
                }

                when {
                    item.speedMs < 0 -> {
                        tvSpeedBadge.text = "超时"
                        tvSpeedBadge.setTextColor(0xFFE53935.toInt())
                    }
                    item.speedMs < 300 -> {
                        tvSpeedBadge.text = "${item.speedMs}ms"
                        tvSpeedBadge.setTextColor(0xFF4CAF50.toInt())
                    }
                    else -> {
                        tvSpeedBadge.text = "${item.speedMs}ms"
                        tvSpeedBadge.setTextColor(0xFFFF9800.toInt())
                    }
                }

                cardSource.setOnClickListener { onSelect(item) }
                btnDelete.setOnClickListener { onDelete(item) }
            }
        }
    }
}