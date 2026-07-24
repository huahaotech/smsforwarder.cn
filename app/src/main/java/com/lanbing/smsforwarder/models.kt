/*
 * 短信转发助手
 *
 * 著作权人：华昊科技有限公司
 * 开发者：王士辉
 *
 * Copyright (c) 2026 华昊科技有限公司. All rights reserved.
 * 联系邮箱：huahao@email.cn
 */

package com.lanbing.smsforwarder

/**
 * 通道和关键词规则的共享模型类型
 */
enum class ChannelType { WECHAT, DINGTALK, FEISHU, GENERIC_WEBHOOK }

/**
 * 转发错误类型
 * 用于区分不同类型的错误，实现智能重试策略
 */
enum class ForwardErrorType {
    NETWORK_TIMEOUT,      // 网络超时（可重试）
    NETWORK_UNAVAILABLE,  // 网络不可用（可重试）
    DNS_FAILURE,          // DNS解析失败（可重试）
    CONNECTION_REFUSED,   // 连接被拒绝（可重试）
    HTTP_SERVER_ERROR,    // HTTP 5xx错误（可重试）
    HTTP_RATE_LIMIT,      // HTTP 429限流（可重试）
    HTTP_CLIENT_ERROR,    // HTTP 4xx客户端错误（不应重试）
    INVALID_URL,          // URL格式错误（不应重试）
    UNKNOWN;              // 未知错误（可重试）

    /**
     * 判断该错误类型是否可重试
     */
    fun isRetryable(): Boolean {
        return when (this) {
            NETWORK_TIMEOUT,
            NETWORK_UNAVAILABLE,
            DNS_FAILURE,
            CONNECTION_REFUSED,
            HTTP_SERVER_ERROR,
            HTTP_RATE_LIMIT,
            UNKNOWN -> true
            HTTP_CLIENT_ERROR,
            INVALID_URL -> false
        }
    }

    /**
     * 获取建议的重试延迟（毫秒）
     * 根据错误类型确定合适的重试间隔
     */
    fun getRetryDelay(attempt: Int): Long {
        val baseDelay = when (this) {
            NETWORK_TIMEOUT,
            NETWORK_UNAVAILABLE,
            DNS_FAILURE,
            CONNECTION_REFUSED -> 60_000L        // 网络相关：1分钟
            HTTP_SERVER_ERROR -> 120_000L           // 服务器错误：2分钟
            HTTP_RATE_LIMIT -> 300_000L              // 限流：5分钟
            UNKNOWN -> 180_000L                     // 未知错误：3分钟
            HTTP_CLIENT_ERROR,
            INVALID_URL -> 0L                       // 不可重试
        }
        // 指数退避：每次重试延迟翻倍
        val delay = baseDelay * Math.pow(2.0, attempt.toDouble()).toLong()
        return delay.coerceAtMost(Constants.MAX_RETRY_DELAY_MS)
    }
}

data class Channel(
    val id: String,
    val name: String,
    val type: ChannelType,
    val target: String           // webhook URL
)

data class KeywordConfig(
    val id: String,
    val keyword: String, // empty string means match-all
    val channelId: String
)

data class AppConfig(
    val version: String,
    val exportTime: String,
    val channels: List<Channel>,
    val keywordConfigs: List<KeywordConfig>,
    val showReceiverPhone: Boolean,
    val showSenderPhone: Boolean,
    val highlightVerificationCode: Boolean,
    val batteryReminderEnabled: Boolean,
    val lowBatteryReminderEnabled: Boolean,
    val highBatteryReminderEnabled: Boolean,
    val chargingReminderEnabled: Boolean,
    val batteryReminderChannelId: String?,
    val lowBatteryThreshold: Int,
    val highBatteryThreshold: Int,
    val customSim1Phone: String?,
    val customSim2Phone: String?,
    val startOnBoot: Boolean
)

/**
 * 转发结果
 * 包含成功状态、错误类型和详细信息
 */
data class ForwardResult(
    val success: Boolean,
    val errorType: ForwardErrorType = ForwardErrorType.UNKNOWN,
    val errorMessage: String = ""
) {
    companion object {
        fun success(): ForwardResult = ForwardResult(true)
        fun failure(errorType: ForwardErrorType, errorMessage: String): ForwardResult = 
            ForwardResult(false, errorType, errorMessage)
    }
}