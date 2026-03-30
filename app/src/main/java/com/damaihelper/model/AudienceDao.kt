// ============================================================================
// 📅 创建日期：2026-03-30 12:45
// 🔧 功能：观众人 DAO - 数据库访问对象
//  版本：v2.0.0
// ============================================================================

package com.damaihelper.model

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AudienceDao {
    
    // 查询所有观众
    @Query("SELECT * FROM audiences ORDER BY isDefault DESC, createTime ASC")
    fun getAllAudiences(): Flow<List<Audience>>
    
    // 查询默认观众
    @Query("SELECT * FROM audiences WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultAudience(): Audience?
    
    // 根据 ID 查询
    @Query("SELECT * FROM audiences WHERE id = :id")
    suspend fun getAudienceById(id: Long): Audience?
    
    // 插入观众
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(audience: Audience): Long
    
    // 更新观众
    @Update
    suspend fun update(audience: Audience)
    
    // 删除观众
    @Delete
    suspend fun delete(audience: Audience)
    
    // 设置默认观众
    @Query("UPDATE audiences SET isDefault = 0")
    suspend fun clearAllDefaults()
    
    @Query("UPDATE audiences SET isDefault = 1 WHERE id = :id")
    suspend fun setAsDefault(id: Long)
    
    // 查询总数
    @Query("SELECT COUNT(*) FROM audiences")
    suspend fun getCount(): Int
}
