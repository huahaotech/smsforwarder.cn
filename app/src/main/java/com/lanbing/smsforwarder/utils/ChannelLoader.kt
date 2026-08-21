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

import android.content.SharedPreferences
import com.lanbing.smsforwarder.Channel
import com.lanbing.smsforwarder.ChannelType
import com.lanbing.smsforwarder.Constants
import com.lanbing.smsforwarder.KeywordConfig
import com.lanbing.smsforwarder.MatchLogic
import com.lanbing.smsforwarder.MatchMode
import com.lanbing.smsforwarder.SenderMatchMode
import org.json.JSONArray
import org.json.JSONObject

/**
 * 通道与关键词配置加载工具类
 *
 * 统一封装从 SharedPreferences 加载通道配置和关键词规则的逻辑，
 * 内置内存缓存避免每次都解析 JSON，提升短信接收时的性能。
 */
object ChannelLoader {

    // 内存缓存
    private var cachedChannels: List<Channel>? = null
    private var cachedConfigs: List<KeywordConfig>? = null
    private var cachedChannelMap: Map<String, Channel>? = null
    private var lastPrefsHash: Int = 0

    /**
     * 从 SharedPreferences 加载所有通道配置（带内存缓存）
     */
    fun loadChannels(prefs: SharedPreferences): List<Channel> {
        val currentValue = prefs.getString(Constants.PREF_CHANNELS, "[]") ?: "[]"
        val hash = currentValue.hashCode()
        if (cachedChannels != null && lastPrefsHash == hash) {
            return cachedChannels!!
        }
        val channels = parseChannels(currentValue)
        cachedChannels = channels
        cachedChannelMap = channels.associateBy { it.id }
        lastPrefsHash = hash
        return channels
    }

    /**
     * 获取通道 Map（id -> Channel），用于 O(1) 查找
     */
    fun getChannelMap(prefs: SharedPreferences): Map<String, Channel> {
        loadChannels(prefs) // 确保缓存已刷新
        return cachedChannelMap ?: emptyMap()
    }

    /**
     * 从 SharedPreferences 加载所有关键词配置（带内存缓存）
     */
    fun loadConfigs(prefs: SharedPreferences): List<KeywordConfig> {
        val currentValue = prefs.getString(Constants.PREF_KEYWORD_CONFIGS, "[]") ?: "[]"
        // 使用独立的缓存变量避免与 channels 混用
        val hash = currentValue.hashCode()
        if (cachedConfigs != null && cachedConfigsHash == hash) {
            return cachedConfigs!!
        }
        val configs = parseConfigs(currentValue)
        cachedConfigs = configs
        cachedConfigsHash = hash
        return configs
    }

    // configs 的缓存哈希
    private var cachedConfigsHash: Int = 0

    private fun parseChannels(arrStr: String): List<Channel> {
        return try {
            val arr = JSONArray(arrStr)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val typeStr = o.optString("type", "WECHAT")
                val type = try { ChannelType.valueOf(typeStr) } catch (t: Throwable) { ChannelType.WECHAT }
                Channel(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    type = type,
                    target = o.getString("target"),
                    messageTemplate = o.optString("messageTemplate", null)?.takeIf { it.isNotBlank() }
                )
            }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    private fun parseConfigs(arrStr: String): List<KeywordConfig> {
        return try {
            val arr = JSONArray(arrStr)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val matchModeStr = o.optString("matchMode", "CONTAINS")
                val matchMode = try { MatchMode.valueOf(matchModeStr) } catch (_: Throwable) { MatchMode.CONTAINS }
                val matchLogicStr = o.optString("matchLogic", "OR")
                val matchLogic = try { MatchLogic.valueOf(matchLogicStr) } catch (_: Throwable) { MatchLogic.OR }
                val senderMatchModeStr = o.optString("senderMatchMode", "CONTAINS")
                val senderMatchMode = try { SenderMatchMode.valueOf(senderMatchModeStr) } catch (_: Throwable) { SenderMatchMode.CONTAINS }

                // 解析 extraKeywords 数组（旧版本没有此字段时为空列表）
                val extraKeywords = mutableListOf<String>()
                val extraArr = o.optJSONArray("extraKeywords")
                if (extraArr != null) {
                    for (j in 0 until extraArr.length()) {
                        extraKeywords.add(extraArr.getString(j))
                    }
                }

                KeywordConfig(
                    id = o.getString("id"),
                    keyword = o.getString("keyword"),
                    channelId = o.getString("channelId"),
                    matchMode = matchMode,
                    matchLogic = matchLogic,
                    extraKeywords = extraKeywords,
                    senderPattern = o.optString("senderPattern", null)?.takeIf { it.isNotBlank() },
                    senderMatchMode = senderMatchMode,
                    enabled = o.optBoolean("enabled", true)
                )
            }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    /**
     * 保存通道配置到 SharedPreferences（同时更新缓存）
     */
    fun saveChannels(prefs: SharedPreferences, channels: List<Channel>) {
        val arr = JSONArray()
        channels.forEach {
            val o = JSONObject()
            o.put("id", it.id)
            o.put("name", it.name)
            o.put("type", it.type.name)
            o.put("target", it.target)
            if (it.messageTemplate != null) o.put("messageTemplate", it.messageTemplate)
            arr.put(o)
        }
        val jsonStr = arr.toString()
        prefs.edit().putString(Constants.PREF_CHANNELS, jsonStr).apply()
        // 直接更新缓存，避免下次读取重新解析
        cachedChannels = channels
        cachedChannelMap = channels.associateBy { it.id }
        lastPrefsHash = jsonStr.hashCode()
    }

    /**
     * 保存关键词配置到 SharedPreferences（同时更新缓存）
     */
    fun saveConfigs(prefs: SharedPreferences, configs: List<KeywordConfig>) {
        val arr = JSONArray()
        configs.forEach {
            val o = JSONObject()
            o.put("id", it.id)
            o.put("keyword", it.keyword)
            o.put("channelId", it.channelId)
            o.put("matchMode", it.matchMode.name)
            o.put("matchLogic", it.matchLogic.name)
            // extraKeywords 数组
            val extraArr = JSONArray()
            it.extraKeywords.forEach { kw -> extraArr.put(kw) }
            o.put("extraKeywords", extraArr)
            if (it.senderPattern != null) o.put("senderPattern", it.senderPattern)
            o.put("senderMatchMode", it.senderMatchMode.name)
            o.put("enabled", it.enabled)
            arr.put(o)
        }
        val jsonStr = arr.toString()
        prefs.edit().putString(Constants.PREF_KEYWORD_CONFIGS, jsonStr).apply()
        cachedConfigs = configs
        cachedConfigsHash = jsonStr.hashCode()
        // 关键词变更，清理正则缓存
        MessageMatcher.clearRegexCache()
    }

    /**
     * 手动清除缓存（如配置变更后需要强制重新加载）
     */
    fun clearCache() {
        cachedChannels = null
        cachedConfigs = null
        cachedChannelMap = null
        lastPrefsHash = 0
        cachedConfigsHash = 0
    }
}
