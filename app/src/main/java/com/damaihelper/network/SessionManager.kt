package com.damaihelper.network

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import java.util.*

/**
 * 会话管理器
 * 负责管理用户的登录状态、访问令牌、刷新令牌等会话信息
 */
class SessionManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "session_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val gson = Gson()

    /**
     * 保存会话信息
     */
    fun saveSession(session: Session) {
        encryptedPrefs.edit().apply {
            putString("session", gson.toJson(session))
            putLong("session_created_time", System.currentTimeMillis())
            apply()
        }
    }

    /**
     * 获取当前会话信息
     */
    fun getSession(): Session? {
        val sessionJson = encryptedPrefs.getString("session", null) ?: return null
        return try {
            gson.fromJson(sessionJson, Session::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 保存访问令牌
     */
    fun saveAccessToken(accessToken: String, expiresIn: Long = 3600) {
        encryptedPrefs.edit().apply {
            putString("access_token", accessToken)
            putLong("access_token_expires_at", System.currentTimeMillis() + expiresIn * 1000)
            apply()
        }
    }

    /**
     * 获取访问令牌
     */
    fun getAccessToken(): String? {
        val expiresAt = encryptedPrefs.getLong("access_token_expires_at", 0)
        if (expiresAt > 0 && System.currentTimeMillis() > expiresAt) {
            // 令牌已过期
            return null
        }
        return encryptedPrefs.getString("access_token", null)
    }

    /**
     * 保存刷新令牌
     */
    fun saveRefreshToken(refreshToken: String) {
        encryptedPrefs.edit().apply {
            putString("refresh_token", refreshToken)
            apply()
        }
    }

    /**
     * 获取刷新令牌
     */
    fun getRefreshToken(): String? {
        return encryptedPrefs.getString("refresh_token", null)
    }

    /**
     * 保存用户信息
     */
    fun saveUserInfo(userInfo: UserInfo) {
        encryptedPrefs.edit().apply {
            putString("user_info", gson.toJson(userInfo))
            apply()
        }
    }

    /**
     * 获取用户信息
     */
    fun getUserInfo(): UserInfo? {
        val userInfoJson = encryptedPrefs.getString("user_info", null) ?: return null
        return try {
            gson.fromJson(userInfoJson, UserInfo::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 检查访问令牌是否有效
     */
    fun isAccessTokenValid(): Boolean {
        val expiresAt = encryptedPrefs.getLong("access_token_expires_at", 0)
        return expiresAt > 0 && System.currentTimeMillis() < expiresAt
    }

    /**
     * 检查是否已登录
     */
    fun isLoggedIn(): Boolean {
        return getAccessToken() != null || getRefreshToken() != null
    }

    /**
     * 清除所有会话信息
     */
    fun clearSession() {
        encryptedPrefs.edit().apply {
            clear()
            apply()
        }
    }

    /**
     * 获取会话的创建时间
     */
    fun getSessionCreatedTime(): Long {
        return encryptedPrefs.getLong("session_created_time", 0)
    }

    /**
     * 会话数据类
     */
    data class Session(
        val userId: String,
        val username: String,
        val accessToken: String,
        val refreshToken: String,
        val expiresIn: Long,
        val tokenType: String = "Bearer",
        val createdAt: Long = System.currentTimeMillis()
    )

    /**
     * 用户信息数据类
     */
    data class UserInfo(
        val userId: String,
        val username: String,
        val email: String?,
        val phone: String?,
        val realName: String?,
        val idType: String?,
        val idNumber: String?,
        val avatar: String?,
        val vipLevel: Int = 0,
        val createdAt: Long = System.currentTimeMillis()
    )
}

