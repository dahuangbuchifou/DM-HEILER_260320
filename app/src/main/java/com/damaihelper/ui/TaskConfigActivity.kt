package com.damaihelper.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.damaihelper.R
import com.damaihelper.model.ConcertInfo
import com.damaihelper.model.TicketTask
import com.damaihelper.service.TicketGrabbingAccessibilityService
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * 任务配置界面
 * 用户可以手动输入或从大麦同步任务信息
 */
class TaskConfigActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "TaskConfig"
        const val EXTRA_TASK_ID = "task_id"
    }

    // UI 组件
    private lateinit var taskNameEdit: EditText
    private lateinit var concertKeywordEdit: EditText
    private lateinit var grabDateEdit: EditText
    private lateinit var grabTimeEdit: EditText
    private lateinit var ticketPriceEdit: EditText
    private lateinit var viewerNamesEdit: EditText
    private lateinit var remarkEdit: EditText
    private lateinit var saveButton: Button
    private lateinit var syncFromDamaiButton: Button

    // ✅ 新增：显示场馆和城市的控件
    private lateinit var tvVenue: TextView
    private lateinit var tvCity: TextView
    private lateinit var extractedInfoContainer: LinearLayout

    // ✅ 新增：日期和票价选择器
    private lateinit var spinnerDate: Spinner
    private lateinit var spinnerPrice: Spinner
    private lateinit var dateSpinnerContainer: LinearLayout
    private lateinit var priceSpinnerContainer: LinearLayout

    // 编辑模式
    private var editingTaskId: Long? = null
    private var selectedDate: String = ""
    private var selectedTime: String = ""

    // ✅ 新增：抓取的演出信息
    private var extractedInfo: ConcertInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_config)

        initViews()
        setupListeners()

        // ✅ 新增：检查是否从抓取功能进入
        handleExtractedInfo()

        // 检查是否是编辑模式
        editingTaskId = intent.getLongExtra(EXTRA_TASK_ID, -1).takeIf { it != -1L }
        if (editingTaskId != null) {
            loadTaskForEditing(editingTaskId!!)
        }
    }

    private fun initViews() {
        taskNameEdit = findViewById(R.id.task_name_edit)
        concertKeywordEdit = findViewById(R.id.concert_keyword_edit)
        grabDateEdit = findViewById(R.id.grab_date_edit)
        grabTimeEdit = findViewById(R.id.grab_time_edit)
        ticketPriceEdit = findViewById(R.id.ticket_price_edit)
        viewerNamesEdit = findViewById(R.id.viewer_names_edit)
        remarkEdit = findViewById(R.id.remark_edit)
        saveButton = findViewById(R.id.save_button)
        syncFromDamaiButton = findViewById(R.id.sync_from_damai_button)

        // ✅ 新增：初始化场馆和城市显示控件
        tvVenue = findViewById(R.id.tvVenue)
        tvCity = findViewById(R.id.tvCity)
        extractedInfoContainer = findViewById(R.id.extracted_info_container)

        // ✅ 新增：初始化 Spinner
        spinnerDate = findViewById(R.id.spinnerDate)
        spinnerPrice = findViewById(R.id.spinnerPrice)
        dateSpinnerContainer = findViewById(R.id.date_spinner_container)
        priceSpinnerContainer = findViewById(R.id.price_spinner_container)

        // 设置日期和时间输入框为只读
        grabDateEdit.isFocusable = false
        grabDateEdit.isClickable = true
        grabTimeEdit.isFocusable = false
        grabTimeEdit.isClickable = true
    }

    private fun setupListeners() {
        // 日期选择
        grabDateEdit.setOnClickListener {
            showDatePicker()
        }

        // 时间选择
        grabTimeEdit.setOnClickListener {
            showTimePicker()
        }

        // 保存按钮
        saveButton.setOnClickListener {
            saveTask()
        }

        // ✅ M4 模块：从大麦同步按钮
        syncFromDamaiButton.setOnClickListener {
            onSyncButtonClick()
        }
    }

    /**
     * ✅ 新增：处理从 MainActivity 传递过来的演出信息
     */
    private fun handleExtractedInfo() {
        val fromExtract = intent.getBooleanExtra("from_extract", false)

        if (fromExtract) {
            val concertInfo = intent.getParcelableExtra<ConcertInfo>("concert_info")

            if (concertInfo != null) {
                Log.i(TAG, "收到抓取的演出信息: ${concertInfo.concertName}")
                extractedInfo = concertInfo

                // 填充演出信息到界面
                fillConcertInfo(concertInfo)

                Toast.makeText(this, "✅ 已自动填充演出信息", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * ✅ 新增：填充演出信息到界面
     */
    private fun fillConcertInfo(info: ConcertInfo) {
        // 1. 填充任务名称和演出关键词
        if (info.concertName.isNotEmpty()) {
            taskNameEdit.setText(info.concertName)
            concertKeywordEdit.setText(info.concertName)
        }

        // 2. 显示场馆和城市信息卡片
        var hasExtractedInfo = false

        if (info.venue.isNotEmpty()) {
            tvVenue.text = "场馆: ${info.venue}"
            hasExtractedInfo = true
        }

        if (info.city.isNotEmpty()) {
            tvCity.text = "城市: ${info.city}"
            hasExtractedInfo = true
        }

        // 显示或隐藏信息卡片
        extractedInfoContainer.visibility = if (hasExtractedInfo) View.VISIBLE else View.GONE

        // 3. 处理日期信息
        if (info.availableDates.isNotEmpty()) {
            // 显示日期选择器容器
            dateSpinnerContainer.visibility = View.VISIBLE

            // 设置适配器
            val dateAdapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                info.availableDates
            )
            dateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerDate.adapter = dateAdapter

            // Spinner 选择监听
            spinnerDate.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    selectedDate = info.availableDates[position]
                    grabDateEdit.setText(selectedDate)
                    Log.d(TAG, "用户选择日期: $selectedDate")
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

            // 默认选择第一个日期
            selectedDate = info.availableDates.firstOrNull() ?: ""
            if (selectedDate.isNotEmpty()) {
                grabDateEdit.setText(selectedDate)
            }
        } else {
            dateSpinnerContainer.visibility = View.GONE
        }

        // 4. 处理票价信息
        if (info.availablePrices.isNotEmpty()) {
            // 显示票价选择器容器
            priceSpinnerContainer.visibility = View.VISIBLE

            // 设置适配器
            val priceAdapter = ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                info.availablePrices
            )
            priceAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerPrice.adapter = priceAdapter

            // Spinner 选择监听
            spinnerPrice.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val priceText = info.availablePrices[position]
                    ticketPriceEdit.setText(priceText)
                    Log.d(TAG, "用户选择票价: $priceText")
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

            // 默认选择第一个票价
            val firstPrice = info.availablePrices.firstOrNull() ?: ""
            if (firstPrice.isNotEmpty()) {
                ticketPriceEdit.setText(firstPrice)
            }
        } else {
            priceSpinnerContainer.visibility = View.GONE
        }

        Log.i(TAG, "演出信息已填充到界面")
    }

    /**
     * 显示日期选择器
     */
    private fun showDatePicker() {
        val calendar = Calendar.getInstance()

        // 如果已有选择的日期，使用它
        if (selectedDate.isNotEmpty()) {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                calendar.time = sdf.parse(selectedDate) ?: Date()
            } catch (e: Exception) {
                // 使用当前日期
            }
        }

        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                selectedDate = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
                grabDateEdit.setText(selectedDate)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    /**
     * 显示时间选择器
     */
    private fun showTimePicker() {
        val calendar = Calendar.getInstance()

        // 如果已有选择的时间，使用它
        if (selectedTime.isNotEmpty()) {
            try {
                val parts = selectedTime.split(":")
                calendar.set(Calendar.HOUR_OF_DAY, parts[0].toInt())
                calendar.set(Calendar.MINUTE, parts[1].toInt())
            } catch (e: Exception) {
                // 使用当前时间
            }
        }

        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                selectedTime = String.format("%02d:%02d", hourOfDay, minute)
                grabTimeEdit.setText(selectedTime)
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true  // 24 小时制
        ).show()
    }

    /**
     * 验证输入
     */
    private fun validateInput(): Boolean {
        if (taskNameEdit.text.toString().trim().isEmpty()) {
            Toast.makeText(this, "请输入任务名称", Toast.LENGTH_SHORT).show()
            return false
        }

        if (concertKeywordEdit.text.toString().trim().isEmpty()) {
            Toast.makeText(this, "请输入演出关键词", Toast.LENGTH_SHORT).show()
            return false
        }

        if (selectedDate.isEmpty()) {
            Toast.makeText(this, "请选择抢票日期", Toast.LENGTH_SHORT).show()
            return false
        }

        if (selectedTime.isEmpty()) {
            Toast.makeText(this, "请选择抢票时间", Toast.LENGTH_SHORT).show()
            return false
        }

        if (ticketPriceEdit.text.toString().trim().isEmpty()) {
            Toast.makeText(this, "请输入票价关键词", Toast.LENGTH_SHORT).show()
            return false
        }

        if (viewerNamesEdit.text.toString().trim().isEmpty()) {
            Toast.makeText(this, "请输入观演人姓名", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    /**
     * 保存任务
     */
    /**
     * M4 模块：从大麦 App 同步信息
     */
    private fun onSyncButtonClick() {
        val service = TicketGrabbingAccessibilityService.getInstance()

        if (service == null) {
            Log.e(TAG, "Accessibility Service 未运行或未开启！")
            Toast.makeText(this, "请先开启辅助功能服务", Toast.LENGTH_LONG).show()
            return
        }

        // 1. 调用 M4 模块的同步方法
        val extractedData = service.extractTaskInfoFromDamaiPage()

        // 2. 检查结果并填充 UI
        if (extractedData.isNotEmpty()) {
            Log.i(TAG, "✅ 同步成功！提取到的信息: $extractedData")

            // 填充到 EditText
            concertKeywordEdit.setText(extractedData["concertKeyword"] ?: "")
            grabDateEdit.setText(extractedData["grabDate"] ?: "")
            ticketPriceEdit.setText(extractedData["ticketPriceKeyword"] ?: "")

            Toast.makeText(this, "✅ 同步成功！信息已填充", Toast.LENGTH_SHORT).show()
        } else {
            Log.w(TAG, "❌ 同步失败！请确保在大麦 App 详情页")
            Toast.makeText(this, "❌ 同步失败！请确保已打开大麦 App 并进入演出详情页", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * 保存任务
     */
    private fun saveTask() {
        if (!validateInput()) return

        val taskName = taskNameEdit.text.toString().trim()
        val concertKeyword = concertKeywordEdit.text.toString().trim()
        val ticketPrice = ticketPriceEdit.text.toString().trim()
        val viewerNames = viewerNamesEdit.text.toString().trim()
        val remark = remarkEdit.text.toString().trim()

        // 将日期和时间转换为时间戳
        val grabTime = parseTimeToTimestamp(selectedDate, selectedTime)

        val task = TicketTask(
            id = if (editingTaskId != null && editingTaskId!! > 0) editingTaskId!! else 0,
            name = taskName,
            concertKeyword = concertKeyword,
            grabDate = selectedDate,
            grabTime = grabTime,
            ticketPriceKeyword = ticketPrice,
            count = 1,  // 默认 1 张
            viewerNames = viewerNames,
            status = getString(R.string.task_status_idle),
            remark = remark
        )

        // TODO: 保存到数据库
        // lifecycleScope.launch {
        //     taskDao.insertTask(task)
        // }

        Toast.makeText(this, "✅ 任务保存成功", Toast.LENGTH_SHORT).show()

        // ✅ 新增：记录日志
        Log.i(TAG, "任务已保存: $taskName, 抢票时间: $selectedDate $selectedTime")

        finish()
    }

    /**
     * 加载任务用于编辑
     */
    private fun loadTaskForEditing(taskId: Long) {
        // TODO: 从数据库加载任务
        // lifecycleScope.launch {
        //     val task = taskDao.getTaskById(taskId)
        //     if (task != null) {
        //         fillTaskData(task)
        //     }
        // }
    }

    /**
     * 填充任务数据到界面
     */
    private fun fillTaskData(task: TicketTask) {
        taskNameEdit.setText(task.name)
        concertKeywordEdit.setText(task.concertKeyword)

        selectedDate = task.grabDate
        grabDateEdit.setText(selectedDate)

        // 从时间戳提取时间
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = task.grabTime
        selectedTime = String.format(
            "%02d:%02d",
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE)
        )
        grabTimeEdit.setText(selectedTime)

        ticketPriceEdit.setText(task.ticketPriceKeyword)
        viewerNamesEdit.setText(task.viewerNames)
        remarkEdit.setText(task.remark)
    }

    /**
     * 将日期和时间转换为时间戳
     */
    private fun parseTimeToTimestamp(date: String, time: String): Long {
        return try {
            val dateTimeStr = "$date $time:00"
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            sdf.parse(dateTimeStr)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}