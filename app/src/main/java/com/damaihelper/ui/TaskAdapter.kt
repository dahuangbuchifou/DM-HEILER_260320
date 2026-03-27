package com.damaihelper.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.damaihelper.R
import com.damaihelper.model.TicketTask

class TaskAdapter(
    private val onEdit: (TicketTask) -> Unit,
    private val onDelete: (TicketTask) -> Unit,
    private val onStartStop: (TicketTask) -> Unit
) : ListAdapter<TicketTask, TaskAdapter.TaskViewHolder>(TaskDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val task = getItem(position)
        holder.bind(task)
    }

    inner class TaskViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val taskName: TextView = itemView.findViewById(R.id.task_name)
        private val taskDetails: TextView = itemView.findViewById(R.id.task_details)
        private val taskStatus: TextView = itemView.findViewById(R.id.task_status)
        private val editButton: Button = itemView.findViewById(R.id.edit_button)
        private val deleteButton: Button = itemView.findViewById(R.id.delete_button)
        private val startStopButton: Button = itemView.findViewById(R.id.start_stop_button)

        fun bind(task: TicketTask) {
            taskName.text = task.name
            // ✅ 更新显示逻辑：显示 audienceName 和 selectedPrice
            val dateStr = if (task.grabTime > 0) {
                val sdf = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                sdf.format(java.util.Date(task.grabTime))
            } else {
                task.grabDate
            }
            val audienceStr = if (task.audienceName.isNotEmpty()) task.audienceName else "未指定"
            val priceStr = if (task.selectedPrice.isNotEmpty()) task.selectedPrice else task.ticketPriceKeyword
            taskDetails.text = "关键词：${task.concertKeyword} | 日期：$dateStr | 观众：$audienceStr | 票档：$priceStr"
            taskStatus.text = task.status

            // 根据状态设置颜色
            val colorRes = when (task.status) {
                itemView.context.getString(R.string.task_status_idle) -> R.color.status_idle
                itemView.context.getString(R.string.task_status_waiting) -> R.color.status_waiting
                itemView.context.getString(R.string.task_status_running) -> R.color.status_running
                itemView.context.getString(R.string.task_status_success) -> R.color.status_success
                itemView.context.getString(R.string.task_status_failed) -> R.color.status_failed
                else -> R.color.black
            }
            taskStatus.setTextColor(itemView.context.getColor(colorRes))

            // 按钮点击事件
            editButton.setOnClickListener { onEdit(task) }
            deleteButton.setOnClickListener { onDelete(task) }
            startStopButton.setOnClickListener { onStartStop(task) }

            // 根据状态设置启动/停止按钮文本
            if (task.status == itemView.context.getString(R.string.task_status_running)) {
                startStopButton.text = itemView.context.getString(R.string.stop_grabbing)
            } else {
                startStopButton.text = itemView.context.getString(R.string.start_grabbing)
            }
        }
    }

    class TaskDiffCallback : DiffUtil.ItemCallback<TicketTask>() {
        override fun areItemsTheSame(oldItem: TicketTask, newItem: TicketTask): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TicketTask, newItem: TicketTask): Boolean {
            return oldItem == newItem
        }
    }
}

