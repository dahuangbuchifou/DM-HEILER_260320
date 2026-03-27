// ============================================================================
// 📅 最新修复：2026-03-27 20:30
// 🔧 修复内容：
//   - 添加 audienceName 字段（用于自动选择观演人）
//   - 添加 selectedPrice 字段（用于步骤 2 选择票档）
//   - 主界面添加版本更新时间显示
//   - 修复任务保存功能（数据库写入）
//   - 修复任务列表显示（从数据库加载）
//   - 实现抢票流程框架（打开大麦 + 搜索演出）
//   - 实现图像识别抢票方案（截屏 +OCR+ 自动点击）
//   - 修复编译错误（DEFAULT_DISPLAY、await()、类型不匹配）
// 📝 说明：抢票任务数据模型 - Room 数据库实体
// ⚠️ 注意：Room 数据库需要迁移（version++）
// ============================================================================

package com.damaihelper.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 抢票任务数据模型
 */
@Entity(tableName = "ticket_tasks")
data class TicketTask(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // 任务名称
    val name: String = "",

    // 演出关键词（用于搜索）
    val concertKeyword: String,

    // 抢票日期（格式：2025-11-20）
    val grabDate: String,

    // 抢票时间戳（毫秒）- ✅ 修复：改为 Long 类型
    val grabTime: Long,

    // 票价关键词（如："380 元"）
    val ticketPriceKeyword: String,

    // 票数
    val count: Int = 1,

    // 观演人姓名（逗号分隔，如："张三，李四"）
    val viewerNames: String,

    // 观演人姓名（用于自动选择，单个姓名）
    val audienceName: String = "",

    // 选中的票价（用于步骤 2 选择票档）
    val selectedPrice: String = "",

    // 任务状态（0: 未开始，1: 进行中，2: 已完成，3: 失败）
    val status: String = "空闲",

    // 创建时间
    val createTime: Long = System.currentTimeMillis(),
    val quantity: Int = 1,  // ✅ 添加这个

    // 备注
    val remark: String = "备注"


)
