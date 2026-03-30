// ============================================================================
// 📅 创建日期：2026-03-30 13:05
// 🔧 功能：观众管理界面 - 增删改查
//  说明：支持添加、编辑、删除观众，设置默认观众
//  版本：v2.1.0
// ============================================================================

package com.damaihelper.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.damaihelper.R
import com.damaihelper.model.Audience
import com.damaihelper.model.AudienceDao
import com.damaihelper.model.TaskDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AudienceManagerActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "AudienceManagerActivity"
    }
    
    private lateinit var audienceRecyclerView: RecyclerView
    private lateinit var btnAddAudience: Button
    private lateinit var audienceAdapter: AudienceAdapter
    
    private var db: TaskDatabase? = null
    private var audienceDao: AudienceDao? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audience_manager)
        
        // 初始化数据库
        db = TaskDatabase.getDatabase(this)
        audienceDao = db?.audienceDao()
        
        // 初始化视图
        audienceRecyclerView = findViewById(R.id.audienceRecyclerView)
        btnAddAudience = findViewById(R.id.btnAddAudience)
        
        // 设置 RecyclerView
        setupRecyclerView()
        
        // 设置按钮监听
        btnAddAudience.setOnClickListener {
            showAddEditDialog()
        }
        
        // 加载观众列表
        loadAudiences()
    }
    
    private fun setupRecyclerView() {
        audienceAdapter = AudienceAdapter(
            onSetDefault = { audience -> setAsDefault(audience) },
            onEdit = { audience -> showAddEditDialog(audience) },
            onDelete = { audience -> deleteAudience(audience) }
        )
        
        audienceRecyclerView.layoutManager = LinearLayoutManager(this)
        audienceRecyclerView.adapter = audienceAdapter
    }
    
    private fun loadAudiences() {
        lifecycleScope.launch {
            try {
                audienceDao?.getAllAudiences()?.first()?.let { audiences ->
                    audienceAdapter.submitList(audiences)
                }
            } catch (e: Exception) {
                Toast.makeText(this@AudienceManagerActivity, "加载失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun setAsDefault(audience: Audience) {
        lifecycleScope.launch {
            try {
                // 清除所有默认
                audienceDao?.clearAllDefaults()
                // 设置为默认
                audienceDao?.setAsDefault(audience.id)
                
                Toast.makeText(this@AudienceManagerActivity, "已设置默认观众：${audience.name}", Toast.LENGTH_SHORT).show()
                loadAudiences()
            } catch (e: Exception) {
                Toast.makeText(this@AudienceManagerActivity, "设置失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun deleteAudience(audience: Audience) {
        AlertDialog.Builder(this)
            .setTitle("确认删除")
            .setMessage("确定要删除观众"${audience.name}"吗？")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    try {
                        audienceDao?.delete(audience)
                        Toast.makeText(this@AudienceManagerActivity, "删除成功", Toast.LENGTH_SHORT).show()
                        loadAudiences()
                    } catch (e: Exception) {
                        Toast.makeText(this@AudienceManagerActivity, "删除失败：${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showAddEditDialog(audience: Audience? = null) {
        val isEdit = audience != null
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_add_audience, null)
        
        val etName = view.findViewById<EditText>(R.id.etName)
        val etIdType = view.findViewById<EditText>(R.id.etIdType)
        val etIdNumber = view.findViewById<EditText>(R.id.etIdNumber)
        val etPhone = view.findViewById<EditText>(R.id.etPhone)
        val etRemark = view.findViewById<EditText>(R.id.etRemark)
        
        // 如果是编辑，填充现有数据
        if (isEdit) {
            etName.setText(audience.name)
            etIdType.setText(audience.idType)
            etIdNumber.setText(audience.idNumber)
            etPhone.setText(audience.phone)
            etRemark.setText(audience.remark)
        }
        
        AlertDialog.Builder(this)
            .setTitle(if (isEdit) "编辑观众" else "添加观众")
            .setView(view)
            .setPositiveButton("保存") { _, _ ->
                val name = etName.text.toString().trim()
                val idType = etIdType.text.toString().trim()
                val idNumber = etIdNumber.text.toString().trim()
                val phone = etPhone.text.toString().trim()
                val remark = etRemark.text.toString().trim()
                
                if (name.isEmpty()) {
                    Toast.makeText(this, "姓名不能为空", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                lifecycleScope.launch {
                    try {
                        val newAudience = Audience(
                            id = audience?.id ?: 0,
                            name = name,
                            idType = if (idType.isEmpty()) "身份证" else idType,
                            idNumber = idNumber,
                            phone = phone,
                            isDefault = audience?.isDefault ?: false,
                            createTime = audience?.createTime ?: System.currentTimeMillis(),
                            remark = remark
                        )
                        
                        if (isEdit) {
                            audienceDao?.update(newAudience)
                            Toast.makeText(this@AudienceManagerActivity, "更新成功", Toast.LENGTH_SHORT).show()
                        } else {
                            audienceDao?.insert(newAudience)
                            Toast.makeText(this@AudienceManagerActivity, "添加成功", Toast.LENGTH_SHORT).show()
                        }
                        
                        loadAudiences()
                    } catch (e: Exception) {
                        Toast.makeText(this@AudienceManagerActivity, "保存失败：${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}

/**
 * 观众列表适配器
 */
class AudienceAdapter(
    private val onSetDefault: (Audience) -> Unit,
    private val onEdit: (Audience) -> Unit,
    private val onDelete: (Audience) -> Unit
) : RecyclerView.Adapter<AudienceAdapter.ViewHolder>() {
    
    private var audiences: List<Audience> = emptyList()
    
    fun submitList(newAudiences: List<Audience>) {
        audiences = newAudiences
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_audience, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val audience = audiences[position]
        holder.bind(audience, onSetDefault, onEdit, onDelete)
    }
    
    override fun getItemCount() = audiences.size
    
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvAudienceName: TextView = itemView.findViewById(R.id.tvAudienceName)
        private val tvDefaultBadge: TextView = itemView.findViewById(R.id.tvDefaultBadge)
        private val tvIdInfo: TextView = itemView.findViewById(R.id.tvIdInfo)
        private val tvPhone: TextView = itemView.findViewById(R.id.tvPhone)
        private val btnSetDefault: Button = itemView.findViewById(R.id.btnSetDefault)
        private val btnEdit: Button = itemView.findViewById(R.id.btnEdit)
        private val btnDelete: Button = itemView.findViewById(R.id.btnDelete)
        
        fun bind(
            audience: Audience,
            onSetDefault: (Audience) -> Unit,
            onEdit: (Audience) -> Unit,
            onDelete: (Audience) -> Unit
        ) {
            tvAudienceName.text = audience.name
            tvIdInfo.text = "${audience.idType}：${audience.idNumber}"
            tvPhone.text = "手机：${audience.phone}"
            
            // 显示默认标记
            tvDefaultBadge.visibility = if (audience.isDefault) View.VISIBLE else View.GONE
            btnSetDefault.visibility = if (audience.isDefault) View.GONE else View.VISIBLE
            
            // 按钮监听
            btnSetDefault.setOnClickListener { onSetDefault(audience) }
            btnEdit.setOnClickListener { onEdit(audience) }
            btnDelete.setOnClickListener { onDelete(audience) }
        }
    }
}
