package com.damaihelper.utils

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import kotlin.random.Random

/**
 * 设备指纹伪造和反指纹技术模块
 * 负责生成和管理虚假的设备指纹，以规避大麦网的设备识别
 */
class DeviceFingerprintSpoofing(private val context: Context) {

    private val random = Random(System.currentTimeMillis())
    private var spoofedFingerprint: DeviceFingerprint? = null

    /**
     * 设备指纹数据类
     */
    data class DeviceFingerprint(
        val deviceId: String,
        val androidId: String,
        val deviceModel: String,
        val deviceBrand: String,
        val deviceManufacturer: String,
        val osVersion: String,
        val screenResolution: String,
        val screenDensity: String,
        val cpuAbi: String,
        val userAgent: String,
        val language: String,  // 添加语言字段
        val imei: String,
        val imsi: String,
        val phoneNumber: String,
        val simSerialNumber: String,
        val macAddress: String,
        val bluetoothAddress: String,
        val buildFingerprint: String,
        val buildSerial: String,
        val buildId: String,
        val buildTags: String
    )

    /**
     * 生成虚假的设备指纹
     * @return 虚假的设备指纹
     */
    fun generateSpoofedFingerprint(): DeviceFingerprint {
        if (spoofedFingerprint != null) {
            return spoofedFingerprint!!
        }

        val fingerprint = DeviceFingerprint(
            deviceId = generateRandomDeviceId(),
            androidId = generateRandomAndroidId(),
            deviceModel = generateRandomDeviceModel(),
            deviceBrand = generateRandomDeviceBrand(),
            deviceManufacturer = generateRandomManufacturer(),
            osVersion = generateRandomOsVersion(),
            screenResolution = generateRandomScreenResolution(),
            screenDensity = generateRandomScreenDensity(),
            cpuAbi = generateRandomCpuAbi(),
            userAgent = generateRandomUserAgent(),
            language = generateRandomLanguage(),
            imei = generateRandomImei(),
            imsi = generateRandomImsi(),
            phoneNumber = generateRandomPhoneNumber(),
            simSerialNumber = generateRandomSimSerialNumber(),
            macAddress = generateRandomMacAddress(),
            bluetoothAddress = generateRandomBluetoothAddress(),
            buildFingerprint = generateRandomBuildFingerprint(),
            buildSerial = generateRandomBuildSerial(),
            buildId = generateRandomBuildId(),
            buildTags = generateRandomBuildTags()
        )

        spoofedFingerprint = fingerprint
        return fingerprint
    }

    /**
     * 获取当前的虚假设备指纹
     * @return 虚假的设备指纹，如果未生成则返回null
     */
    fun getSpoofedFingerprint(): DeviceFingerprint? {
        return spoofedFingerprint
    }

    /**
     * 重置设备指纹（生成新的虚假指纹）
     */
    fun resetFingerprint() {
        spoofedFingerprint = null
    }

    /**
     * 获取真实的设备ID
     * @return 真实的设备ID
     */
    fun getRealDeviceId(): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    /**
     * 生成随机的设备ID
     * @return 随机的设备ID
     */
    private fun generateRandomDeviceId(): String {
        return (0..15).map { (random.nextInt(0, 16)).toString(16) }.joinToString("")
    }

    /**
     * 生成随机的Android ID
     * @return 随机的Android ID
     */
    private fun generateRandomAndroidId(): String {
        return (0..15).map { (random.nextInt(0, 16)).toString(16) }.joinToString("")
    }

    /**
     * 生成随机的设备型号
     * @return 随机的设备型号
     */
    private fun generateRandomDeviceModel(): String {
        val models = listOf(
            "SM-G950F", "SM-G960F", "SM-G970F", "SM-G980F", "SM-G990F",
            "Pixel 3", "Pixel 4", "Pixel 5", "Pixel 6", "Pixel 7",
            "ONEPLUS A6003", "ONEPLUS A6013", "ONEPLUS A6013",
            "MI 8", "MI 9", "MI 10", "MI 11"
        )
        return models[random.nextInt(models.size)]
    }

    /**
     * 生成随机的设备品牌
     * @return 随机的设备品牌
     */
    private fun generateRandomDeviceBrand(): String {
        val brands = listOf("samsung", "google", "oneplus", "xiaomi", "huawei", "oppo", "vivo")
        return brands[random.nextInt(brands.size)]
    }

    /**
     * 生成随机的制造商
     * @return 随机的制造商
     */
    private fun generateRandomManufacturer(): String {
        val manufacturers = listOf("Samsung", "Google", "OnePlus", "Xiaomi", "Huawei", "OPPO", "Vivo")
        return manufacturers[random.nextInt(manufacturers.size)]
    }

    /**
     * 生成随机的OS版本
     * @return 随机的OS版本
     */
    private fun generateRandomOsVersion(): String {
        val versions = listOf("10", "11", "12", "13", "14")
        return versions[random.nextInt(versions.size)]
    }

    /**
     * 生成随机的屏幕分辨率
     * @return 随机的屏幕分辨率
     */
    private fun generateRandomScreenResolution(): String {
        val resolutions = listOf(
            "1080x2340", "1080x2400", "1440x3120", "1440x3200",
            "1080x2160", "1440x2960", "1080x1920"
        )
        return resolutions[random.nextInt(resolutions.size)]
    }

    /**
     * 生成随机的屏幕密度
     * @return 随机的屏幕密度
     */
    private fun generateRandomScreenDensity(): String {
        val densities = listOf("420", "480", "560", "640")
        return densities[random.nextInt(densities.size)]
    }

    /**
     * 生成随机的CPU ABI
     * @return 随机的CPU ABI
     */
    private fun generateRandomCpuAbi(): String {
        val abis = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        return abis[random.nextInt(abis.size)]
    }

    /**
     * 生成随机的User-Agent
     * @return 随机的User-Agent
     */
    private fun generateRandomUserAgent(): String {
        val userAgents = listOf(
            "Mozilla/5.0 (Linux; Android 10; SM-G950F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36",
            "Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36",
            "Mozilla/5.0 (Linux; Android 12; MI 11) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36",
            "Mozilla/5.0 (Linux; Android 13; ONEPLUS A6013) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36"
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
            "zh-CN,zh;q=0.9,en-US;q=0.8",
            "en-US,en;q=0.9",
            "en-US,en;q=0.9,zh-CN;q=0.8"
        )
        return languages[random.nextInt(languages.size)]
    }

    /**
     * 生成随机的IMEI
     * @return 随机的IMEI
     */
    private fun generateRandomImei(): String {
        // IMEI格式：15位数字
        return (0..14).map { random.nextInt(0, 10) }.joinToString("")
    }

    /**
     * 生成随机的IMSI
     * @return 随机的IMSI
     */
    private fun generateRandomImsi(): String {
        // IMSI格式：15位数字
        return (0..14).map { random.nextInt(0, 10) }.joinToString("")
    }

    /**
     * 生成随机的电话号码
     * @return 随机的电话号码
     */
    private fun generateRandomPhoneNumber(): String {
        return "1" + (0..9).map { random.nextInt(0, 10) }.joinToString("")
    }

    /**
     * 生成随机的SIM卡序列号
     * @return 随机的SIM卡序列号
     */
    private fun generateRandomSimSerialNumber(): String {
        return (0..19).map { (random.nextInt(0, 16)).toString(16) }.joinToString("")
    }

    /**
     * 生成随机的MAC地址
     * @return 随机的MAC地址
     */
    private fun generateRandomMacAddress(): String {
        return (0..5).map { (random.nextInt(0, 256)).toString(16).padStart(2, '0') }
            .joinToString(":").uppercase()
    }

    /**
     * 生成随机的蓝牙地址
     * @return 随机的蓝牙地址
     */
    private fun generateRandomBluetoothAddress(): String {
        return (0..5).map { (random.nextInt(0, 256)).toString(16).padStart(2, '0') }
            .joinToString(":").uppercase()
    }

    /**
     * 生成随机的Build指纹
     * @return 随机的Build指纹
     */
    private fun generateRandomBuildFingerprint(): String {
        val brand = generateRandomDeviceBrand()
        val model = generateRandomDeviceModel()
        val version = generateRandomOsVersion()
        val buildId = generateRandomBuildId()
        return "$brand/$model:$version/$buildId"
    }

    /**
     * 生成随机的Build序列号
     * @return 随机的Build序列号
     */
    private fun generateRandomBuildSerial(): String {
        return (0..15).map { (random.nextInt(0, 36)).toString(36) }.joinToString("").uppercase()
    }

    /**
     * 生成随机的Build ID
     * @return 随机的Build ID
     */
    private fun generateRandomBuildId(): String {
        return (0..7).map { (random.nextInt(0, 36)).toString(36) }.joinToString("").uppercase()
    }

    /**
     * 生成随机的Build标签
     * @return 随机的Build标签
     */
    private fun generateRandomBuildTags(): String {
        val tags = listOf("release-keys", "userdebug", "user")
        return tags[random.nextInt(tags.size)]
    }

    /**
     * 获取设备指纹的哈希值（用于比较）
     * @param fingerprint 设备指纹
     * @return 哈希值
     */
    fun getFingerprintHash(fingerprint: DeviceFingerprint): String {
        val fingerprintString = fingerprint.toString()
        return fingerprintString.hashCode().toString()
    }
}

