package com.damaihelper.model

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM ticket_tasks ORDER BY id DESC")
    fun getAllTasks(): Flow<List<TicketTask>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TicketTask)

    @Update
    suspend fun updateTask(task: TicketTask)

    @Delete
    suspend fun deleteTask(task: TicketTask)

    @Query("SELECT * FROM ticket_tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: Int): TicketTask?
}

