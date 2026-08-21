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
 * 关键词匹配模式
 *
 * 控制一条规则中关键词与短信内容的匹配方式。
 */
enum class MatchMode {
    CONTAINS,   // 包含匹配（默认，向后兼容）
    EXACT,      // 精确匹配
    REGEX,      // 正则表达式匹配
    EXCLUDE     // 排除匹配（不包含该关键词才转发）
}

/**
 * 发送者匹配模式
 */
enum class SenderMatchMode {
    CONTAINS,   // 包含匹配
    EXACT,      // 精确匹配
    WILDCARD,   // 通配符匹配（* 代表任意字符）
    REGEX       // 正则表达式匹配
}

/**
 * 多关键词组合逻辑
 */
enum class MatchLogic {
    OR,     // 命中任意一个关键词即匹配（默认，向后兼容）
    AND     // 必须同时命中所有关键词才匹配
}

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
    val target: String,           // webhook URL
    val messageTemplate: String? = null  // 自定义消息模板，null 表示使用默认模板
)

data class KeywordConfig(
    val id: String,
    val keyword: String,          // 主关键词，empty string means match-all
    val channelId: String,
    val matchMode: MatchMode = MatchMode.CONTAINS,   // 内容匹配模式
    val matchLogic: MatchLogic = MatchLogic.OR,      // 多关键词组合逻辑
    val extraKeywords: List<String> = emptyList(),   // 额外关键词列表（用于 AND/OR 组合）
    val senderPattern: String? = null,               // 发送者匹配模式，null 表示不过滤
    val senderMatchMode: SenderMatchMode = SenderMatchMode.CONTAINS, // 发送者匹配模式
    val enabled: Boolean = true                      // 规则是否启用
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
    val errorType: ForwardErrorType = ForwardErrorType.RETRYABLE,
    val errorMessage: String = ""
) {
    companion object {
        fun success(): ForwardResult = ForwardResult(true)
        fun failure(errorType: ForwardErrorType, errorMessage: String): ForwardResult = 
            ForwardResult(false, errorType, errorMessage)
    }
}