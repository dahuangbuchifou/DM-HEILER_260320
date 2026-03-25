// ============================================================================
// 📅 修复日期：2026-03-20
// 🔧 修复内容：
//   - 确认 Parcelable 实现正确
//   - 添加 extractTime 字段（记录抓取时间）
// 📝 说明：演出信息数据模型 - 用于 UI 和广播传输
// ============================================================================

package com.damaihelper.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ConcertInfo(
    val concertName: String = "",           // 演出名称
    val venue: String = "",                 // 场馆
    val city: String = "",                  // 城市
    val availableDates: List<String> = emptyList(),  // 可选日期列表
    val availablePrices: List<String> = emptyList(), // 可选票价列表
    val extractTime: Long = System.currentTimeMillis(), // 抓取时间
    
    // === 新增字段：抢票配置信息 ===
    val selectedDate: String = "",          // 已选日期（如：2026-04-18 周六 19:00）
    val selectedPrice: String = "",         // 已选票价（优先最贵，如：内场 880 元）
    val ticketCount: Int = 1,               // 票数
    val audienceName: String = "",          // 观演人姓名
    val audienceIdCard: String = "",        // 身份证号（脱敏，如：220***********512）
    val phoneNumber: String = "",           // 联系电话
    val paymentMethod: String = "支付宝"    // 支付方式（支付宝/微信）
) : Parcelable