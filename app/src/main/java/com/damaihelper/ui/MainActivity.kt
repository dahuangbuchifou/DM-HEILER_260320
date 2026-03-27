// ============================================================================
// 📅 修复日期：2026-03-20
// 🔧 修复内容：增强广播接收器注销保护、优化错误处理
// 📝 说明：主界面 - 抢票任务管理和演出信息抓取入口
// ============================================================================

package com.damaihelper.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.damaihelper.R
import com.damaihelper.model.ConcertInfo
import com.damaihelper.core.ScreenCaptureService
import com.damaihelper.core.ScreenAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.damaihelper.model.TaskDatabase
import com.damaihelper.model.TicketTask
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.lifecycle.lifecycleScope
import com.damaihelper.service.ConcertInfoExtractor
import com.damaihelper.service.TicketGrabbingAccessibilityService
import com.damaihelper.utils.AccessibilityUtils
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var accessibilityStatusText: TextView
    private lateinit var versionUpdateTimeText: TextView
    private lateinit var taskRecyclerView: RecyclerView
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var addTaskButton: Button

    // ✅ 新增：抓取演出信息按钮
    private lateinit var extractConcertInfoButton: Button
    private lateinit var btnExtractFromPreSale: Button
    
    // 截屏和识别服务
    private var screenCapture: ScreenCaptureService? = null
    private var screenAnalyzer: ScreenAnalyzer? = null

    // ✅ 新增：广播接收器
    private lateinit var concertInfoReceiver: BroadcastReceiver

    private val currentTasks = mutableListOf<TicketTask>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        accessibilityStatusText = findViewById(R.id.accessibility_status_text)
        versionUpdateTimeText = findViewById(R.id.version_update_time_text)
        taskRecyclerView = findViewById(R.id.task_recycler_view)
        addTaskButton = findViewById(R.id.add_task_button)

        // ✅ 新增：初始化抓取按钮
        extractConcertInfoButton = findViewById(R.id.btnExtractConcertInfo)
        btnExtractFromPreSale = findViewById(R.id.btnExtractFromPreSale)
        
        // 初始化截屏和识别服务
        screenCapture = ScreenCaptureService(this)
        screenAnalyzer = ScreenAnalyzer()

        // ✅ 设置版本更新时间
        updateVersionTime()

        setupTaskList()
        setupListeners()

        // ✅ 新增：注册广播接收器
        setupConcertInfoReceiver()
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityStatus()
        loadTasks()
    }

    override fun onDestroy() {
        super.onDestroy()
        // ✅ 新增：注销广播接收器
        try {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(concertInfoReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "注销广播接收器失败", e)
        }
    }

    private fun setupTaskList() {
        taskAdapter = TaskAdapter(
            onEdit = { Toast.makeText(this, "编辑: ${it.name}", Toast.LENGTH_SHORT).show() },
            onDelete = { Toast.makeText(this, "删除: ${it.name}", Toast.LENGTH_SHORT).show() },
            onStartStop = { handleStartStopTask(it) }
        )
        taskRecyclerView.layoutManager = LinearLayoutManager(this)
        taskRecyclerView.adapter = taskAdapter
    }

    private fun handleStartStopTask(task: TicketTask) {
        if (!AccessibilityUtils.isServiceEnabled(this)) {
            Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }

        val service = TicketGrabbingAccessibilityService.getInstance()
        if (service == null) {
            Toast.makeText(this, "服务未启动，请重启应用", Toast.LENGTH_LONG).show()
            return
        }

        if (task.status == getString(R.string.task_status_running)) {
            service.stopGrabbing()
            updateTaskStatus(task, getString(R.string.task_status_idle))
            Toast.makeText(this, "已停止", Toast.LENGTH_SHORT).show()
        } else {
            service.startGrabbing(task)
            updateTaskStatus(task, getString(R.string.task_status_running))
            Toast.makeText(this, "开始抢票: ${task.name}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateTaskStatus(task: TicketTask, newStatus: String) {
        val updatedTask = task.copy(status = newStatus)
        val index = currentTasks.indexOfFirst { it.id == task.id }
        if (index != -1) {
            currentTasks[index] = updatedTask
            taskAdapter.submitList(currentTasks.toList())
            taskAdapter.notifyItemChanged(index)
        }
    }

    /**
     * 设置版本更新时间显示
     * 📅 与 TicketTask.kt 中的修复时间保持一致
     */
    private fun updateVersionTime() {
        // 格式：2026-03-27 20:45
        versionUpdateTimeText.text = "📅 版本更新时间：2026-03-27 22:15"
    }

    private fun setupListeners() {
        accessibilityStatusText.setOnClickListener {
            if (!AccessibilityUtils.isServiceEnabled(this)) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        addTaskButton.setOnClickListener {
            startActivity(Intent(this, TaskConfigActivity::class.java))
        }

        // ✅ 新增：抓取演出信息按钮监听
        extractConcertInfoButton.setOnClickListener {
            handleExtractConcertInfo()
        }
        
        // 从预售页抓取按钮监听
        btnExtractFromPreSale.setOnClickListener {
            handleExtractFromPreSalePage()
        }
    }

    private fun updateAccessibilityStatus() {
        if (AccessibilityUtils.isServiceEnabled(this)) {
            accessibilityStatusText.text = "辅助服务已开启 ✓"
            accessibilityStatusText.setTextColor(resources.getColor(R.color.status_success, theme))
        } else {
            accessibilityStatusText.text = getString(R.string.enable_accessibility_service)
            accessibilityStatusText.setTextColor(resources.getColor(R.color.status_failed, theme))
        }
    }

    private fun loadTasks() {
        // ✅ 从数据库加载真实任务
        lifecycleScope.launch {
            try {
                val db = TaskDatabase.getDatabase(this@MainActivity)
                val tasks = db.taskDao().getAllTasks()
                // tasks 是 Flow<List<TicketTask>>，使用 first() 取一次值
                val taskList = tasks.first()
                currentTasks.clear()
                currentTasks.addAll(taskList)
                taskAdapter.submitList(taskList)
                if (taskList.isNotEmpty()) {
                    Log.d(TAG, "加载任务成功，共 ${taskList.size} 个任务")
                }
            } catch (e: Exception) {
                Log.e(TAG, "加载任务失败", e)
                Toast.makeText(this@MainActivity, "加载任务失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ==================== ✅ 新增方法区域 ====================

    /**
     * 设置广播接收器
     */
    private fun setupConcertInfoReceiver() {
        concertInfoReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == ConcertInfoExtractor.ACTION_CONCERT_INFO_EXTRACTED) {

                    // 检查是否有错误
                    val errorMessage = intent.getStringExtra(ConcertInfoExtractor.EXTRA_ERROR_MESSAGE)
                    if (errorMessage != null) {
                        showError(errorMessage)
                        return
                    }

                    // 获取演出信息
                    val info = intent.getParcelableExtra<ConcertInfo>(
                        ConcertInfoExtractor.EXTRA_CONCERT_INFO
                    )

                    if (info != null) {
                        onConcertInfoReceived(info)
                    }
                }
            }
        }

        val filter = IntentFilter(ConcertInfoExtractor.ACTION_CONCERT_INFO_EXTRACTED)
        LocalBroadcastManager.getInstance(this).registerReceiver(concertInfoReceiver, filter)
        Log.d(TAG, "广播接收器已注册")
    }

    /**
     * 处理抓取演出信息按钮点击
     */
    private fun handleExtractConcertInfo() {
        // 检查无障碍服务是否开启
        if (!AccessibilityUtils.isServiceEnabled(this)) {
            Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }

        // 获取服务实例
        val service = TicketGrabbingAccessibilityService.getInstance()
        if (service == null) {
            Toast.makeText(this, "服务未启动，请重启应用", Toast.LENGTH_LONG).show()
            return
        }

        // 开始抓取
        Toast.makeText(this, "📥 开始抓取演出信息...", Toast.LENGTH_SHORT).show()
        Log.i(TAG, "用户点击抓取按钮，开始抓取演出信息")
        service.startExtractingConcertInfo()
    }

    /**
     * 接收到演出信息后的处理
     */
    private fun onConcertInfoReceived(info: ConcertInfo) {
        runOnUiThread {
            Log.i(TAG, "收到演出信息: ${info.concertName}")
            Toast.makeText(this, "✅ 演出信息抓取成功！", Toast.LENGTH_SHORT).show()

            // 显示信息到日志
            Log.i(TAG, "演出名称: ${info.concertName}")
            Log.i(TAG, "场馆: ${info.venue}")
            Log.i(TAG, "城市: ${info.city}")
            Log.i(TAG, "可选日期: ${info.availableDates.joinToString(", ")}")
            Log.i(TAG, "可选票价: ${info.availablePrices.joinToString(", ")}")

            // 保存到配置
            saveToTaskConfig(info)

            // 启动任务配置页面，传递抓取的信息
            startTaskConfigActivityWithInfo(info)
        }
    }

    /**
     * 启动任务配置页面并传递演出信息
     */
    private fun startTaskConfigActivityWithInfo(info: ConcertInfo) {
        val intent = Intent(this, TaskConfigActivity::class.java).apply {
            putExtra("concert_info", info)
            putExtra("from_extract", true)
        }
        startActivity(intent)
    }

    /**
     * 保存到任务配置
     */
    private fun saveToTaskConfig(info: ConcertInfo) {
        val prefs = getSharedPreferences("concert_config", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("last_concert_name", info.concertName)
            putString("last_venue", info.venue)
            putString("last_city", info.city)
            putLong("last_extract_time", info.extractTime)

            // 保存日期和票价列表（使用 JSON 或简单分隔符）
            putString("last_dates", info.availableDates.joinToString("|"))
            putString("last_prices", info.availablePrices.joinToString("|"))

            apply()
        }
        Log.i(TAG, "演出信息已保存到 SharedPreferences")
    }

    /**
     * 从预售页抓取信息
     */
    private fun handleExtractFromPreSalePage() {
        Toast.makeText(this, "📸 正在截屏识别...", Toast.LENGTH_SHORT).show()
        
        lifecycleScope.launch {
            try {
                // 请求截屏权限
                screenCapture?.requestPermission(this@MainActivity)
                // 注意：实际需要在 onActivityResult 中处理结果
                Toast.makeText(this@MainActivity, "⚠️ 请在授权后使用", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Log.e(TAG, "预售页抓取失败", e)
                Toast.makeText(this@MainActivity, "❌ 抓取失败：${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * 显示错误信息
     */
    private fun showError(message: String) {
        runOnUiThread {
            Toast.makeText(this, "❌ $message", Toast.LENGTH_LONG).show()
            Log.e(TAG, "抓取错误: $message")
        }
    }
}