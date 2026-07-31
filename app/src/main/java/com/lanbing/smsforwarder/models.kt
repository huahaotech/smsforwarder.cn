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
 * 只区分可重试和不可重试两类
 */
enum class ForwardErrorType {
    RETRYABLE,     // 可重试（网络错误、超时、5xx等）
    NON_RETRYABLE  // 不可重试（4xx、URL无效等）
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