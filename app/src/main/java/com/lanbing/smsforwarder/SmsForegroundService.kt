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

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.app.PendingIntent
import android.provider.Settings
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

class SmsForegroundService : Service() {

    companion object {
        const val ACTION_UPDATE = "com.lanbing.smsforwarder.ACTION_LOG_UPDATED"
        const val ACTION_STOP = "com.lanbing.smsforwarder.ACTION_STOP_SERVICE"
        private const val TAG = "SmsForegroundService"
        private const val TAG_BATTERY = "BatteryReceiver"
        private var lastNotificationUpdateTime = 0L
        
        // 固定线程池避免线程爆炸
        private val executor = Executors.newFixedThreadPool(Constants.THREAD_POOL_SIZE)

        @Volatile
        private var cachedConfig = BatteryConfig()
    }

    data class BatteryConfig(
        val batteryEnabled: Boolean = false,
        val chargingReminderEnabled: Boolean = false,
        val lowBatteryReminderEnabled: Boolean = true,
        val highBatteryReminderEnabled: Boolean = true,
        val lowThreshold: Int = Constants.DEFAULT_LOW_BATTERY_THRESHOLD,
        val highThreshold: Int = Constants.DEFAULT_HIGH_BATTERY_THRESHOLD,
        val batteryReminderChannelId: String = ""
    )

    private fun refreshBatteryConfig() {
        try {
            val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            cachedConfig = BatteryConfig(
                batteryEnabled = prefs.getBoolean(Constants.PREF_BATTERY_REMINDER_ENABLED, false),
                chargingReminderEnabled = prefs.getBoolean(Constants.PREF_CHARGING_REMINDER_ENABLED, false),
                lowBatteryReminderEnabled = prefs.getBoolean(Constants.PREF_LOW_BATTERY_REMINDER_ENABLED, true),
                highBatteryReminderEnabled = prefs.getBoolean(Constants.PREF_HIGH_BATTERY_REMINDER_ENABLED, true),
                lowThreshold = prefs.getInt(Constants.PREF_LOW_BATTERY_THRESHOLD, Constants.DEFAULT_LOW_BATTERY_THRESHOLD),
                highThreshold = prefs.getInt(Constants.PREF_HIGH_BATTERY_THRESHOLD, Constants.DEFAULT_HIGH_BATTERY_THRESHOLD),
                batteryReminderChannelId = prefs.getString(Constants.PREF_BATTERY_REMINDER_CHANNEL_ID, "") ?: ""
            )
        } catch (t: Throwable) {
            Log.w(TAG, "刷新电量配置失败", t)
        }
    }

    private val updateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                val action = intent?.action
                if (action == ACTION_STOP) {
                    stopSelf()
                    LogStore.append(applicationContext, "收到通知停止服务请求，服务已停止")
                    return
                }
                refreshBatteryConfig()
                updateNotification()
            } catch (t: Throwable) {
                Log.w(TAG, "更新通知失败", t)
            }
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                if (intent == null || context == null) return
                val action = intent.action
                if (action != Intent.ACTION_BATTERY_CHANGED) return

                val config = cachedConfig
                if (!config.batteryEnabled && !config.chargingReminderEnabled) {
                    Log.d(TAG_BATTERY, "所有电量提醒未开启，已跳过")
                    return
                }

                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level == -1 || scale == -1) {
                    Log.w(TAG_BATTERY, "无法获取电量信息")
                    return
                }

                val batteryPercent = (level * 100 / scale)
                val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                val lastLowRemind = prefs.getInt(Constants.PREF_LAST_LOW_BATTERY_REMIND_LEVEL, -1)
                val lastHighRemind = prefs.getInt(Constants.PREF_LAST_HIGH_BATTERY_REMIND_LEVEL, -1)

                val phoneInfo = getSimPhoneInfo(context, prefs)

                // 低电量提醒
                if (config.batteryEnabled && config.lowBatteryReminderEnabled && batteryPercent <= config.lowThreshold) {
                    if (lastLowRemind == -1 || lastLowRemind > config.lowThreshold) {
                        var message = "【电量提醒】当前电量：$batteryPercent%，电量较低，请及时充电"
                        if (phoneInfo.isNotEmpty()) {
                            message += "\n设备：$phoneInfo"
                        }
                        sendBatteryReminder(context, message)
                        prefs.edit().putInt(Constants.PREF_LAST_LOW_BATTERY_REMIND_LEVEL, batteryPercent).apply()
                        LogStore.append(context, "电量提醒：低电量 $batteryPercent%")
                    }
                } else if (config.batteryEnabled) {
                    if (lastLowRemind != -1) {
                        prefs.edit().remove(Constants.PREF_LAST_LOW_BATTERY_REMIND_LEVEL).apply()
                    }
                }

                // 高电量提醒
                if (config.batteryEnabled && config.highBatteryReminderEnabled && batteryPercent >= config.highThreshold) {
                    if (lastHighRemind == -1 || lastHighRemind < config.highThreshold) {
                        var message = "【电量提醒】当前电量：$batteryPercent%，电量充足"
                        if (phoneInfo.isNotEmpty()) {
                            message += "\n设备：$phoneInfo"
                        }
                        sendBatteryReminder(context, message)
                        prefs.edit().putInt(Constants.PREF_LAST_HIGH_BATTERY_REMIND_LEVEL, batteryPercent).apply()
                        LogStore.append(context, "电量提醒：高电量 $batteryPercent%")
                    }
                } else if (config.batteryEnabled) {
                    if (lastHighRemind != -1) {
                        prefs.edit().remove(Constants.PREF_LAST_HIGH_BATTERY_REMIND_LEVEL).apply()
                    }
                }

                // 充电状态变化监测（独立于电量提醒主开关）
                if (config.chargingReminderEnabled) {
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
                Log.e(TAG_BATTERY, "处理电量变化失败", t)
            }
        }
    }

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private fun sendBatteryReminder(context: Context, message: String) {
        executor.execute {
            try {
                val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                val channels = loadChannels(prefs)
                if (channels.isEmpty()) {
                    LogStore.append(context, "电量提醒：未配置转发通道，提醒无法发送，请先配置通道")
                    return@execute
                }

                val targetChannels = if (cachedConfig.batteryReminderChannelId.isNotEmpty()) {
                    val filtered = channels.filter { it.id == cachedConfig.batteryReminderChannelId }
                    if (filtered.isEmpty()) {
                        LogStore.append(context, "电量提醒：指定的通道不存在，已跳过")
                        channels
                    } else {
                        filtered
                    }
                } else {
                    channels
                }

                if (targetChannels.isEmpty()) {
                    LogStore.append(context, "电量提醒：无可用通道，已跳过")
                    return@execute
                }

                targetChannels.forEach { channel ->
                    try {
                        val jsonObject = ChannelMessageBuilder.buildSimpleMessage(channel.type, message)
                        val body = jsonObject.toString().toRequestBody(JSON)
                        val request = Request.Builder()
                            .url(channel.target)
                            .post(body)
                            .build()

                        SmsReceiver.client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                LogStore.append(context, "电量提醒发送成功 -> ${channel.name}")
                            } else {
                                val errorBody = try { response.body?.string()?.take(200) } catch (_: Exception) { "无法读取响应" }
                                LogStore.append(context, "电量提醒发送失败 -> ${channel.name}: HTTP ${response.code} ${errorBody ?: ""}")
                            }
                        }
                    } catch (e: java.net.SocketTimeoutException) {
                        LogStore.append(context, "电量提醒发送失败 -> ${channel.name}: 连接超时")
                    } catch (e: java.net.UnknownHostException) {
                        LogStore.append(context, "电量提醒发送失败 -> ${channel.name}: 域名解析失败")
                    } catch (e: java.io.IOException) {
                        LogStore.append(context, "电量提醒发送失败 -> ${channel.name}: 网络错误: ${e.message}")
                    } catch (t: Throwable) {
                        Log.w(TAG_BATTERY, "发送到 ${channel.name} 失败", t)
                        LogStore.append(context, "电量提醒发送失败 -> ${channel.name}: ${t.message ?: t.javaClass.simpleName}")
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG_BATTERY, "发送电量提醒失败", t)
            }
        }
    }

    private fun loadChannels(prefs: android.content.SharedPreferences): List<Channel> {
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

    private fun getSimPhoneInfo(context: Context, prefs: android.content.SharedPreferences): String {
        val phoneNumbers = mutableListOf<String>()
        
        // 优先使用自定义的 SIM 卡号码
        val customSim1Phone = prefs.getString(Constants.PREF_CUSTOM_SIM1_PHONE, null)
        val customSim2Phone = prefs.getString(Constants.PREF_CUSTOM_SIM2_PHONE, null)
        
        if (!customSim1Phone.isNullOrBlank()) {
            phoneNumbers.add(customSim1Phone)
        }
        if (!customSim2Phone.isNullOrBlank()) {
            phoneNumbers.add(customSim2Phone)
        }
        
        // 如果有自定义号码，直接返回
        if (phoneNumbers.isNotEmpty()) {
            return phoneNumbers.joinToString(" / ")
        }
        
        // 尝试自动获取 SIM 卡号码
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return ""
        }
        
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
                @Suppress("DEPRECATION")
                val subscriptionManager = SubscriptionManager.from(context)
                val activeSubscriptions = subscriptionManager.activeSubscriptionInfoList
                if (activeSubscriptions != null) {
                    activeSubscriptions.forEach { subInfo ->
                        try {
                            @Suppress("DEPRECATION")
                            if (subInfo != null && !subInfo.number.isNullOrBlank()) {
                                @Suppress("DEPRECATION")
                                phoneNumbers.add(subInfo.number)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG_BATTERY, "获取 SIM 卡号码失败", e)
                        }
                    }
                }
            }
            
            // 如果没有从 SubscriptionManager 获取到，尝试从 TelephonyManager 获取
            if (phoneNumbers.isEmpty()) {
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                if (telephonyManager != null) {
                    @Suppress("DEPRECATION")
                    val number = telephonyManager.line1Number
                    if (!number.isNullOrBlank()) {
                        phoneNumbers.add(number)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG_BATTERY, "获取 SIM 卡信息失败", e)
        }
        
        return if (phoneNumbers.isNotEmpty()) {
            phoneNumbers.joinToString(" / ")
        } else {
            ""
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        refreshBatteryConfig()
        startFailedMessageDispatcher()
        try {
            val filter = IntentFilter().apply {
                addAction(ACTION_UPDATE)
                addAction(ACTION_STOP)
            }
            registerReceiver(updateReceiver, filter)
        } catch (t: Throwable) {
            Log.w(TAG, "注册接收器失败", t)
        }
        try {
            val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            registerReceiver(batteryReceiver, batteryFilter)
            Log.d(TAG_BATTERY, "电量监听器已注册")
            
            // 初始化充电状态，防止服务启动时误触发
            initializeChargingState()
        } catch (t: Throwable) {
            Log.w(TAG_BATTERY, "注册电量监听器失败", t)
        }
    }

    private fun initializeChargingState() {
        try {
            val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            val chargingReminderEnabled = prefs.getBoolean(Constants.PREF_CHARGING_REMINDER_ENABLED, false)
            if (!chargingReminderEnabled) return
            
            val lastStateInitialized = prefs.getBoolean(Constants.PREF_LAST_CHARGING_STATE_INITIALIZED, false)
            if (lastStateInitialized) return
            
            val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
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
            Log.d(TAG_BATTERY, "初始化充电状态: isCharging=$isCharging")
        } catch (t: Throwable) {
            Log.w(TAG_BATTERY, "初始化充电状态失败", t)
        }
    }

    private val dispatcherExecutor = Executors.newSingleThreadScheduledExecutor()
    private var dispatcherStarted = false

    private fun startFailedMessageDispatcher() {
        if (dispatcherStarted) return
        dispatcherStarted = true
        dispatcherExecutor.scheduleAtFixedRate({
            try {
                SmsReceiver.retryFailedMessages(applicationContext)
            } catch (t: Throwable) {
                Log.w(TAG, "失败消息调度异常", t)
            }
        }, 10, 60, java.util.concurrent.TimeUnit.SECONDS)
        LogStore.append(this, "失败消息调度器已启动（每60秒检查一次）")
    }

    private fun createChannel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = getSystemService(NotificationManager::class.java)
                if (nm != null) {
                    val importance = NotificationManager.IMPORTANCE_HIGH
                    val channel = NotificationChannel(
                        Constants.NOTIFICATION_CHANNEL_ID,
                        Constants.NOTIFICATION_CHANNEL_NAME,
                        importance
                    )
                    channel.setShowBadge(false)
                    channel.lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
                    nm.createNotificationChannel(channel)
                } else {
                    Log.w(TAG, "创建通道时NotificationManager为null")
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "创建通道失败", t)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 检查通知权限
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            Log.w(TAG, "通知权限未授予，无法启动前台服务")
            LogStore.append(applicationContext, "错误：缺少通知权限，无法启动前台服务")
            stopSelf()
            return START_NOT_STICKY
        }

        val notification: Notification = try {
            buildNotification()
        } catch (t: Throwable) {
            Log.w(TAG, "构建通知失败，使用回退方案", t)
            // fallback: 直接使用编译时资源，确保 smallIcon 不会回退到系统占位
            NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
                .setContentTitle("短信转发助手")
                .setContentText("服务正在运行")
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setOngoing(true)
                .build()
        }

        try {
            if (Build.VERSION.SDK_INT >= 34) {
                val type = getRemoteMessagingForegroundServiceType()
                if (type != 0) {
                    startForeground(Constants.NOTIFICATION_ID, notification, type)
                } else {
                    Log.w(TAG, "通过反射未找到FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING，无类型调用startForeground")
                    startForeground(Constants.NOTIFICATION_ID, notification)
                }
            } else {
                startForeground(Constants.NOTIFICATION_ID, notification)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "启动前台服务失败，正在停止服务", t)
            LogStore.append(applicationContext, "错误: startForeground 失败: ${t.javaClass.simpleName} ${t.message}")
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) nm?.getNotificationChannel(Constants.NOTIFICATION_CHANNEL_ID) else null
            val chInfo = if (channel != null) "channel(${channel.id}): importance=${channel.importance} name=${channel.name}" else "channel:null"
            val notifAllowed = NotificationManagerCompat.from(this).areNotificationsEnabled()
            LogStore.append(applicationContext, "DEBUG: notifAllowed=$notifAllowed ; $chInfo")
        } catch (t: Throwable) {
            LogStore.append(applicationContext, "DEBUG: 检查 channel 失败: ${t.message}")
        }

        try {
            val nm2 = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm2?.notify(Constants.NOTIFICATION_ID, notification)
        } catch (t: Throwable) {
            Log.w(TAG, "额外通知失败", t)
        }
        return START_STICKY
    }

    private fun getRemoteMessagingForegroundServiceType(): Int {
        return try {
            val cls = Class.forName("android.app.ServiceInfo")
            val field = cls.getField("FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING")
            (field.getInt(null))
        } catch (t: Throwable) {
            Log.w(TAG, "通过反射读取FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING失败: ${t.message}")
            0
        }
    }

    /**
     * 保留 resolveSmallIcon 作为极小概率回退（仍优先使用编译时资源），但通知构建处已直接使用 R.drawable.ic_stat_notification
     */
    private fun resolveSmallIcon(): Int {
        val statDrawable = resources.getIdentifier("ic_stat_notification", "drawable", packageName)
        if (statDrawable != 0) return statDrawable
        val statMipmap = resources.getIdentifier("ic_stat_notification", "mipmap", packageName)
        if (statMipmap != 0) return statMipmap

        val appIcon = applicationInfo.icon
        if (appIcon != 0) return appIcon
        return android.R.drawable.ic_dialog_info
    }

    private fun buildNotification(): Notification {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(Constants.PREF_ENABLED, false)
        val status = if (enabled) "已启用" else "已禁用"
        val latest = LogStore.latest(this)

        val builder = NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("短信转发助手 - $status")
            .setContentText(latest)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)

        // 尝试设置彩色大图标（在展开的通知/设置中会显示），优先使用 mipmap/ic_launcher 或 drawable/ic_launcher
        try {
            val largeId = resources.getIdentifier("ic_launcher", "mipmap", packageName).takeIf { it != 0 }
                ?: resources.getIdentifier("ic_launcher", "drawable", packageName).takeIf { it != 0 }
            if (largeId != null && largeId != 0) {
                val bmp = BitmapFactory.decodeResource(resources, largeId)
                if (bmp != null) builder.setLargeIcon(bmp)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "设置大图标失败: ${t.message}")
        }

        val piFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            `package` = packageName
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, mainIntent, piFlags)
        builder.setContentIntent(pendingIntent)

        val stopIntent = Intent(ACTION_STOP).apply { `package` = packageName }
        val stopPending = PendingIntent.getBroadcast(this, 1, stopIntent, piFlags)
        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止服务", stopPending)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, Constants.NOTIFICATION_CHANNEL_ID)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                `package` = packageName
            }
            val pi = PendingIntent.getActivity(this, 2, intent, piFlags)
            builder.addAction(android.R.drawable.ic_menu_manage, "通知设置", pi)
        } else {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                `package` = packageName
            }
            val pi = PendingIntent.getActivity(this, 3, intent, piFlags)
            builder.addAction(android.R.drawable.ic_menu_manage, "应用设置", pi)
        }

        return builder.build()
    }

    private fun updateNotification() {
        val now = System.currentTimeMillis()
        if (now - lastNotificationUpdateTime < Constants.NOTIFICATION_UPDATE_THROTTLE_MS) {
            Log.d(TAG, "由于节流限制跳过通知更新")
            return
        }
        lastNotificationUpdateTime = now
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(Constants.NOTIFICATION_ID, buildNotification())
        } catch (t: Throwable) {
            Log.w(TAG, "更新通知失败", t)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(updateReceiver) } catch (e: Exception) { /* ignore */ }
        try { unregisterReceiver(batteryReceiver) } catch (e: Exception) { /* ignore */ }
        dispatcherExecutor.shutdownNow()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}