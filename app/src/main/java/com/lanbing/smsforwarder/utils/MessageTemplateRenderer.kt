/*
 * 短信转发助手
 *
 * 著作权人：华昊科技有限公司
 * 开发者：王士辉
 *
 * Copyright (c) 2026 华昊科技有限公司. All rights reserved.
 * 联系邮箱：huahao@email.cn
 */

package com.lanbing.smsforwarder.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 消息模板渲染器
 *
 * 支持用户自定义转发消息模板，通过占位符替换实现动态内容。
 *
 * 可用占位符：
 * - {sender}       发送者号码
 * - {content}      短信内容
 * - {time}         接收时间（yyyy-MM-dd HH:mm:ss）
 * - {sim}          接收 SIM 卡号码（本机号码）
 * - {code}         提取的验证码（未提取到则为空字符串）
 * - {keyword}      命中的关键词
 * - {channel}      通道名称
 *
 * 默认模板（内容为空时使用）：
 *  文本通道："{content}"
 *  带验证码："验证码: {code}\n{content}"
 */
object MessageTemplateRenderer {

    private const val TAG = "MessageTemplateRenderer"

    // 模板参数
    data class TemplateParams(
        val sender: String,
        val content: String,
        val receiverPhone: String? = null,
        val verificationCode: String? = null,
        val matchedKeyword: String? = null,
        val channelName: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    )

    /**
     * 渲染消息模板
     *
     * @param template 模板字符串，null 或空则返回原始内容
     * @param params 模板参数
     * @return 渲染后的消息文本
     */
    fun render(template: String?, params: TemplateParams): String {
        if (template.isNullOrBlank()) return params.content

        val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(params.timestamp))

        return template
            .replace("{sender}", params.sender)
            .replace("{content}", params.content)
            .replace("{time}", timeStr)
            .replace("{sim}", params.receiverPhone ?: "")
            .replace("{code}", params.verificationCode ?: "")
            .replace("{keyword}", params.matchedKeyword ?: "")
            .replace("{channel}", params.channelName ?: "")
    }

    /**
     * 默认的短信转发消息（保留旧的构建逻辑，作为无自定义模板时的回退）
     *
     * @param params 模板参数
     * @param showSenderPhone 是否显示发送方号码
     * @param highlightVerificationCode 是否突出显示验证码
     * @return 格式化的消息文本
     */
    fun buildDefaultMessage(
        params: TemplateParams,
        showSenderPhone: Boolean,
        highlightVerificationCode: Boolean
    ): String {
        val parts = mutableListOf<String>()

        if (highlightVerificationCode && !params.verificationCode.isNullOrBlank()) {
            parts.add("验证码: ${params.verificationCode}")
        }
        if (params.receiverPhone != null) {
            parts.add("本机: ${params.receiverPhone}")
        }
        if (showSenderPhone) {
            parts.add("来自: ${params.sender}")
        }
        parts.add(params.content)

        return parts.joinToString("\n")
    }

    /**
     * 验证模板字符串是否有效（目前只检查基本格式）
     *
     * @return true 表示模板有效
     */
    fun validateTemplate(template: String?): Boolean {
        if (template == null) return true // null 表示使用默认模板
        // 检查是否有未闭合的占位符（简单检查）
        val openBraces = template.count { it == '{' }
        val closeBraces = template.count { it == '}' }
        return openBraces == closeBraces
    }

    /**
     * 获取所有支持的占位符说明（用于 UI 提示）
     */
    fun getPlaceholderHints(): List<Pair<String, String>> = listOf(
        "{sender}" to "发送者号码",
        "{content}" to "短信内容",
        "{time}" to "接收时间",
        "{sim}" to "本机号码/SIM 卡",
        "{code}" to "验证码（提取到的）",
        "{keyword}" to "命中的关键词",
        "{channel}" to "通道名称"
    )

    /**
     * 预置模板列表（用于 UI 快速选择）
     */
    fun getPresetTemplates(): List<Pair<String, String>> = listOf(
        "简洁模式" to "{content}",
        "含发送者" to "来自: {sender}\n{content}",
        "验证码优先" to "验证码: {code}\n{content}",
        "完整信息" to "【短信转发】\n来自: {sender}\n时间: {time}\n\n{content}",
        "仅验证码" to "{code}",
        "本机+内容" to "本机: {sim}\n{content}"
    )
}
