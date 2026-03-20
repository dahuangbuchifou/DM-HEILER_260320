package com.damaihelper.service

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import com.damaihelper.config.CaptchaConfig

/**
 * 验证码识别服务
 * 集成多个第三方验证码识别API，提供自动化验证码处理能力
 */
class CaptchaRecognitionService {

    companion object {
        private const val TAG = "CaptchaRecognition"

        // 超级鹰验证码识别服务配置
        private const val CHAOJIYING_API_URL = "http://upload.chaojiying.net/Upload/Processing.php"

        // 云码验证码识别服务配置
        private const val YUNMA_API_URL = "http://api.jfbym.com/api/YmServer/customApi"

        // 2Captcha服务配置
        private const val TWOCAPTCHA_API_URL = "http://2captcha.com/in.php"
        private const val TWOCAPTCHA_RESULT_URL = "http://2captcha.com/res.php"

        // 验证码类型
        const val TYPE_NORMAL = 1004  // 普通数字字母验证码
        const val TYPE_CLICK = 9004   // 点选验证码
        const val TYPE_SLIDE = 2001   // 滑动验证码
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 识别验证码的主要入口方法
     * 会依次尝试多个服务，直到成功或全部失败
     */
    suspend fun recognizeCaptcha(
        captchaBitmap: Bitmap,
        captchaType: Int = TYPE_NORMAL
    ): CaptchaResult {
        return withContext(Dispatchers.IO) {
            val base64Image = bitmapToBase64(captchaBitmap)

            // 依次尝试不同的验证码识别服务
            val services = listOf(
                ::recognizeWithChaojiying,
                ::recognizeWithYunma,
                ::recognizeWithTwoCaptcha
            )

            for (service in services) {
                try {
                    val result = service(base64Image, captchaType)
                    if (result.success) {
                        Log.d(TAG, "验证码识别成功: ${result.text}")
                        return@withContext result
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "验证码识别服务异常", e)
                }
            }

            Log.e(TAG, "所有验证码识别服务均失败")
            return@withContext CaptchaResult(false, "", "所有识别服务均失败")
        }
    }

    /**
     * 使用超级鹰服务识别验证码
     */
    private suspend fun recognizeWithChaojiying(
        base64Image: String,
        captchaType: Int
    ): CaptchaResult {
        return withContext(Dispatchers.IO) {
            try {
                // 检查配置
                if (CaptchaConfig.CHAOJIYING_USER.isEmpty() ||
                    CaptchaConfig.CHAOJIYING_PASS.isEmpty() ||
                    CaptchaConfig.CHAOJIYING_SOFT_ID.isEmpty()) {
                    return@withContext CaptchaResult(false, "", "超级鹰配置未完成")
                }

                val formBody = FormBody.Builder()
                    .add("user", CaptchaConfig.CHAOJIYING_USER)
                    .add("pass", CaptchaConfig.CHAOJIYING_PASS)
                    .add("softid", CaptchaConfig.CHAOJIYING_SOFT_ID)
                    .add("codetype", captchaType.toString())
                    .add("userfile", base64Image)
                    .build()

                val request = Request.Builder()
                    .url(CHAOJIYING_API_URL)
                    .post(formBody)
                    .build()

                val response = httpClient.newCall(request).execute()
                val responseBody = response.body?.string()

                if (response.isSuccessful && responseBody != null) {
                    val jsonResponse = JSONObject(responseBody)
                    val errorCode = jsonResponse.optInt("err_no", -1)

                    if (errorCode == 0) {
                        val recognizedText = jsonResponse.optString("pic_str", "")
                        return@withContext CaptchaResult(true, recognizedText, "超级鹰识别成功")
                    } else {
                        val errorMsg = jsonResponse.optString("err_str", "未知错误")
                        return@withContext CaptchaResult(false, "", "超级鹰识别失败: $errorMsg")
                    }
                } else {
                    return@withContext CaptchaResult(false, "", "超级鹰服务请求失败")
                }
            } catch (e: Exception) {
                Log.e(TAG, "超级鹰识别异常", e)
                return@withContext CaptchaResult(false, "", "超级鹰识别异常: ${e.message}")
            }
        }
    }

    /**
     * 使用云码服务识别验证码
     */
    private suspend fun recognizeWithYunma(
        base64Image: String,
        captchaType: Int
    ): CaptchaResult {
        return withContext(Dispatchers.IO) {
            try {
                // 检查配置
                if (CaptchaConfig.YUNMA_TOKEN.isEmpty()) {
                    return@withContext CaptchaResult(false, "", "云码配置未完成")
                }

                val jsonBody = JSONObject().apply {
                    put("image", base64Image)
                    put("token", CaptchaConfig.YUNMA_TOKEN)
                    put("type", captchaType.toString())
                }

                val requestBody = jsonBody.toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType())

                val request = Request.Builder()
                    .url(YUNMA_API_URL)
                    .post(requestBody)
                    .build()

                val response = httpClient.newCall(request).execute()
                val responseBody = response.body?.string()

                if (response.isSuccessful && responseBody != null) {
                    val jsonResponse = JSONObject(responseBody)
                    val code = jsonResponse.optInt("code", -1)

                    if (code == 10000) {
                        val data = jsonResponse.optJSONObject("data")
                        val recognizedText = data?.optString("data", "") ?: ""
                        return@withContext CaptchaResult(true, recognizedText, "云码识别成功")
                    } else {
                        val message = jsonResponse.optString("msg", "未知错误")
                        return@withContext CaptchaResult(false, "", "云码识别失败: $message")
                    }
                } else {
                    return@withContext CaptchaResult(false, "", "云码服务请求失败")
                }
            } catch (e: Exception) {
                Log.e(TAG, "云码识别异常", e)
                return@withContext CaptchaResult(false, "", "云码识别异常: ${e.message}")
            }
        }
    }

    /**
     * 使用2Captcha服务识别验证码
     */
    private suspend fun recognizeWithTwoCaptcha(
        base64Image: String,
        captchaType: Int
    ): CaptchaResult {
        return withContext(Dispatchers.IO) {
            try {
                // 检查配置
                if (CaptchaConfig.TWOCAPTCHA_API_KEY.isEmpty()) {
                    return@withContext CaptchaResult(false, "", "2Captcha配置未完成")
                }

                // 第一步：提交验证码
                val submitBody = FormBody.Builder()
                    .add("method", "base64")
                    .add("key", CaptchaConfig.TWOCAPTCHA_API_KEY)
                    .add("body", base64Image)
                    .build()

                val submitRequest = Request.Builder()
                    .url(TWOCAPTCHA_API_URL)
                    .post(submitBody)
                    .build()

                val submitResponse = httpClient.newCall(submitRequest).execute()
                val submitResponseBody = submitResponse.body?.string()

                if (!submitResponse.isSuccessful || submitResponseBody == null) {
                    return@withContext CaptchaResult(false, "", "2Captcha提交失败")
                }

                if (submitResponseBody.startsWith("ERROR")) {
                    return@withContext CaptchaResult(false, "", "2Captcha提交错误: $submitResponseBody")
                }

                val captchaId = submitResponseBody.removePrefix("OK|")

                // 第二步：轮询获取结果
                var attempts = 0
                val maxAttempts = 30

                while (attempts < maxAttempts) {
                    kotlinx.coroutines.delay(3000) // 等待3秒

                    val resultRequest = Request.Builder()
                        .url("$TWOCAPTCHA_RESULT_URL?key=${CaptchaConfig.TWOCAPTCHA_API_KEY}&action=get&id=$captchaId")
                        .build()

                    val resultResponse = httpClient.newCall(resultRequest).execute()
                    val resultResponseBody = resultResponse.body?.string()

                    if (resultResponse.isSuccessful && resultResponseBody != null) {
                        when {
                            resultResponseBody == "CAPCHA_NOT_READY" -> {
                                attempts++
                                continue
                            }
                            resultResponseBody.startsWith("OK|") -> {
                                val recognizedText = resultResponseBody.removePrefix("OK|")
                                return@withContext CaptchaResult(true, recognizedText, "2Captcha识别成功")
                            }
                            resultResponseBody.startsWith("ERROR") -> {
                                return@withContext CaptchaResult(false, "", "2Captcha识别错误: $resultResponseBody")
                            }
                        }
                    }
                    attempts++
                }

                return@withContext CaptchaResult(false, "", "2Captcha识别超时")
            } catch (e: Exception) {
                Log.e(TAG, "2Captcha识别异常", e)
                return@withContext CaptchaResult(false, "", "2Captcha识别异常: ${e.message}")
            }
        }
    }

    /**
     * 将Bitmap转换为Base64字符串
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    /**
     * 验证码识别结果数据类
     */
    data class CaptchaResult(
        val success: Boolean,
        val text: String,
        val message: String
    )
}