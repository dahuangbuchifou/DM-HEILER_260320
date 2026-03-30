// ============================================================================
// 📅 创建日期：2026-03-30 12:45
// 🔧 功能：观众人数据模型 - 用于预先配置观众信息
//  说明：支持增删改查，自动选择默认观众
//  版本：v2.0.0
// ============================================================================

package com.damaihelper.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 观众人信息数据模型
 * 
 * 用于预先配置观众信息，支持：
 * - 添加观众
 * - 删除观众
 * - 修改观众
 * - 设置默认观众
 * - 自动选择
 */
@Entity(tableName = "audiences")
data class Audience(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // 观众姓名
    val name: String,
    
    // 证件类型（身份证、护照等）
    val idType: String = "身份证",
    
    // 证件号码
    val idNumber: String = "",
    
    // 手机号
    val phone: String = "",
    
    // 是否为默认观众
    val isDefault: Boolean = false,
    
    // 创建时间
    val createTime: Long = System.currentTimeMillis(),
    
    // 备注
    val remark: String = ""
)
