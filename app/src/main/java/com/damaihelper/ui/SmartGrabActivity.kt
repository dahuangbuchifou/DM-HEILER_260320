// ============================================================================
// 📅 创建日期：2026-03-30 22:30
// 📅 最新修复：2026-03-31 10:15
// 🔧 修复内容：
//   1. 修复删除按钮无效问题（连接 onDelete 到实际删除函数）
//   2. 增强智能抢票功能（创建任务后自动跳转大麦并启动搜索）
//   3. 实现完整抢票流程（搜索→选座→观众→提交）
//  说明：只需填写 3 项（歌手、日期、价位），其余自动完成
//  版本：v2.2.2
// ============================================================================

package com.damaihelper.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.damaihelper.R
import com.damaihelper.model.Audience
import com.damaihelper.model.TaskDatabase
import com.damaihelper.model.TicketTask
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SmartGrabActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SmartGrabActivity"
    }

    // UI 组件
    private lateinit var etKeyword: EditText
    private lateinit var etDate: EditText
    private lateinit var etPrice: EditText
    private lateinit var spinnerAudience: Spinner
    private lateinit var btnSave: Button
    private lateinit var tvAdvancedOptions: TextView
    private lateinit var advancedOptionsContainer: LinearLayout
    private lateinit var etFallbackPrices: EditText
    private lateinit var rgGrabMode: RadioGroup
    private lateinit var rbNormal: RadioButton
    private lateinit var rbSnap: RadioButton

    // 数据
    private var audienceList = listOf<Audience>()
    private var selectedAudienceId: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_smart_grab)

        // 初始化 UI 组件
        initViews()

        // 加载观众列表
        loadAudiences()

        // 设置监听器
        setupListeners()
    }

    private fun initViews() {
        etKeyword = findViewById(R.id.etKeyword)
        etDate = findViewById(R.id.etDate)
        etPrice = findViewById(R.id.etPrice)
        spinnerAudience = findViewById(R.id.spinnerAudience)
        btnSave = findViewById(R.id.btnSave)
        tvAdvancedOptions = findViewById(R.id.tvAdvancedOptions)
        advancedOptionsContainer = findViewById(R.id.advancedOptionsContainer)
        etFallbackPrices = findViewById(R.id.etFallbackPrices)
        rgGrabMode = findViewById(R.id.rgGrabMode)
        rbNormal = findViewById(R.id.rbNormal)
        rbSnap = findViewById(R.id.rbSnap)
    }

    private fun loadAudiences() {
        lifecycleScope.launch {
            try {
                val db = TaskDatabase.getDatabase(this@SmartGrabActivity)
                audienceList = db.audienceDao().getAllAudiences().first()

                if (audienceList.isEmpty()) {
                    // 没有观众，提示添加
                    showNoAudienceDialog()
                    return@launch
                }

                // 设置 Spinner 适配器
                val audienceNames = audienceList.map { it.name }.toTypedArray()
                val adapter = ArrayAdapter(
                    this@SmartGrabActivity,
                    android.R.layout.simple_spinner_item,
                    audienceNames
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerAudience.adapter = adapter

                // 选择第一个观众
                selectedAudienceId = audienceList[0].id

                // 设置监听器
                spinnerAudience.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        parent: AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long
                    ) {
                        selectedAudienceId = audienceList[position].id
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {}
                }

            } catch (e: Exception) {
                Toast.makeText(
                    this@SmartGrabActivity,
                    "加载观众失败：${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showNoAudienceDialog() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ 提示")
            .setMessage("请先添加观众信息！\n\n路径：主页 → 👤 观众管理 → ➕ 添加观众")
            .setPositiveButton("去添加") { _, _ ->
                startActivity(android.content.Intent(this, AudienceManagerActivity::class.java))
                finish()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun setupListeners() {
        // 高级选项展开/折叠
        tvAdvancedOptions.setOnClickListener {
            if (advancedOptionsContainer.visibility == View.VISIBLE) {
                advancedOptionsContainer.visibility = View.GONE
                tvAdvancedOptions.text = "▼ 高级选项（可选）"
            } else {
                advancedOptionsContainer.visibility = View.VISIBLE
                tvAdvancedOptions.text = "▲ 高级选项（已展开）"
            }
        }

        // 保存按钮
        btnSave.setOnClickListener {
            validateAndSaveTask()
        }
    }

    private fun validateAndSaveTask() {
        // 验证必填字段
        val keyword = etKeyword.text.toString().trim()
        val date = etDate.text.toString().trim()
        val price = etPrice.text.toString().trim()

        if (keyword.isEmpty()) {
            etKeyword.error = "请填写歌手/演唱会"
            etKeyword.requestFocus()
            return
        }

        if (date.isEmpty()) {
            etDate.error = "请填写抢票日期"
            etDate.requestFocus()
            return
        }

        // 验证日期格式（YYYY-MM-DD）
        if (!date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
            etDate.error = "日期格式：2026-05-15"
            etDate.requestFocus()
            return
        }

        if (price.isEmpty()) {
            etPrice.error = "请填写预期价位"
            etPrice.requestFocus()
            return
        }

        // 获取观众信息
        val selectedAudience = audienceList.find { it.id == selectedAudienceId }
        if (selectedAudience == null) {
            Toast.makeText(this, "请选择观众", Toast.LENGTH_SHORT).show()
            return
        }

        // 获取高级选项
        val fallbackPrices = etFallbackPrices.text.toString().trim()
        val grabMode = if (rbSnap.isChecked) "snap" else "normal"

        // 生成任务名称
        val taskName = "$keyword 抢票任务"

        // 保存任务
        saveTask(taskName, keyword, date, price, selectedAudience, fallbackPrices, grabMode)
    }

    private fun saveTask(
        taskName: String,
        keyword: String,
        date: String,
        price: String,
        audience: Audience,
        fallbackPrices: String,
        grabMode: String
    ) {
        lifecycleScope.launch {
            try {
                val db = TaskDatabase.getDatabase(this@SmartGrabActivity)

                // 计算抢票时间戳（假设日期当天的 12:00）
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                val grabTime = try {
                    sdf.parse("$date 12:00")?.time ?: System.currentTimeMillis()
                } catch (e: Exception) {
                    System.currentTimeMillis()
                }

                val task = TicketTask(
                    name = taskName,
                    concertKeyword = keyword,
                    grabDate = date,
                    grabTime = grabTime,
                    sessionId = "", // 自动匹配
                    priceTiers = if (fallbackPrices.isNotEmpty()) fallbackPrices else price,
                    ticketPriceKeyword = price,
                    selectedPrice = price,
                    count = 1,
                    viewerNames = audience.name,
                    audienceName = audience.name,
                    audienceIndex = 1,
                    grabMode = grabMode,
                    status = "空闲",
                    quantity = 1,
                    remark = "智能抢票模式创建"
                )

                val taskId = db.taskDao().insertTask(task).toInt()

                Toast.makeText(this@SmartGrabActivity, "✅ 任务创建成功！即将自动跳转到大麦...", Toast.LENGTH_LONG).show()

                //  延迟后启动抢票服务并打开大麦
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        // 启动抢票服务
                        val service = TicketGrabbingAccessibilityService.getInstance()
                        if (service != null) {
                            // 自动开始抢票流程
                            service.startGrabbing(task)
                            
                            // 打开大麦 App
                            val damaiIntent = Intent().apply {
                                setClassName("cn.damai", "cn.damai.main.activity.MainActivity")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            
                            // 尝试启动大麦
                            try {
                                startActivity(damaiIntent)
                                Toast.makeText(this@SmartGrabActivity, "🎫 已启动大麦 App，正在自动搜索...", Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                // 如果直接启动失败，尝试通用方式
                                val webIntent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                    data = android.net.Uri.parse("https://www.damai.cn/")
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                startActivity(webIntent)
                                Toast.makeText(this@SmartGrabActivity, "🌐 已打开大麦网页版，请手动操作", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(this@SmartGrabActivity, "️ 无障碍服务未启动，请手动开启", Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this@SmartGrabActivity, "️ 自动启动失败，请手动操作", Toast.LENGTH_LONG).show()
                    }
                }, 1000)

                // 返回主页
                finish()

            } catch (e: Exception) {
                Toast.makeText(
                    this@SmartGrabActivity,
                    "保存失败：${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
