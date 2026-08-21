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
import org.json.JSONArray
import org.json.JSONObject

/**
 * 通道与关键词配置加载工具类
 *
 * 统一封装从 SharedPreferences 加载通道配置和关键词规则的逻辑，
 * 消除 SmsReceiver 和 SmsForegroundService 中的重复代码。
 */
object ChannelLoader {

    /**
     * 从 SharedPreferences 加载所有通道配置
     */
    fun loadChannels(prefs: SharedPreferences): List<Channel> {
        val arrStr = prefs.getString(Constants.PREF_CHANNELS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(arrStr)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val typeStr = o.optString("type", "WECHAT")
                val type = try { ChannelType.valueOf(typeStr) } catch (t: Throwable) { ChannelType.WECHAT }
                Channel(o.getString("id"), o.getString("name"), type, o.getString("target"))
            }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    /**
     * 从 SharedPreferences 加载所有关键词配置
     */
    fun loadConfigs(prefs: SharedPreferences): List<KeywordConfig> {
        val arrStr = prefs.getString(Constants.PREF_KEYWORD_CONFIGS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(arrStr)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                KeywordConfig(o.getString("id"), o.getString("keyword"), o.getString("channelId"))
            }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    /**
     * 保存通道配置到 SharedPreferences
     */
    fun saveChannels(prefs: SharedPreferences, channels: List<Channel>) {
        val arr = JSONArray()
        channels.forEach {
            val o = JSONObject()
            o.put("id", it.id)
            o.put("name", it.name)
            o.put("type", it.type.name)
            o.put("target", it.target)
            arr.put(o)
        }
        prefs.edit().putString(Constants.PREF_CHANNELS, arr.toString()).apply()
    }

    /**
     * 保存关键词配置到 SharedPreferences
     */
    fun saveConfigs(prefs: SharedPreferences, configs: List<KeywordConfig>) {
        val arr = JSONArray()
        configs.forEach {
            val o = JSONObject()
            o.put("id", it.id)
            o.put("keyword", it.keyword)
            o.put("channelId", it.channelId)
            arr.put(o)
        }
        prefs.edit().putString(Constants.PREF_KEYWORD_CONFIGS, arr.toString()).apply()
    }
}
