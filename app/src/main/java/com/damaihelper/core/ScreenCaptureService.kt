package com.damaihelper.core

import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.view.Display
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 📸 截屏服务 - 使用 MediaProjection API
 * 
 * 功能：
 * - 定时截屏（可配置 FPS）
 * - 提供最新截图给图像识别模块
 * - 支持分屏状态检测
 * 
 * 使用流程：
 * 1. 调用 requestPermission() 申请权限
 * 2. 用户授权后调用 startCapture() 开始截屏
 * 3. 通过 getLatestFrame() 获取最新截图
 * 4. 调用 stopCapture() 停止截屏
 */
class ScreenCaptureService(private val context: Context) {

    companion object {
        private const val TAG = "ScreenCapture"
        const val REQUEST_CODE_SCREEN_CAPTURE = 1001
        
        // 默认截屏频率：1 FPS
        const val DEFAULT_FPS = 1
        const val DEFAULT_INTERVAL_MS = 1000 / DEFAULT_FPS
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handler: Handler? = null
    private var isCapturing = false
    
    // 屏幕尺寸
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    // 最新截图
    @Volatile
    private var latestFrame: Bitmap? = null
    
    // 截屏频率
    private var captureIntervalMs = DEFAULT_INTERVAL_MS
    
    // 状态流
    private val _isCapturingFlow = MutableStateFlow(false)
    val isCapturingFlow: StateFlow<Boolean> = _isCapturingFlow.asStateFlow()

    /**
     * 请求截屏权限
     * 需要在 Activity 中调用
     */
    fun requestPermission(activity: Activity) {
        val projectionManager = context.getSystemService(
            Service.MEDIA_PROJECTION_SERVICE
        ) as MediaProjectionManager
        
        val permissionIntent = projectionManager.createScreenCaptureIntent()
        activity.startActivityForResult(permissionIntent, REQUEST_CODE_SCREEN_CAPTURE)
    }

    /**
     * 处理权限结果
     * 在 Activity 的 onActivityResult 中调用
     */
    fun handlePermissionResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_CODE_SCREEN_CAPTURE) {
            return false
        }

        if (resultCode != Activity.RESULT_OK) {
            Log.e(TAG, "用户拒绝截屏权限")
            return false
        }

        val projectionManager = context.getSystemService(
            Service.MEDIA_PROJECTION_SERVICE
        ) as MediaProjectionManager
        
        mediaProjection = projectionManager.getMediaProjection(resultCode, data!!)
        Log.i(TAG, "✅ 截屏权限已授予")
        return true
    }

    /**
     * 开始截屏
     * @param fps 每秒帧数，默认 1
     */
    fun startCapture(fps: Int = DEFAULT_FPS) {
        if (mediaProjection == null) {
            Log.e(TAG, "❌ 未获取截屏权限，请先调用 requestPermission()")
            return
        }

        if (isCapturing) {
            Log.w(TAG, "截屏已在进行中")
            return
        }

        // 获取屏幕尺寸
        val metrics = DisplayMetrics()
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        displayManager.getDisplay(Display.DEFAULT_DISPLAY).getMetrics(metrics)
        
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi

        Log.i(TAG, "屏幕尺寸：${screenWidth}x${screenHeight}, DPI: $screenDensity")

        // 创建 ImageReader
        captureIntervalMs = 1000 / fps
        imageReader = ImageReader.newInstance(
            screenWidth,
            screenHeight,
            PixelFormat.RGBA_8888,
            2  // 最大图像数
        )

        // 创建 VirtualDisplay
        virtualDisplay = mediaProjection!!.createVirtualDisplay(
            "ScreenCapture",
            screenWidth,
            screenHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null,
            handler
        )

        // 开始定时捕获
        handler = Handler(Looper.getMainLooper())
        startCaptureLoop()
        
        isCapturing = true
        _isCapturingFlow.value = true
        
        Log.i(TAG, "✅ 开始截屏，FPS: $fps, 间隔：${captureIntervalMs}ms")
    }

    /**
     * 截屏循环
     */
    private fun startCaptureLoop() {
        handler?.post(captureRunnable)
    }

    private val captureRunnable = object : Runnable {
        override fun run() {
            if (!isCapturing) return

            try {
                val image = imageReader?.acquireLatestImage() ?: return
                
                // 转换为 Bitmap
                val bitmap = imageToBitmap(image)
                if (bitmap != null) {
                    latestFrame = bitmap
                    Log.d(TAG, "📸 捕获帧：${bitmap.width}x${bitmap.height}")
                }
                
                image.close()
            } catch (e: Exception) {
                Log.e(TAG, "捕获帧失败", e)
            }

            handler?.postDelayed(this, captureIntervalMs)
        }
    }

    /**
     * Image 转 Bitmap
     */
    private fun imageToBitmap(image: Image): Bitmap? {
        try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * screenWidth

            val bitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            
            // 裁剪到实际屏幕尺寸
            return Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
        } catch (e: Exception) {
            Log.e(TAG, "Image 转 Bitmap 失败", e)
            return null
        }
    }

    /**
     * 获取最新截图
     */
    fun getLatestFrame(): Bitmap? {
        return latestFrame
    }

    /**
     * 停止截屏
     */
    fun stopCapture() {
        if (!isCapturing) return

        isCapturing = false
        _isCapturingFlow.value = false
        
        handler?.removeCallbacks(captureRunnable)
        
        virtualDisplay?.release()
        imageReader?.close()
        latestFrame = null
        
        Log.i(TAG, "⏹️ 停止截屏")
    }

    /**
     * 释放资源
     */
    fun release() {
        stopCapture()
        mediaProjection?.stop()
        mediaProjection = null
        Log.i(TAG, "🔚 截屏服务已释放")
    }

    /**
     * 检测是否分屏状态
     * 通过屏幕宽度判断（分屏时宽度会减小）
     */
    fun isSplitScreenMode(): Boolean {
        if (screenWidth == 0) return false
        // 如果屏幕宽度小于完整宽度的 90%，可能是分屏
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val metrics = DisplayMetrics()
        displayManager.getDisplay(Display.DEFAULT_DISPLAY).getMetrics(metrics)
        
        val currentWidth = metrics.widthPixels
        val isSplit = currentWidth < screenWidth * 0.9
        
        Log.d(TAG, "分屏检测：当前宽度=$currentWidth, 完整宽度=$screenWidth, 分屏=$isSplit")
        return isSplit
    }
}
