// 文件路径: app/src/main/java/com/damaihelper/captcha/OcrService.kt

package com.damaihelper.captcha

import android.graphics.Bitmap
import android.util.Log
import com.damaihelper.service.TicketGrabbingAccessibilityService

/**
 * 验证码识别服务（本地 OCR 封装）。
 * 由于依赖问题，目前暂时禁用 ML Kit 逻辑。
 */
class OcrService(private val service: TicketGrabbingAccessibilityService) {
    private val TAG = "OcrService"

    /**
     * 尝试使用本地 OCR 识别。
     * @param screenshotBitmap 待识别的验证码图片。
     * @return 识别成功并尝试输入文本则返回 true，否则返回 false。
     */
    suspend fun recognizeWithMLKit(screenshotBitmap: Bitmap): Boolean {
        Log.d(TAG, "ML Kit OCR is temporarily disabled due to dependency issues.")

        // 暂时返回 false，直到我们解决依赖问题
        return false
    }
}
