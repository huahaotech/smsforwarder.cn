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

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.lanbing.smsforwarder.Channel
import com.lanbing.smsforwarder.ChannelType
import com.lanbing.smsforwarder.Constants
import com.lanbing.smsforwarder.ExportConfig
import com.lanbing.smsforwarder.KeywordConfig
import com.lanbing.smsforwarder.LogStore
import com.lanbing.smsforwarder.R
import com.lanbing.smsforwarder.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 配置管理工具类
 *
 * 统一封装配置的导入、导出、生成、保存到下载目录等逻辑，
 * 从 MainActivity 中抽离，便于维护和测试。
 */
object ConfigManager {

    /**
     * 生成配置 JSON 字符串
     */
    fun generateConfigJson(config: ExportConfig): String {
        return JSONObject().apply {
            put("version", BuildConfig.VERSION_NAME)
            put("exportTime", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))

            val channelsArr = JSONArray()
            config.channels.forEach { ch ->
                channelsArr.put(JSONObject().apply {
                    put("id", ch.id)
                    put("name", ch.name)
                    put("type", ch.type.name)
                    put("target", ch.target)
                })
            }
            put("channels", channelsArr)

            val configsArr = JSONArray()
            config.keywordConfigs.forEach { cfg ->
                configsArr.put(JSONObject().apply {
                    put("id", cfg.id)
                    put("keyword", cfg.keyword)
                    put("channelId", cfg.channelId)
                })
            }
            put("keywordConfigs", configsArr)

            put("showReceiverPhone", config.showReceiverPhone)
            put("showSenderPhone", config.showSenderPhone)
            put("highlightVerificationCode", config.highlightVerificationCode)
            put("batteryReminderEnabled", config.batteryReminderEnabled)
            put("lowBatteryReminderEnabled", config.lowBatteryReminderEnabled)
            put("highBatteryReminderEnabled", config.highBatteryReminderEnabled)
            put("chargingReminderEnabled", config.chargingReminderEnabled)
            if (config.batteryReminderChannelId != null) put("batteryReminderChannelId", config.batteryReminderChannelId)
            put("lowBatteryThreshold", config.lowBatteryThreshold)
            put("highBatteryThreshold", config.highBatteryThreshold)
            if (config.customSim1Phone != null) put("customSim1Phone", config.customSim1Phone)
            if (config.customSim2Phone != null) put("customSim2Phone", config.customSim2Phone)
            put("startOnBoot", config.startOnBoot)
        }.toString(2) // 格式化输出，便于阅读
    }

    /**
     * 导出配置（分享文件方式）
     */
    fun exportConfig(
        context: Context,
        config: ExportConfig
    ) {
        val jsonStr = generateConfigJson(config)

        try {
            val fileName = "sms_forwarder_config_${System.currentTimeMillis()}.json"
            val fileUri = saveConfigToDownloads(context, jsonStr, fileName)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                putExtra(Intent.EXTRA_SUBJECT, "短信转发助手配置")
                putExtra(Intent.EXTRA_TEXT, "这是我的短信转发助手配置文件")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "分享配置文件")
            context.startActivity(chooser)

            Toast.makeText(context, "配置已保存到下载目录，可选择分享", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 保存配置文件到系统下载目录
     *
     * @return 文件的 Uri
     */
    fun saveConfigToDownloads(
        context: Context,
        jsonStr: String,
        fileName: String = "sms_forwarder_config_${System.currentTimeMillis()}.json"
    ): Uri {
        val jsonBytes = jsonStr.toByteArray(Charsets.UTF_8)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/json")
                put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }

            val collection = android.provider.MediaStore.Downloads.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = context.contentResolver.insert(collection, values)
                ?: throw Exception("无法创建下载目录条目")

            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(jsonBytes)
            } ?: throw Exception("无法写入文件")

            return uri
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = java.io.File(downloadsDir, fileName)
            file.writeText(jsonStr, Charsets.UTF_8)

            return FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        }
    }

    /**
     * 从 JSON 字符串导入配置
     *
     * @param context 上下文
     * @param jsonStr 配置 JSON 字符串
     * @param onImportSuccess 导入成功后的回调
     */
    fun importConfigFromJson(
        context: Context,
        jsonStr: String,
        onImportSuccess: () -> Unit
    ) {
        try {
            val json = JSONObject(jsonStr)

            val channels = mutableListOf<Channel>()
            val channelsArr = json.optJSONArray("channels") ?: JSONArray()
            for (i in 0 until channelsArr.length()) {
                val chObj = channelsArr.getJSONObject(i)
                val typeStr = chObj.optString("type", "WECHAT")
                val type = try { ChannelType.valueOf(typeStr) } catch (_: Throwable) { ChannelType.WECHAT }
                channels.add(
                    Channel(
                        id = chObj.getString("id"),
                        name = chObj.getString("name"),
                        type = type,
                        target = chObj.getString("target")
                    )
                )
            }

            val configs = mutableListOf<KeywordConfig>()
            val configsArr = json.optJSONArray("keywordConfigs") ?: JSONArray()
            for (i in 0 until configsArr.length()) {
                val cfgObj = configsArr.getJSONObject(i)
                configs.add(
                    KeywordConfig(
                        id = cfgObj.getString("id"),
                        keyword = cfgObj.getString("keyword"),
                        channelId = cfgObj.getString("channelId")
                    )
                )
            }

            val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            val editor = prefs.edit()

            ChannelLoader.saveChannels(prefs, channels)
            ChannelLoader.saveConfigs(prefs, configs)

            editor.putBoolean(Constants.PREF_SHOW_RECEIVER_PHONE, json.optBoolean("showReceiverPhone", true))
            editor.putBoolean(Constants.PREF_SHOW_SENDER_PHONE, json.optBoolean("showSenderPhone", true))
            editor.putBoolean(Constants.PREF_HIGHLIGHT_VERIFICATION_CODE, json.optBoolean("highlightVerificationCode", true))
            editor.putBoolean(Constants.PREF_BATTERY_REMINDER_ENABLED, json.optBoolean("batteryReminderEnabled", false))
            editor.putBoolean(Constants.PREF_LOW_BATTERY_REMINDER_ENABLED, json.optBoolean("lowBatteryReminderEnabled", true))
            editor.putBoolean(Constants.PREF_HIGH_BATTERY_REMINDER_ENABLED, json.optBoolean("highBatteryReminderEnabled", true))
            editor.putBoolean(Constants.PREF_CHARGING_REMINDER_ENABLED, json.optBoolean("chargingReminderEnabled", true))
            val reminderChannelId = if (json.isNull("batteryReminderChannelId")) "" else json.optString("batteryReminderChannelId", "")
            if (reminderChannelId.isEmpty()) {
                editor.remove(Constants.PREF_BATTERY_REMINDER_CHANNEL_ID)
            } else {
                editor.putString(Constants.PREF_BATTERY_REMINDER_CHANNEL_ID, reminderChannelId)
            }
            editor.putInt(Constants.PREF_LOW_BATTERY_THRESHOLD, json.optInt("lowBatteryThreshold", Constants.DEFAULT_LOW_BATTERY_THRESHOLD))
            editor.putInt(Constants.PREF_HIGH_BATTERY_THRESHOLD, json.optInt("highBatteryThreshold", Constants.DEFAULT_HIGH_BATTERY_THRESHOLD))
            editor.putBoolean(Constants.PREF_START_ON_BOOT, json.optBoolean("startOnBoot", false))

            val sim1Phone = if (json.isNull("customSim1Phone")) "" else json.optString("customSim1Phone", "")
            if (sim1Phone.isEmpty()) {
                editor.remove(Constants.PREF_CUSTOM_SIM1_PHONE)
            } else {
                editor.putString(Constants.PREF_CUSTOM_SIM1_PHONE, sim1Phone)
            }

            val sim2Phone = if (json.isNull("customSim2Phone")) "" else json.optString("customSim2Phone", "")
            if (sim2Phone.isEmpty()) {
                editor.remove(Constants.PREF_CUSTOM_SIM2_PHONE)
            } else {
                editor.putString(Constants.PREF_CUSTOM_SIM2_PHONE, sim2Phone)
            }

            editor.apply()

            LogStore.append(context, "通过配置导入成功")
            onImportSuccess()
            Toast.makeText(context, "配置导入成功", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            LogStore.append(context, "通过配置导入失败: ${e.message}")
            Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 获取通道类型的中文标签
     */
    fun getChannelTypeLabel(type: ChannelType): String = when (type) {
        ChannelType.WECHAT -> "企业微信"
        ChannelType.DINGTALK -> "钉钉"
        ChannelType.FEISHU -> "飞书"
        ChannelType.GENERIC_WEBHOOK -> "Webhook"
    }
}
