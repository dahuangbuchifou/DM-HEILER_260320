// ============================================================================
// 📅 修复日期：2026-03-20
// 🔧 修复内容：
//   - 确认 grabTime 为 Long 类型（避免类型转换错误）
//   - 添加 quantity 字段（用于票数）
// 📝 说明：抢票任务数据模型 - Room 数据库实体
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

    // 票价关键词（如："380元"）
    val ticketPriceKeyword: String,

    // 票数
    val count: Int = 1,

    // 观演人姓名（逗号分隔，如："张三,李四"）
    val viewerNames: String,

    // 任务状态（0: 未开始, 1: 进行中, 2: 已完成, 3: 失败）
    val status: String = "空闲",

    // 创建时间
    val createTime: Long = System.currentTimeMillis(),
    val quantity: Int = 1,  // ✅ 添加这个

    // 备注
    val remark: String = "备注"


)