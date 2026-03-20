package com.damaihelper.config

/**
 * 第三方验证码识别服务的配置信息。
 * 请替换以下占位符为您在各平台注册获得的真实凭证。
 */
object CaptchaConfig {

    // --- 超级鹰 (Chaojiying) 配置 ---
    // 请从您的超级鹰账户后台获取以下信息
    const val CHAOJIYING_USER = "dahuangbuchirou" // 您的用户名
    const val CHAOJIYING_PASS = "dztf6w25" // 您的密码
    const val CHAOJIYING_SOFT_ID = "974229" // 您的软件ID

    // --- 云码 (Yunma) 配置 ---
    // 请从您的云码账户后台获取以下信息
    const val YUNMA_TOKEN = "PkbpJg_VYrHINBR9Nmo9BfrwGeklnyEJlibcyJZISDE" // 您的API Token/密钥

    // --- 2Captcha 配置 ---
    // 请从您的 2Captcha 账户后台获取以下信息
    const val TWOCAPTCHA_API_KEY = "c5bc1c96c9da5df25f3309ac33d9ea73" // 您的 API Key

    // 更多配置...
    // 如果您未来添加其他服务，请在此处扩展
}