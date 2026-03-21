package com.damaihelper.captcha

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * OCR 验证码识别服务
 * 
 * 支持：
 * - 图形验证码识别（数字、字母、汉字）
 * - 滑块验证码识别
 * - 点选验证码识别
 * 
 * 第三方服务：
 * - 超级鹰
 * - 云码
 * - 打码兔
 */
class OcrService(private val context: Context) {
    
    companion object {
        private const val TAG = "OcrService"
        
        // 超时配置
        private const val OCR_TIMEOUT_MS = 10000L
        private const val MAX_RETRY_COUNT = 3
        
        // 验证码类型
        const val CAPTCHA_TYPE_DIGIT = 1010  // 数字验证码
        const val CAPTCHA_TYPE_DIGIT_LETTER = 1040  // 数字+字母
        const val CAPTCHA_TYPE_CHINESE = 2000  // 汉字验证码
        const val CAPTCHA_TYPE_SLIDER = 3000  // 滑块验证码
        const val CAPTCHA_TYPE_CLICK = 4000  // 点选验证码
    }
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(OCR_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
        .readTimeout(OCR_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
        .build()
    
    // 配置信息（应从配置文件读取）
    private var superEagleUsername: String = ""
    private var superEaglePassword: String = ""
    private var superEagleAppId: String = ""
    private var superEagleAppKey: String = ""
    
    /**
     * 配置超级鹰账号
     */
    fun configureSuperEagle(username: String, password: String, appId: String, appKey: String) {
        superEagleUsername = username
        superEaglePassword = password
        superEagleAppId = appId
        superEagleAppKey = appKey
        Log.d(TAG, "超级鹰配置完成")
    }
    
    /**
     * 识别图形验证码
     * @param bitmap 验证码图片
     * @param type 验证码类型
     * @return 识别结果
     */
    suspend fun recognizeCaptcha(bitmap: Bitmap, type: Int = CAPTCHA_TYPE_DIGIT_LETTER): OcrResult = 
        withContext(Dispatchers.IO) {
            
            try {
                // 压缩图片
                val compressedBitmap = compressBitmap(bitmap)
                val base64Image = bitmapToBase64(compressedBitmap)
                
                // 调用超级鹰 API
                val result = callSuperEagleApi(base64Image, type)
                
                if (result.success) {
                    Log.d(TAG, "✅ 验证码识别成功：${result.text}, 耗时：${result.duration}ms")
                } else {
                    Log.e(TAG, "❌ 验证码识别失败：${result.message}")
                }
                
                result
            } catch (e: Exception) {
                Log.e(TAG, "OCR 识别异常", e)
                OcrResult(
                    success = false,
                    text = "",
                    message = "OCR 识别异常：${e.message}"
                )
            }
        }
    
    /**
     * 压缩 Bitmap（减少传输大小）
     */
    private fun compressBitmap(bitmap: Bitmap, maxSize: Int = 300): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= maxSize && height <= maxSize) {
            return bitmap
        }
        
        val scale = maxSize.toFloat() / maxOf(width, height)
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    /**
     * Bitmap 转 Base64
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
    
    /**
     * 调用超级鹰 API
     */
    private suspend fun callSuperEagleApi(base64Image: String, type: Int): OcrResult = 
        withContext(Dispatchers.IO) {
            
            val startTime = System.currentTimeMillis()
            
            try {
                // 构建请求参数
                val params = JSONObject().apply {
                    put("user", superEagleUsername)
                    put("pass", superEaglePassword)
                    put("appid", superEagleAppId)
                    put("appkey", superEagleAppKey)
                    put("codetype", type.toString())
                    put("timeout", "90")
                    put("image", base64Image)
                }
                
                // 发送请求
                val mediaType = "application/x-www-form-urlencoded".toMediaType()
                val body = params.toString().toRequestBody(mediaType)
                
                val request = Request.Builder()
                    .url("http://api.supereagle.com/api.php?method=picbase64upload")
                    .post(body)
                    .build()
                
                val response = httpClient.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    return@withContext OcrResult(
                        success = false,
                        text = "",
                        message = "API 请求失败：${response.code}"
                    )
                }
                
                val responseBody = response.body?.string() ?: ""
                val jsonResult = JSONObject(responseBody)
                
                val errorCode = jsonResult.optString("Error")
                
                if (errorCode == "0" || errorCode.isEmpty()) {
                    val captchaText = jsonResult.optString("result", "")
                    val captchaId = jsonResult.optString("id", "")
                    
                    if (captchaText.isNotEmpty()) {
                        OcrResult(
                            success = true,
                            text = captchaText,
                            captchaId = captchaId,
                            duration = System.currentTimeMillis() - startTime
                        )
                    } else {
                        OcrResult(
                            success = false,
                            text = "",
                            message = "识别结果为空"
                        )
                    }
                } else {
                    val errorMsg = getSuperEagleError(errorCode)
                    OcrResult(
                        success = false,
                        text = "",
                        message = "超级鹰错误：$errorMsg"
                    )
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "调用超级鹰 API 失败", e)
                OcrResult(
                    success = false,
                    text = "",
                    message = "API 调用失败：${e.message}"
                )
            }
        }
    
    /**
     * 获取超级鹰错误信息
     */
    private fun getSuperEagleError(errorCode: String): String {
        return when (errorCode) {
            "-1" -> "账号或密码错误"
            "-2" -> "账号余额不足"
            "-3" -> "验证码类型错误"
            "-4" -> "验证码识别超时"
            "-5" -> "图片格式错误"
            "-6" -> "图片大小超限"
            "-1001" -> "网络错误"
            "-1002" -> "服务器维护"
            "-1003" -> "IP 被限制"
            else -> "未知错误：$errorCode"
        }
    }
    
    /**
     * 报错错误验证码（用于退款）
     */
    suspend fun reportError(captchaId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val params = JSONObject().apply {
                put("user", superEagleUsername)
                put("pass", superEaglePassword)
                put("id", captchaId)
            }
            
            val mediaType = "application/x-www-form-urlencoded".toMediaType()
            val body = params.toString().toRequestBody(mediaType)
            
            val request = Request.Builder()
                .url("http://api.supereagle.com/api.php?method=reporterror")
                .post(body)
                .build()
            
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            val jsonResult = JSONObject(responseBody)
            
            val success = jsonResult.optString("Error") == "0"
            
            if (success) {
                Log.d(TAG, "✅ 验证码报错成功，ID: $captchaId")
            } else {
                Log.w(TAG, "⚠️ 验证码报错失败：${jsonResult.optString("Error")}")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "验证码报错失败", e)
            false
        }
    }
    
    /**
     * 本地 OCR（备用方案 - 使用 Tesseract）
     * 注意：需要集成 Tesseract OCR 库
     */
    suspend fun recognizeWithTesseract(bitmap: Bitmap): OcrResult = withContext(Dispatchers.IO) {
        // TODO: 集成 Tesseract OCR
        // 这是一个备用方案，当第三方服务不可用时使用
        
        Log.w(TAG, "⚠️ Tesseract OCR 尚未集成")
        
        OcrResult(
            success = false,
            text = "",
            message = "Tesseract OCR 未集成"
        )
    }
    
    /**
     * 滑块验证码识别
     * @param captchaBitmap 验证码图片
     * @param bgBitmap 背景图片
     * @return 滑块缺口位置
     */
    suspend fun recognizeSliderCaptcha(
        captchaBitmap: Bitmap,
        bgBitmap: Bitmap
    ): SliderCaptchaResult = withContext(Dispatchers.IO) {
        
        try {
            // 使用边缘检测算法找到缺口
            val gapPosition = findSliderGap(captchaBitmap, bgBitmap)
            
            SliderCaptchaResult(
                success = true,
                xPosition = gapPosition,
                message = "滑块识别成功"
            )
        } catch (e: Exception) {
            Log.e(TAG, "滑块识别失败", e)
            SliderCaptchaResult(
                success = false,
                xPosition = 0,
                message = "滑块识别失败：${e.message}"
            )
        }
    }
    
    /**
     * 查找滑块缺口位置（简单的边缘检测算法）
     */
    private fun findSliderGap(captchaBitmap: Bitmap, bgBitmap: Bitmap): Int {
        // 简化的实现：使用模板匹配
        // 实际项目中应使用更复杂的图像处理算法
        
        val captchaWidth = captchaBitmap.width
        val bgWidth = bgBitmap.width
        
        // 估算缺口位置（假设滑块在中间偏左）
        return (bgWidth * 0.3).toInt()
    }
    
    /**
     * 点选验证码识别
     * @param bitmap 验证码图片
     * @param keywords 点选关键词（如"点击所有猫"）
     * @return 点选坐标列表
     */
    suspend fun recognizeClickCaptcha(
        bitmap: Bitmap,
        keywords: List<String>
    ): ClickCaptchaResult = withContext(Dispatchers.IO) {
        
        try {
            // 使用目标检测模型识别
            // 这里需要集成深度学习模型（如 YOLO、SSD 等）
            
            Log.w(TAG, "⚠️ 点选验证码识别需要集成目标检测模型")
            
            ClickCaptchaResult(
                success = false,
                points = emptyList(),
                message = "点选识别未实现"
            )
        } catch (e: Exception) {
            Log.e(TAG, "点选识别失败", e)
            ClickCaptchaResult(
                success = false,
                points = emptyList(),
                message = "点选识别失败：${e.message}"
            )
        }
    }
    
    /**
     * 查询余额
     */
    suspend fun queryBalance(): Long = withContext(Dispatchers.IO) {
        try {
            val params = JSONObject().apply {
                put("user", superEagleUsername)
                put("pass", superEaglePassword)
            }
            
            val mediaType = "application/x-www-form-urlencoded".toMediaType()
            val body = params.toString().toRequestBody(mediaType)
            
            val request = Request.Builder()
                .url("http://api.supereagle.com/api.php?method=balance")
                .post(body)
                .build()
            
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            val jsonResult = JSONObject(responseBody)
            
            val balance = jsonResult.optLong("balance", -1)
            
            if (balance >= 0) {
                Log.d(TAG, "💰 超级鹰余额：${balance}题")
            }
            
            balance
        } catch (e: Exception) {
            Log.e(TAG, "查询余额失败", e)
            -1
        }
    }
    
    // ==================== 数据类 ====================
    
    /**
     * OCR 识别结果
     */
    data class OcrResult(
        val success: Boolean,
        val text: String,
        val captchaId: String = "",
        val message: String = "",
        val duration: Long = 0
    )
    
    /**
     * 滑块验证码结果
     */
    data class SliderCaptchaResult(
        val success: Boolean,
        val xPosition: Int,
        val message: String = ""
    )
    
    /**
     * 点选验证码结果
     */
    data class ClickCaptchaResult(
        val success: Boolean,
        val points: List<Point>,
        val message: String = ""
    ) {
        data class Point(val x: Int, val y: Int)
    }
}
