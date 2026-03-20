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