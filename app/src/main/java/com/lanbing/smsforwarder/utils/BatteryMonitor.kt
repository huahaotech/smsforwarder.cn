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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.util.Log
import com.lanbing.smsforwarder.ChannelType
import com.lanbing.smsforwarder.Constants
import com.lanbing.smsforwarder.LogStore
import java.util.concurrent.Executors

/**
 * 电量与充电状态监控器
 *
 * 从 SmsForegroundService 中抽离的独立模块，负责：
 * - 监听电量变化，触发低电量/高电量提醒
 * - 监听充电状态变化，触发充电开始/结束提醒
 * - 获取 SIM 卡信息用于标识设备
 *
 * 使用方式：
 * 1. 创建实例并传入 Context
 * 2. 调用 register() 开始监听
 * 3. 调用 unregister() 停止监听
 */
class BatteryMonitor(private val context: Context) {

    companion object {
        private const val TAG = "BatteryMonitor"
        private val executor = Executors.newFixedThreadPool(Constants.THREAD_POOL_SIZE)
    }

    private var isRegistered = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                if (intent == null || context == null) return
                val action = intent.action
                if (action != Intent.ACTION_BATTERY_CHANGED) return

                val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                val batteryEnabled = prefs.getBoolean(Constants.PREF_BATTERY_REMINDER_ENABLED, false)
                val chargingReminderEnabled = prefs.getBoolean(Constants.PREF_CHARGING_REMINDER_ENABLED, false)

                if (!batteryEnabled && !chargingReminderEnabled) {
                    return
                }

                val lowBatteryReminderEnabled = prefs.getBoolean(Constants.PREF_LOW_BATTERY_REMINDER_ENABLED, true)
                val highBatteryReminderEnabled = prefs.getBoolean(Constants.PREF_HIGH_BATTERY_REMINDER_ENABLED, true)
                val lowThreshold = prefs.getInt(Constants.PREF_LOW_BATTERY_THRESHOLD, Constants.DEFAULT_LOW_BATTERY_THRESHOLD)
                val highThreshold = prefs.getInt(Constants.PREF_HIGH_BATTERY_THRESHOLD, Constants.DEFAULT_HIGH_BATTERY_THRESHOLD)

                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level == -1 || scale == -1) {
                    Log.w(TAG, "无法获取电量信息")
                    return
                }

                val batteryPercent = (level * 100 / scale)
                val lastLowRemind = prefs.getInt(Constants.PREF_LAST_LOW_BATTERY_REMIND_LEVEL, -1)
                val lastHighRemind = prefs.getInt(Constants.PREF_LAST_HIGH_BATTERY_REMIND_LEVEL, -1)

                val phoneInfo = SimInfoUtils.getSimPhoneInfo(context, prefs)

                // 低电量提醒
                if (batteryEnabled && lowBatteryReminderEnabled && batteryPercent <= lowThreshold) {
                    if (lastLowRemind == -1 || lastLowRemind > lowThreshold) {
                        var message = "【电量提醒】当前电量：$batteryPercent%，电量较低，请及时充电"
                        if (phoneInfo.isNotEmpty()) {
                            message += "\n设备：$phoneInfo"
                        }
                        sendBatteryReminder(context, message)
                        prefs.edit().putInt(Constants.PREF_LAST_LOW_BATTERY_REMIND_LEVEL, batteryPercent).apply()
                        LogStore.append(context, "电量提醒：低电量 $batteryPercent%")
                    }
                } else if (batteryEnabled) {
                    if (lastLowRemind != -1) {
                        prefs.edit().remove(Constants.PREF_LAST_LOW_BATTERY_REMIND_LEVEL).apply()
                    }
                }

                // 高电量提醒
                if (batteryEnabled && highBatteryReminderEnabled && batteryPercent >= highThreshold) {
                    if (lastHighRemind == -1 || lastHighRemind < highThreshold) {
                        var message = "【电量提醒】当前电量：$batteryPercent%，电量充足"
                        if (phoneInfo.isNotEmpty()) {
                            message += "\n设备：$phoneInfo"
                        }
                        sendBatteryReminder(context, message)
                        prefs.edit().putInt(Constants.PREF_LAST_HIGH_BATTERY_REMIND_LEVEL, batteryPercent).apply()
                        LogStore.append(context, "电量提醒：高电量 $batteryPercent%")
                    }
                } else if (batteryEnabled) {
                    if (lastHighRemind != -1) {
                        prefs.edit().remove(Constants.PREF_LAST_HIGH_BATTERY_REMIND_LEVEL).apply()
                    }
                }

                // 充电状态变化监测（独立于电量提醒主开关）
                if (chargingReminderEnabled) {
                    val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
                    val isCharging = plugged != 0 || status == BatteryManager.BATTERY_STATUS_CHARGING
                    val lastChargingState = prefs.getBoolean(Constants.PREF_LAST_CHARGING_STATE, false)

                    if (isCharging && !lastChargingState) {
                        val chargeType = when {
                            plugged == BatteryManager.BATTERY_PLUGGED_AC -> "AC充电"
                            plugged == BatteryManager.BATTERY_PLUGGED_USB -> "USB充电"
                            plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS -> "无线充电"
                            status == BatteryManager.BATTERY_STATUS_CHARGING -> "充电"
                            else -> "充电"
                        }
                        var message = "【充电提醒】设备已开始${chargeType}，当前电量：$batteryPercent%"
                        if (phoneInfo.isNotEmpty()) {
                            message += "\n设备：$phoneInfo"
                        }
                        sendBatteryReminder(context, message)
                        prefs.edit().putBoolean(Constants.PREF_LAST_CHARGING_STATE, true).apply()
                        LogStore.append(context, "充电提醒：已开始${chargeType}，电量 $batteryPercent%")
                    } else if (!isCharging && lastChargingState) {
                        var message = "【充电提醒】设备已结束充电，当前电量：$batteryPercent%"
                        if (phoneInfo.isNotEmpty()) {
                            message += "\n设备：$phoneInfo"
                        }
                        sendBatteryReminder(context, message)
                        prefs.edit().putBoolean(Constants.PREF_LAST_CHARGING_STATE, false).apply()
                        LogStore.append(context, "充电提醒：已结束充电，电量 $batteryPercent%")
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "处理电量变化失败", t)
            }
        }
    }

    /**
     * 注册电量监听器
     *
     * @param initializeChargingState 是否初始化充电状态（首次注册时调用）
     */
    fun register(initializeChargingState: Boolean = false) {
        if (isRegistered) return
        try {
            val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            context.registerReceiver(batteryReceiver, batteryFilter)
            isRegistered = true
            if (initializeChargingState) {
                initChargingState()
            }
            LogStore.append(context, "电量监听器已注册")
        } catch (t: Throwable) {
            Log.w(TAG, "注册电量监听器失败", t)
        }
    }

    /**
     * 注销电量监听器
     */
    fun unregister() {
        if (!isRegistered) return
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (_: Exception) { /* already unregistered */ }
        isRegistered = false
        LogStore.append(context, "电量监听器已注销")
    }

    /**
     * 是否已注册
     */
    fun isActive(): Boolean = isRegistered

    /**
     * 初始化充电状态（首次注册时调用，避免初始状态误触发提醒）
     */
    private fun initChargingState() {
        try {
            val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            val chargingReminderEnabled = prefs.getBoolean(Constants.PREF_CHARGING_REMINDER_ENABLED, false)
            if (!chargingReminderEnabled) return

            val lastStateInitialized = prefs.getBoolean(Constants.PREF_LAST_CHARGING_STATE_INITIALIZED, false)
            if (lastStateInitialized) return

            val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val isCharging = if (batteryIntent != null) {
                val plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
                plugged != 0 || status == BatteryManager.BATTERY_STATUS_CHARGING
            } else {
                false
            }

            prefs.edit()
                .putBoolean(Constants.PREF_LAST_CHARGING_STATE, isCharging)
                .putBoolean(Constants.PREF_LAST_CHARGING_STATE_INITIALIZED, true)
                .apply()
            LogStore.append(context, "初始化充电状态: isCharging=$isCharging")
        } catch (t: Throwable) {
            Log.w(TAG, "初始化充电状态失败", t)
        }
    }

    /**
     * 发送电量/充电提醒消息到配置的通道
     */
    private fun sendBatteryReminder(context: Context, message: String) {
        executor.execute {
            try {
                val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                val channels = ChannelLoader.loadChannels(prefs)
                if (channels.isEmpty()) {
                    LogStore.append(context, "电量提醒：未配置转发通道，提醒无法发送，请先配置通道")
                    return@execute
                }

                val reminderChannelId = prefs.getString(Constants.PREF_BATTERY_REMINDER_CHANNEL_ID, null)
                val targetChannels = if (reminderChannelId.isNullOrEmpty()) {
                    channels
                } else {
                    val filtered = channels.filter { it.id == reminderChannelId }
                    if (filtered.isEmpty()) {
                        LogStore.append(context, "电量提醒：指定的通道不存在，已跳过")
                        channels
                    } else {
                        filtered
                    }
                }

                if (targetChannels.isEmpty()) {
                    LogStore.append(context, "电量提醒：无可用通道，已跳过")
                    return@execute
                }

                targetChannels.forEach { channel ->
                    try {
                        val result = WebhookSender.sendTextMessage(channel.target, message, channel.type)
                        if (result.success) {
                            LogStore.append(context, "电量提醒发送成功 -> ${channel.name}")
                        } else {
                            LogStore.append(context, "电量提醒发送失败 -> ${channel.name}: ${result.errorMessage}")
                        }
                    } catch (t: Throwable) {
                        Log.w(TAG, "发送到 ${channel.name} 失败", t)
                        LogStore.append(context, "电量提醒发送失败 -> ${channel.name}: ${t.message ?: t.javaClass.simpleName}")
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "发送电量提醒失败", t)
            }
        }
    }
}
