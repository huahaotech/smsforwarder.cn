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

import org.json.JSONObject

/**
 * 通道和关键词规则的共享模型类型
 */
enum class ChannelType { WECHAT, DINGTALK, FEISHU, GENERIC_WEBHOOK }

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
 * 集中化的通道消息构建器
 */
object ChannelMessageBuilder {

    fun buildSimpleMessage(type: ChannelType, message: String): JSONObject {
        return when (type) {
            ChannelType.WECHAT -> buildWechat(message)
            ChannelType.DINGTALK -> buildDingtalk(message)
            ChannelType.FEISHU -> buildFeishu(message)
            ChannelType.GENERIC_WEBHOOK -> buildGeneric(message)
        }
    }

    fun buildFullMessage(
        type: ChannelType,
        sender: String,
        content: String,
        receiverPhoneNumber: String?,
        showSenderPhone: Boolean,
        highlightVerificationCode: Boolean
    ): JSONObject {
        val message = buildDisplayMessage(sender, content, receiverPhoneNumber, showSenderPhone, highlightVerificationCode)
        return when (type) {
            ChannelType.WECHAT -> buildWechat(message)
            ChannelType.DINGTALK -> buildDingtalk(message)
            ChannelType.FEISHU -> buildFeishu(message)
            ChannelType.GENERIC_WEBHOOK -> buildGenericFull(sender, content, receiverPhoneNumber, showSenderPhone, highlightVerificationCode)
        }
    }

    private fun buildWechat(message: String): JSONObject {
        val json = JSONObject()
        json.put("msgtype", "text")
        val text = JSONObject()
        text.put("content", message)
        json.put("text", text)
        return json
    }

    private fun buildDingtalk(message: String): JSONObject {
        val json = JSONObject()
        json.put("msgtype", "text")
        val text = JSONObject()
        text.put("content", message)
        json.put("text", text)
        return json
    }

    private fun buildFeishu(message: String): JSONObject {
        val json = JSONObject()
        json.put("msg_type", "text")
        val text = JSONObject()
        text.put("text", message)
        json.put("content", text)
        return json
    }

    private fun buildGeneric(message: String): JSONObject {
        val json = JSONObject()
        json.put("message", message)
        return json
    }

    private fun buildGenericFull(
        sender: String,
        content: String,
        receiverPhoneNumber: String?,
        showSenderPhone: Boolean,
        highlightVerificationCode: Boolean
    ): JSONObject {
        val json = JSONObject()
        if (showSenderPhone) {
            json.put("sender", sender)
        }
        if (receiverPhoneNumber != null) {
            json.put("receiver", receiverPhoneNumber)
        }
        json.put("content", content)
        if (highlightVerificationCode) {
            json.put("verificationCode", extractVerificationCode(content))
        }
        json.put("timestamp", System.currentTimeMillis())
        return json
    }

    private fun buildDisplayMessage(
        sender: String,
        content: String,
        receiverPhoneNumber: String?,
        showSenderPhone: Boolean,
        highlightVerificationCode: Boolean
    ): String {
        val parts = mutableListOf<String>()
        val code = if (highlightVerificationCode) extractVerificationCode(content) else null

        if (code != null) {
            parts.add("验证码: $code")
        }
        if (receiverPhoneNumber != null) {
            parts.add("本机: $receiverPhoneNumber")
        }
        if (showSenderPhone) {
            parts.add("来自: $sender")
        }
        parts.add(content)

        return parts.joinToString("\n")
    }

    private fun extractVerificationCode(content: String): String? {
        val keywords = listOf("验证码", "校验码", "动态码", "验证 code", "verification code", "verify code")
        val hasKeyword = keywords.any { content.contains(it, ignoreCase = true) }

        if (hasKeyword) {
            val pattern1 = Regex("""(?:验证码|校验码|动态码|验证|verification|verify)[^\d]*(\d{4,8})""", RegexOption.IGNORE_CASE)
            pattern1.find(content)?.let { return it.groupValues[1] }

            val pattern2 = Regex("""(?:验证码|校验码|动态码|验证|verification|verify).{0,30}?(\d{4,8})""", RegexOption.IGNORE_CASE)
            pattern2.find(content)?.let { return it.groupValues[1] }
        }

        val pattern3 = Regex("""\b(\d{4,8})\b""")
        val matches = pattern3.findAll(content).map { it.groupValues[1] }.toList()
        if (matches.isNotEmpty()) {
            return matches.maxByOrNull { it.length }
        }

        return null
    }
}
