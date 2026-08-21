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

/**
 * 验证码提取工具类
 *
 * 从短信内容中自动识别并提取验证码，采用三级匹配策略：
 * 1. 关键词紧邻匹配：关键词后直接跟数字
 * 2. 关键词附近匹配：关键词后一定范围内的数字
 * 3. 独立数字匹配：匹配所有符合长度的数字，返回最长的
 */
object VerificationCodeExtractor {

    // 常见的验证码关键词
    private val keywords = listOf(
        "验证码", "校验码", "动态码", "验证 code", "verification code", "verify code"
    )

    /**
     * 从短信内容中提取验证码
     *
     * 匹配常见验证码格式：4-8位数字，可能带有"验证码"、"校验码"等关键词
     *
     * @param content 短信内容
     * @return 提取到的验证码，如果没有找到则返回 null
     */
    fun extract(content: String): String? {
        val hasKeyword = keywords.any { content.contains(it, ignoreCase = true) }

        // 优先匹配：关键词后紧跟的 4-8 位数字
        if (hasKeyword) {
            // 模式1：关键词后面直接跟数字（如"验证码是123456"）
            val pattern1 = Regex(
                """(?:验证码|校验码|动态码|验证|verification|verify)[^\d]*(\d{4,8})""",
                RegexOption.IGNORE_CASE
            )
            pattern1.find(content)?.let { return it.groupValues[1] }

            // 模式2：关键词附近的数字（关键词后30字符内）
            val pattern2 = Regex(
                """(?:验证码|校验码|动态码|验证|verification|verify).{0,30}?(\d{4,8})""",
                RegexOption.IGNORE_CASE
            )
            pattern2.find(content)?.let { return it.groupValues[1] }
        }

        // 匹配独立的4-8位数字（作为备选）
        val pattern3 = Regex("""\b(\d{4,8})\b""")
        val matches = pattern3.findAll(content).map { it.groupValues[1] }.toList()

        // 如果有多个匹配，返回最长的那个（更可能是验证码）
        if (matches.isNotEmpty()) {
            return matches.maxByOrNull { it.length }
        }

        return null
    }
}
