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

import android.util.Log
import com.lanbing.smsforwarder.KeywordConfig
import com.lanbing.smsforwarder.MatchLogic
import com.lanbing.smsforwarder.MatchMode
import com.lanbing.smsforwarder.SenderMatchMode

/**
 * 消息匹配引擎
 *
 * 统一封装短信内容匹配、发送者过滤、多关键词组合逻辑等匹配规则。
 * 支持：
 * 1. 多种内容匹配模式：包含、精确、正则、排除
 * 2. 多关键词组合逻辑：AND / OR
 * 3. 发送者过滤：包含、精确、通配符、正则
 *
 * 内置正则表达式缓存，避免每次匹配都重新编译。
 */
object MessageMatcher {

    private const val TAG = "MessageMatcher"

    // 正则表达式缓存：pattern string -> compiled Regex
    private val regexCache = mutableMapOf<String, Regex>()

    /**
     * 判断一条关键词规则是否匹配给定的短信
     *
     * @param config 关键词规则配置
     * @param content 短信内容
     * @param sender 发送者号码
     * @return 是否匹配（命中则转发）
     */
    fun matches(config: KeywordConfig, content: String, sender: String): Boolean {
        // 规则未启用，直接不匹配
        if (!config.enabled) return false

        // 第一步：发送者过滤（不通过则整条规则不匹配）
        if (!matchesSender(config, sender)) return false

        // 第二步：内容匹配
        return matchesContent(config, content)
    }

    // ==================== 发送者匹配 ====================

    private fun matchesSender(config: KeywordConfig, sender: String): Boolean {
        val pattern = config.senderPattern
        if (pattern.isNullOrBlank()) return true // 未设置发送者过滤，直接通过

        // 归一化发送者号码（去除国家代码前缀、格式字符等），提高匹配成功率
        val normalizedSender = normalizePhoneNumber(sender)

        return try {
            when (config.senderMatchMode) {
                SenderMatchMode.CONTAINS -> normalizedSender.contains(pattern, ignoreCase = true)
                SenderMatchMode.EXACT -> normalizedSender.equals(pattern, ignoreCase = true)
                SenderMatchMode.WILDCARD -> matchWildcard(pattern, normalizedSender)
                SenderMatchMode.REGEX -> getOrCompileRegex(pattern).containsMatchIn(normalizedSender)
            }
        } catch (e: Exception) {
            Log.e(TAG, "发送者匹配失败，模式=${config.senderMatchMode}，pattern=$pattern", e)
            false // 匹配异常时保守不通过
        }
    }

    // ==================== 内容匹配 ====================

    private fun matchesContent(config: KeywordConfig, content: String): Boolean {
        val allKeywords = getAllKeywords(config)

        // 没有关键词 = 匹配所有（match-all）
        if (allKeywords.isEmpty()) return true

        val results = allKeywords.map { kw -> matchSingleKeyword(kw, config.matchMode, content) }

        return when (config.matchLogic) {
            MatchLogic.OR -> results.any { it }
            MatchLogic.AND -> results.all { it }
        }
    }

    /**
     * 获取规则的全部关键词列表（主关键词 + 额外关键词，过滤空白）
     */
    fun getAllKeywords(config: KeywordConfig): List<String> {
        val result = mutableListOf<String>()
        if (config.keyword.isNotBlank()) {
            result.add(config.keyword.trim())
        }
        config.extraKeywords.forEach { kw ->
            if (kw.isNotBlank()) {
                result.add(kw.trim())
            }
        }
        return result
    }

    private fun matchSingleKeyword(keyword: String, mode: MatchMode, content: String): Boolean {
        return try {
            when (mode) {
                MatchMode.CONTAINS -> content.contains(keyword, ignoreCase = true)
                MatchMode.EXACT -> content.equals(keyword, ignoreCase = true)
                MatchMode.REGEX -> getOrCompileRegex(keyword).containsMatchIn(content)
                MatchMode.EXCLUDE -> !content.contains(keyword, ignoreCase = true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "关键词匹配失败，模式=$mode，keyword=$keyword", e)
            false // 匹配异常时保守不通过
        }
    }

    // ==================== 号码归一化 ====================

    /**
     * 归一化电话号码，用于匹配前的统一处理
     *
     * 处理内容：
     * 1. 去除所有空格、横杠、括号等格式字符（保留数字和 +）
     * 2. 去除中国国家代码前缀 +86 / 0086
     * 3. 去除开头的 +（未知国家代码的情况也尽量兼容）
     *
     * 这样用户设置黑名单/白名单时不需要考虑国家代码前缀，
     * 直接写 139*、106* 等即可匹配带 +86 前缀的号码。
     */
    fun normalizePhoneNumber(phone: String): String {
        // 先去除空格、横杠、括号等常见格式字符，保留数字和 +
        var normalized = phone.replace(Regex("[\\s\\-()\\[\\]{}]"), "")

        // 去除中国国家代码前缀 +86 或 0086
        if (normalized.startsWith("+86")) {
            normalized = normalized.removePrefix("+86")
        } else if (normalized.startsWith("0086")) {
            normalized = normalized.removePrefix("0086")
        }

        // 如果还有其他 + 开头（未知国家代码），也去掉 + 尽量兼容
        if (normalized.startsWith("+")) {
            normalized = normalized.removePrefix("+")
        }

        return normalized
    }

    // ==================== 通配符匹配 ====================

    /**
     * 通配符匹配：* 代表任意字符（包括空字符），? 代表单个字符
     * 不区分大小写
     */
    fun matchWildcard(pattern: String, text: String): Boolean {
        val regexPattern = buildString {
            append('^')
            for (ch in pattern) {
                when (ch) {
                    '*' -> append(".*")
                    '?' -> append('.')
                    '.', '+', '(', ')', '[', ']', '{', '}', '\\', '^', '$', '|' -> {
                        append('\\')
                        append(ch)
                    }
                    else -> append(ch)
                }
            }
            append('$')
        }
        return try {
            Regex(regexPattern, RegexOption.IGNORE_CASE).matches(text)
        } catch (e: Exception) {
            Log.e(TAG, "通配符匹配失败，pattern=$pattern", e)
            false
        }
    }

    // ==================== 全局发送者过滤 ====================

    /**
     * 全局发送者白名单/黑名单过滤
     *
     * @param sender 发送者号码
     * @param whitelist 白名单模式列表（null/空表示不启用白名单）
     * @param blacklist 黑名单模式列表（null/空表示不启用黑名单）
     * @param whitelistMode 白名单匹配模式
     * @param blacklistMode 黑名单匹配模式
     * @return true 表示通过过滤（应该继续处理），false 表示被过滤掉
     */
    fun passesGlobalSenderFilter(
        sender: String,
        whitelist: List<String>?,
        blacklist: List<String>?,
        whitelistMode: SenderMatchMode = SenderMatchMode.WILDCARD,
        blacklistMode: SenderMatchMode = SenderMatchMode.WILDCARD
    ): Boolean {
        // 归一化发送者号码（去除国家代码前缀、格式字符等），提高匹配成功率
        val normalizedSender = normalizePhoneNumber(sender)

        // 白名单优先：启用了白名单，只有命中白名单的才通过
        if (!whitelist.isNullOrEmpty()) {
            val inWhitelist = whitelist.any { pattern ->
                try {
                    when (whitelistMode) {
                        SenderMatchMode.CONTAINS -> normalizedSender.contains(pattern, ignoreCase = true)
                        SenderMatchMode.EXACT -> normalizedSender.equals(pattern, ignoreCase = true)
                        SenderMatchMode.WILDCARD -> matchWildcard(pattern, normalizedSender)
                        SenderMatchMode.REGEX -> getOrCompileRegex(pattern).containsMatchIn(normalizedSender)
                    }
                } catch (e: Exception) {
                    false
                }
            }
            if (!inWhitelist) return false
        }

        // 黑名单：命中黑名单则不通过
        if (!blacklist.isNullOrEmpty()) {
            val inBlacklist = blacklist.any { pattern ->
                try {
                    when (blacklistMode) {
                        SenderMatchMode.CONTAINS -> normalizedSender.contains(pattern, ignoreCase = true)
                        SenderMatchMode.EXACT -> normalizedSender.equals(pattern, ignoreCase = true)
                        SenderMatchMode.WILDCARD -> matchWildcard(pattern, normalizedSender)
                        SenderMatchMode.REGEX -> getOrCompileRegex(pattern).containsMatchIn(normalizedSender)
                    }
                } catch (e: Exception) {
                    false
                }
            }
            if (inBlacklist) return false
        }

        return true
    }

    // ==================== 全局内容过滤 ====================

    /**
     * 全局内容白名单/黑名单过滤
     *
     * @param content 短信内容
     * @param whitelist 内容白名单模式列表（null/空表示不启用白名单）
     * @param blacklist 内容黑名单模式列表（null/空表示不启用黑名单）
     * @param whitelistMode 白名单匹配模式（默认通配符）
     * @param blacklistMode 黑名单匹配模式（默认通配符）
     * @return true 表示通过过滤（应该继续处理），false 表示被过滤掉
     */
    fun passesGlobalContentFilter(
        content: String,
        whitelist: List<String>?,
        blacklist: List<String>?,
        whitelistMode: SenderMatchMode = SenderMatchMode.WILDCARD,
        blacklistMode: SenderMatchMode = SenderMatchMode.WILDCARD
    ): Boolean {
        // 内容白名单优先：启用了白名单，只有命中白名单的才通过
        if (!whitelist.isNullOrEmpty()) {
            val inWhitelist = whitelist.any { pattern ->
                try {
                    when (whitelistMode) {
                        SenderMatchMode.CONTAINS -> content.contains(pattern, ignoreCase = true)
                        SenderMatchMode.EXACT -> content.equals(pattern, ignoreCase = true)
                        SenderMatchMode.WILDCARD -> matchWildcard(pattern, content)
                        SenderMatchMode.REGEX -> getOrCompileRegex(pattern).containsMatchIn(content)
                    }
                } catch (e: Exception) {
                    false
                }
            }
            if (!inWhitelist) return false
        }

        // 内容黑名单：命中黑名单则不通过
        if (!blacklist.isNullOrEmpty()) {
            val inBlacklist = blacklist.any { pattern ->
                try {
                    when (blacklistMode) {
                        SenderMatchMode.CONTAINS -> content.contains(pattern, ignoreCase = true)
                        SenderMatchMode.EXACT -> content.equals(pattern, ignoreCase = true)
                        SenderMatchMode.WILDCARD -> matchWildcard(pattern, content)
                        SenderMatchMode.REGEX -> getOrCompileRegex(pattern).containsMatchIn(content)
                    }
                } catch (e: Exception) {
                    false
                }
            }
            if (inBlacklist) return false
        }

        return true
    }

    // ==================== 正则缓存 ====================

    /**
     * 获取或编译正则表达式，带缓存
     */
    private fun getOrCompileRegex(pattern: String): Regex {
        return regexCache.getOrPut(pattern) {
            Regex(pattern, RegexOption.IGNORE_CASE)
        }
    }

    /**
     * 清空正则缓存（配置变更时调用，防止内存泄漏）
     */
    fun clearRegexCache() {
        regexCache.clear()
    }
}
