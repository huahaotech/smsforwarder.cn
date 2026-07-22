package com.lanbing.smsforwarder

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap

object ConfigCache {
    private val configCache = ConcurrentHashMap<String, Any>()
    private var lastUpdateTime = 0L
    private const val CACHE_TTL_MS = 5000L

    @Synchronized
    fun getAppConfig(context: Context): AppConfig {
        val cacheKey = "app_config"
        val now = System.currentTimeMillis()
        
        if (configCache.containsKey(cacheKey) && (now - lastUpdateTime) < CACHE_TTL_MS) {
            return configCache[cacheKey] as AppConfig
        }

        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val config = loadAppConfig(prefs)
        
        configCache[cacheKey] = config
        lastUpdateTime = now
        return config
    }

    @Synchronized
    fun getChannels(context: Context): List<Channel> {
        val cacheKey = "channels"
        val now = System.currentTimeMillis()
        
        if (configCache.containsKey(cacheKey) && (now - lastUpdateTime) < CACHE_TTL_MS) {
            @Suppress("UNCHECKED_CAST")
            return configCache[cacheKey] as List<Channel>
        }

        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val channels = loadChannels(prefs)
        
        configCache[cacheKey] = channels
        lastUpdateTime = now
        return channels
    }

    @Synchronized
    fun getConfigs(context: Context): List<KeywordConfig> {
        val cacheKey = "configs"
        val now = System.currentTimeMillis()
        
        if (configCache.containsKey(cacheKey) && (now - lastUpdateTime) < CACHE_TTL_MS) {
            @Suppress("UNCHECKED_CAST")
            return configCache[cacheKey] as List<KeywordConfig>
        }

        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val configs = loadConfigs(prefs)
        
        configCache[cacheKey] = configs
        lastUpdateTime = now
        return configs
    }

    @Synchronized
    fun invalidate() {
        configCache.clear()
        lastUpdateTime = 0
    }

    private fun loadAppConfig(prefs: SharedPreferences): AppConfig {
        return AppConfig(
            enabled = prefs.getBoolean(Constants.PREF_ENABLED, false),
            startOnBoot = prefs.getBoolean(Constants.PREF_START_ON_BOOT, false),
            showReceiverPhone = prefs.getBoolean(Constants.PREF_SHOW_RECEIVER_PHONE, true),
            showSenderPhone = prefs.getBoolean(Constants.PREF_SHOW_SENDER_PHONE, true),
            highlightVerificationCode = prefs.getBoolean(Constants.PREF_HIGHLIGHT_VERIFICATION_CODE, true),
            batteryReminderEnabled = prefs.getBoolean(Constants.PREF_BATTERY_REMINDER_ENABLED, false),
            lowBatteryReminderEnabled = prefs.getBoolean(Constants.PREF_LOW_BATTERY_REMINDER_ENABLED, true),
            highBatteryReminderEnabled = prefs.getBoolean(Constants.PREF_HIGH_BATTERY_REMINDER_ENABLED, true),
            chargingReminderEnabled = prefs.getBoolean(Constants.PREF_CHARGING_REMINDER_ENABLED, false),
            batteryReminderChannelId = prefs.getString(Constants.PREF_BATTERY_REMINDER_CHANNEL_ID, null),
            lowBatteryThreshold = prefs.getInt(Constants.PREF_LOW_BATTERY_THRESHOLD, Constants.DEFAULT_LOW_BATTERY_THRESHOLD),
            highBatteryThreshold = prefs.getInt(Constants.PREF_HIGH_BATTERY_THRESHOLD, Constants.DEFAULT_HIGH_BATTERY_THRESHOLD),
            customSim1Phone = prefs.getString(Constants.PREF_CUSTOM_SIM1_PHONE, null),
            customSim2Phone = prefs.getString(Constants.PREF_CUSTOM_SIM2_PHONE, null)
        )
    }

    private fun loadChannels(prefs: SharedPreferences): List<Channel> {
        val arrStr = prefs.getString(Constants.PREF_CHANNELS, "[]") ?: "[]"
        return try {
            val arr = org.json.JSONArray(arrStr)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val typeStr = o.optString("type", "WECHAT")
                val type = try { ChannelType.valueOf(typeStr) } catch (_: Throwable) { ChannelType.WECHAT }
                Channel(o.getString("id"), o.getString("name"), type, o.getString("target"))
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun loadConfigs(prefs: SharedPreferences): List<KeywordConfig> {
        val arrStr = prefs.getString(Constants.PREF_KEYWORD_CONFIGS, "[]") ?: "[]"
        return try {
            val arr = org.json.JSONArray(arrStr)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                KeywordConfig(o.getString("id"), o.getString("keyword"), o.getString("channelId"))
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }
}