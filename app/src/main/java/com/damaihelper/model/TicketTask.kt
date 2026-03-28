// ============================================================================
// 📅 最新修复：2026-03-28 21:45
// 🔧 修复内容：
//   - 🆕 添加 sessionId 字段（指定场次 ID）
//   - 🆕 添加 priceTiers 字段（多票档备选，逗号分隔）
//   - 🆕 添加 audienceIndex 字段（观影人索引，默认 1）
//   - 🆕 添加 grabMode 字段（抢票模式：normal/snap）
//   - 修复协程作用域错误（return vs return@launch）
//   - 删除 preparePhase 中重复插入的代码（28 行）
//   - extractFromDetailPage 添加非空断言（rootNode!!）
//   - 分屏模式下大麦 App 检测优化（findDamaiRootNode）
//   - 主界面添加版本更新时间显示
//   - 修复任务保存功能（数据库写入）
//   - 修复任务列表显示（从数据库加载）
//   - 实现抢票流程框架（打开大麦 + 搜索演出）
//   - 实现图像识别抢票方案（截屏 +OCR+ 自动点击）
//   - 修复编译错误（DEFAULT_DISPLAY、await()、类型不匹配）
//   - 支持上下分屏检测
// 📝 说明：抢票任务数据模型 - Room 数据库实体
// ⚠️ 注意：Room 数据库需要迁移（version++）
// ============================================================================

package com.damaihelper.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 抢票任务数据模型
 * 
 * 支持预约 + 秒杀模式：
 * - 提前配置场次、票档、观影人
 * - 开票瞬间自动完成：立即预定 → 选票档 → 确认观影人 → 提交
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

    // 抢票时间戳（毫秒）
    val grabTime: Long,

    // 🆕 场次 ID（如："2026-04-18-19:00"，用于指定场次）
    val sessionId: String = "",

    // 🆕 多票档备选（逗号分隔，从高到低，如："1580,1280,880"）
    val priceTiers: String = "",

    // 票价关键词（如："380 元"）- 兼容旧字段
    val ticketPriceKeyword: String,

    // 票数
    val count: Int = 1,

    // 观演人姓名（逗号分隔，如："张三，李四"）
    val viewerNames: String,

    // 观演人姓名（用于自动选择，单个姓名）- 兼容字段
    val audienceName: String = "",

    // 🆕 观影人索引（默认第 1 个，从 1 开始）
    val audienceIndex: Int = 1,

    // 选中的票价（用于步骤 2 选择票档）- 兼容旧字段
    val selectedPrice: String = "",

    // 🆕 抢票模式（normal=正常抢 | snap=秒杀模式）
    val grabMode: String = "normal",

    // 任务状态（0: 未开始，1: 进行中，2: 已完成，3: 失败）
    val status: String = "空闲",

    // 创建时间
    val createTime: Long = System.currentTimeMillis(),
    
    // 票数（兼容字段）
    val quantity: Int = 1,

    // 备注
    val remark: String = "备注"
)
