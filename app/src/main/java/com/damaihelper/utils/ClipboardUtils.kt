// ============================================================================
// 📅 创建日期：2026-03-30 12:50
// 🔧 功能：剪贴板工具类 - 用于自动输入文字
//  说明：通过剪贴板 + 粘贴实现文字输入，绕过无障碍服务输入限制
//  版本：v2.0.0
// ============================================================================

package com.damaihelper.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log

/**
 * 剪贴板工具类
 * 
 * 用于自动输入文字：
 * 1. 复制文字到剪贴板
 * 2. 点击输入框获得焦点
 * 3. 触发粘贴操作
 */
object ClipboardUtils {
    
    private const val TAG = "ClipboardUtils"
    private const val LABEL = "DM-HEILER_AUTO_INPUT"
    
    /**
     * 复制文字到剪贴板
     */
    fun copyToClipboard(context: Context, text: String): Boolean {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(LABEL, text)
            clipboard.setPrimaryClip(clip)
            Log.d(TAG, "✅ 复制文字到剪贴板：$text")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "❌ 复制失败", e)
            return false
        }
    }
    
    /**
     * 从剪贴板读取文字
     */
    fun getFromClipboard(context: Context): String? {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text.toString()
                Log.d(TAG, "✅ 从剪贴板读取文字：$text")
                return text
            }
            
            Log.w(TAG, "⚠️ 剪贴板为空")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "❌ 读取失败", e)
            return null
        }
    }
    
    /**
     * 清空剪贴板
     */
    fun clearClipboard(context: Context) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            Log.d(TAG, "✅ 清空剪贴板")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 清空失败", e)
        }
    }
}
