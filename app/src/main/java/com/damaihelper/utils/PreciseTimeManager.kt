package com.damaihelper.utils

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import kotlin.math.abs

/**
 * 精确时间管理器 - 基于 SNTP 协议
 * 单例模式，提供毫秒级时间同步
 */
object PreciseTimeManager {
    private const val TAG = "PreciseTimeManager"
    private const val NTP_HOST = "ntp.aliyun.com"  // 阿里云 NTP
    private const val NTP_PORT = 123
    private const val TIMEOUT_MS = 5000

    @Volatile
    private var isSynced = false

    @Volatile
    private var localOffset: Long = 0L  // 本地时间与真实时间的偏移量

    /**
     * 初始化 NTP 时间同步（简化实现）
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (isSynced) {
            Log.d(TAG, "NTP 已同步，跳过初始化")
            return@withContext
        }

        try {
            Log.i(TAG, "开始 NTP 时间同步...")

            val ntpTime = fetchNTPTime(NTP_HOST)
            if (ntpTime > 0) {
                localOffset = ntpTime - System.currentTimeMillis()
                isSynced = true
                Log.i(TAG, "✓ NTP 同步成功！偏移量: ${localOffset}ms")
            } else {
                throw IOException("NTP 返回无效时间")
            }
        } catch (e: Exception) {
            Log.e(TAG, "× NTP 同步失败，将使用本地时间", e)
            isSynced = false
            localOffset = 0L
        }
    }

    /**
     * 简化的 SNTP 协议实现
     */
    private fun fetchNTPTime(host: String): Long {
        return try {
            val address = InetAddress.getByName(host)
            val channel = DatagramChannel.open()
            channel.socket().soTimeout = TIMEOUT_MS

            // 构造 NTP 请求包（48 字节）
            val request = ByteBuffer.allocate(48)
            request.put(0, 0x1B.toByte())  // LI=0, VN=3, Mode=3

            // 发送请求
            channel.send(request, InetSocketAddress(address, NTP_PORT))

            // 接收响应
            val response = ByteBuffer.allocate(48)
            channel.receive(response)
            channel.close()

            // 解析时间戳（字节 40-43 是秒，44-47 是小数秒）
            response.position(40)
            val seconds = response.int.toLong() and 0xFFFFFFFFL
            val fraction = response.int.toLong() and 0xFFFFFFFFL

            // NTP 时间从 1900 年开始，转换为 Unix 时间戳（从 1970 年开始）
            val ntpEpochOffset = 2208988800L  // 1900 到 1970 的秒数
            val unixSeconds = seconds - ntpEpochOffset
            val milliseconds = (fraction * 1000L) / 0x100000000L

            unixSeconds * 1000 + milliseconds
        } catch (e: Exception) {
            Log.e(TAG, "NTP 请求失败", e)
            -1L
        }
    }

    /**
     * 获取精确时间戳（毫秒）
     */
    fun getPreciseTime(): Long {
        return System.currentTimeMillis() + localOffset
    }

    /**
     * 精确等待到指定时间（核心方法）
     * @param targetTime 目标时间戳（毫秒）
     */
    suspend fun waitUntilPreciseTime(targetTime: Long) {
        Log.i(TAG, "========== 精确等待开始 ==========")

        // 1. 粗等待（提前 100ms）
        var remaining = targetTime - getPreciseTime()
        if (remaining > 100) {
            val coarseWait = remaining - 100
            Log.d(TAG, "粗等待: ${coarseWait}ms")
            delay(coarseWait)
        }

        // 2. 精确自旋等待（最后 100ms）
        val spinStart = System.currentTimeMillis()
        while (getPreciseTime() < targetTime) {
            // 主动让出 CPU，避免 100% 占用
            if (System.currentTimeMillis() - spinStart < 50) {
                delay(1)  // 前 50ms 用协程延迟
            }
            // 最后 50ms 进入紧密自旋
        }

        val actualTime = getPreciseTime()
        val error = actualTime - targetTime

        Log.i(TAG, "========== 精确等待完成 ==========")
        Log.i(TAG, "目标时间: $targetTime")
        Log.i(TAG, "实际时间: $actualTime")
        Log.i(TAG, "误差: ${error}ms")

        if (abs(error) > 50) {
            Log.w(TAG, "⚠️ 时间误差过大！可能影响抢票成功率")
        }
    }

    /**
     * 检查同步状态
     */
    fun isSynchronized(): Boolean = isSynced

    /**
     * 获取时间偏移量
     */
    fun getOffset(): Long = localOffset
}