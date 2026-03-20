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
    val extractTime: Long = System.currentTimeMillis() // 抓取时间
) : Parcelable