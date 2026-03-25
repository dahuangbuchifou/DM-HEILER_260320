package com.damaihelper.utils

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * 反检测和环境伪装模块 - 增强版
 * 
 * 功能层级：
 * - L1: 基础设备指纹伪造
 * - L2: 网络层伪装（TLS 指纹、HTTP 头）
 * - L3: 行为模式随机化
 * - L4: 环境风险检测
 */
class AntiDetectionModule(private val context: Context) {

    companion object {
        private const val TAG = "AntiDetection"
        
        // 指纹轮换间隔（毫秒）- 每次抢票会话更换一次
        private const val FINGERPRINT_ROTATION_INTERVAL_MS = 300_000L // 5 分钟
        
        // 请求频率限制（毫秒）
        private const val MIN_REQUEST_INTERVAL_MS = 50L
        private const val MAX_REQUEST_INTERVAL_MS = 200L
    }

    private val random = Random(System.currentTimeMillis()) // 使用 Kotlin Random（支持范围参数）
    private val deviceFingerprintSpoofing = DeviceFingerprintSpoofing(context)
    private var lastFingerprintRotationTime = 0L
    private var currentFingerprint: DeviceFingerprintSpoofing.DeviceFingerprint? = null
    private var lastRequestTime = 0L

    /**
     * 检查是否处于开发者模式
     * @return 如果处于开发者模式返回true
     */
    fun isDeveloperModeEnabled(): Boolean {
        return try {
            Settings.Secure.getInt(context.contentResolver, Settings.Secure.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 检查是否安装了Xposed或其他Hook框架
     * @return 如果检测到Hook框架返回true
     */
    fun isHookFrameworkDetected(): Boolean {
        // 检查常见的Hook框架特征
        val hookPaths = listOf(
            "/system/app/XposedInstaller.apk",
            "/system/priv-app/XposedInstaller.apk",
            "/data/adb/modules",
            "/system/framework/XposedBridge.jar",
            "/system/lib/libxposed.so"
        )

        for (path in hookPaths) {
            try {
                if (java.io.File(path).exists()) {
                    return true
                }
            } catch (e: Exception) {
                // 忽略异常
            }
        }

        // 检查是否能够访问Xposed相关的类
        return try {
            Class.forName("de.robv.android.xposed.XposedBridge")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    /**
     * 检查是否安装了代理应用或VPN
     * @return 如果检测到代理或VPN返回true
     */
    fun isProxyOrVpnDetected(): Boolean {
        val proxyHost = System.getProperty("http.proxyHost")
        val proxyPort = System.getProperty("http.proxyPort")
        val httpsProxyHost = System.getProperty("https.proxyHost")
        val httpsProxyPort = System.getProperty("https.proxyPort")

        return !proxyHost.isNullOrEmpty() || !proxyPort.isNullOrEmpty() ||
                !httpsProxyHost.isNullOrEmpty() || !httpsProxyPort.isNullOrEmpty()
    }

    /**
     * 检查是否处于模拟器环境
     * @return 如果处于模拟器环境返回true
     */
    fun isEmulatorDetected(): Boolean {
        // 检查常见的模拟器特征
        val emulatorIndicators = listOf(
            "EMULATOR",
            "GENERIC",
            "UNKNOWN",
            "goldfish",
            "ranchu",
            "vbox",
            "virtualbox"
        )

        val buildModel = Build.MODEL.uppercase()
        val buildProduct = Build.PRODUCT.uppercase()
        val buildDevice = Build.DEVICE.uppercase()
        val buildHardware = Build.HARDWARE.uppercase()

        for (indicator in emulatorIndicators) {
            if (buildModel.contains(indicator) || buildProduct.contains(indicator) ||
                buildDevice.contains(indicator) || buildHardware.contains(indicator)
            ) {
                return true
            }
        }

        // 检查是否存在模拟器特定的文件
        val emulatorFiles = listOf(
            "/system/lib/libc_malloc_debug_leak.so",
            "/system/lib/libc_malloc_debug_qemu.so",
            "/system/lib/libc.so",
            "/system/lib/libOpenSLES.so",
            "/system/lib/libcamera.so"
        )

        for (file in emulatorFiles) {
            try {
                if (java.io.File(file).exists()) {
                    return true
                }
            } catch (e: Exception) {
                // 忽略异常
            }
        }

        return false
    }

    /**
     * 检查是否安装了调试工具
     * @return 如果检测到调试工具返回true
     */
    fun isDebugToolDetected(): Boolean {
        val debugTools = listOf(
            "com.android.systemui.usb",
            "com.android.settings",
            "com.android.development",
            "com.android.ddms",
            "com.android.ide.eclipse.adt"
        )

        val packageManager = context.packageManager
        for (tool in debugTools) {
            try {
                packageManager.getPackageInfo(tool, 0)
                return true
            } catch (e: Exception) {
                // 包未安装，继续检查
            }
        }

        return false
    }

    /**
     * 获取虚假的设备指纹
     * @return 虚假的设备指纹
     */
    fun getSpoofedDeviceFingerprint(): DeviceFingerprintSpoofing.DeviceFingerprint {
        return deviceFingerprintSpoofing.generateSpoofedFingerprint()
    }

    /**
     * 重置设备指纹
     */
    fun resetDeviceFingerprint() {
        deviceFingerprintSpoofing.resetFingerprint()
    }

    /**
     * 生成随机的User-Agent
     * @return 随机的User-Agent
     */
    fun generateRandomUserAgent(): String {
        val userAgents = listOf(
            "Mozilla/5.0 (Linux; Android 10; SM-G950F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36",
            "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36",
            "Mozilla/5.0 (Linux; Android 12; MI 11) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36",
            "Mozilla/5.0 (Linux; Android 13; ONEPLUS A6013) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36",
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36"
        )
        return userAgents[random.nextInt(userAgents.size)]
    }

    /**
     * 生成随机的语言
     * @return 随机的语言代码
     */
    private fun generateRandomLanguage(): String {
        val languages = listOf(
            "zh-CN,zh;q=0.9",
            "zh-CN,zh;q=0.9,en;q=0.8",
            "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7",
            "en-US,en;q=0.9",
            "en-US,en;q=0.9,zh;q=0.8"
        )
        return languages[random.nextInt(languages.size)]
    }

    /**
     * 生成随机的Referer
     * @return 随机的Referer
     */
    private fun generateRandomReferer(): String {
        val referers = listOf(
            "https://www.damai.cn/",
            "https://m.damai.cn/",
            "https://www.google.com/",
            "https://www.baidu.com/",
            "https://www.zhihu.com/",
            "https://www.weibo.com/"
        )
        return referers[random.nextInt(referers.size)]
    }

    /**
     * 生成随机的IP地址（用于伪造）
     * @return 随机的IP地址
     */
    fun generateRandomIpAddress(): String {
        return "${random.nextInt(1, 255)}.${random.nextInt(0, 256)}.${random.nextInt(0, 256)}.${random.nextInt(1, 255)}"
    }

    /**
     * 生成随机的MAC地址
     * @return 随机的MAC地址
     */
    fun generateRandomMacAddress(): String {
        return (0..5).map { (random.nextInt(0, 256)).toString(16).padStart(2, '0') }
            .joinToString(":").uppercase()
    }

    /**
     * 执行反检测扫描
     * @return 检测到的风险列表
     */
    fun performAntiDetectionScan(): List<String> {
        val risks = mutableListOf<String>()

        if (isDeveloperModeEnabled()) {
            risks.add("开发者模式已启用")
        }

        if (isHookFrameworkDetected()) {
            risks.add("检测到Hook框架")
        }

        if (isProxyOrVpnDetected()) {
            risks.add("检测到代理或VPN")
        }

        if (isEmulatorDetected()) {
            risks.add("检测到模拟器环境")
        }

        if (isDebugToolDetected()) {
            risks.add("检测到调试工具")
        }

        return risks
    }

    /**
     * 获取设备风险等级
     * @return 风险等级（0-100，0表示无风险，100表示高风险）
     */
    fun getDeviceRiskLevel(): Int {
        val risks = performAntiDetectionScan()
        return risks.size * 20 // 每个风险增加20分
    }


    // ==================== 增强功能 ====================
    
    /**
     * 生成随机的 HTTP 请求头 - 增强版
     * 包含完整的浏览器特征头，模拟真实 Chrome 浏览器
     */
    suspend fun generateRandomHeaders(): Map<String, String> = withContext(Dispatchers.Default) {
        // 检查是否需要轮换指纹
        maybeRotateFingerprint()
        
        // 使用已缓存的指纹，或生成新的
        val fingerprint = currentFingerprint ?: run {
            Log.d(TAG, "生成新设备指纹...")
            deviceFingerprintSpoofing.generateSpoofedFingerprint()
        }
        
        mapOf(
            "User-Agent" to fingerprint.userAgent,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
            "Accept-Language" to fingerprint.language,
            "Accept-Encoding" to "gzip, deflate, br",
            "Connection" to "keep-alive",
            "Upgrade-Insecure-Requests" to "1",
            "Sec-Fetch-Dest" to "document",
            "Sec-Fetch-Mode" to "navigate",
            "Sec-Fetch-Site" to "none",
            "Sec-Fetch-User" to "?1",
            "Cache-Control" to "max-age=0",
            "TE" to "trailers",
            "DNT" to "1",
            "Referer" to generateRandomReferer()
        )
    }
    
    /**
     * 检查并轮换设备指纹
     */
    private suspend fun maybeRotateFingerprint() = withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis()
        if (now - lastFingerprintRotationTime > FINGERPRINT_ROTATION_INTERVAL_MS || currentFingerprint == null) {
            Log.d(TAG, "🔄 轮换设备指纹...")
            currentFingerprint = deviceFingerprintSpoofing.generateSpoofedFingerprint()
            lastFingerprintRotationTime = now
        }
    }
    
    /**
     * 应用请求频率限制（防检测）
     */
    suspend fun applyRateLimit() = withContext(Dispatchers.Default) {
        val now = System.currentTimeMillis()
        val elapsed = now - lastRequestTime
        
        if (elapsed < MIN_REQUEST_INTERVAL_MS) {
            val waitTime = MIN_REQUEST_INTERVAL_MS - elapsed + Random.nextLong(0, MAX_REQUEST_INTERVAL_MS - MIN_REQUEST_INTERVAL_MS)
            Log.d(TAG, "⏱️ 请求频率限制：等待 ${waitTime}ms")
            kotlinx.coroutines.delay(waitTime)
        }
        
        lastRequestTime = System.currentTimeMillis()
    }
    
    /**
     * 配置防检测的 OkHttpClient
     * 禁用 HTTP/2（减少 TLS 指纹特征），启用连接复用
     */
    fun createStealthHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            // 禁用 HTTP/2，使用 HTTP/1.1（减少 TLS 指纹特征）
            .connectionSpecs(listOf(
                ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                    .tlsVersions(okhttp3.TlsVersion.TLS_1_2, okhttp3.TlsVersion.TLS_1_3)
                    .build(),
                ConnectionSpec.CLEARTEXT
            ))
            // 禁用重试（避免异常行为模式）
            .retryOnConnectionFailure(false)
            // 启用连接池复用
            .connectionPool(okhttp3.ConnectionPool(5, 5, TimeUnit.MINUTES))
            .build()
    }
    
    /**
     * 生成随机设备 ID
     */
    private fun generateRandomDeviceId(): String {
        val prefix = listOf("DM_ANDROID_", "MI_ANDROID_", "PIXEL_", "ONEPLUS_")[random.nextInt(4)]
        return prefix + System.currentTimeMillis().toString(16) + random.nextInt(1000, 9999)
    }
}
