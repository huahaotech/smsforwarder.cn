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

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.app.PendingIntent
import android.provider.Settings
import com.lanbing.smsforwarder.utils.BatteryMonitor
import java.util.concurrent.Executors

class SmsForegroundService : Service() {

    companion object {
        const val ACTION_UPDATE = "com.lanbing.smsforwarder.ACTION_LOG_UPDATED"
        const val ACTION_STOP = "com.lanbing.smsforwarder.ACTION_STOP_SERVICE"
        private const val TAG = "SmsForegroundService"
        private var lastNotificationUpdateTime = 0L

        // 固定线程池避免线程爆炸
        private val executor = Executors.newFixedThreadPool(Constants.THREAD_POOL_SIZE)

        // 定时重试相关
        private val retryHandler = Handler(Looper.getMainLooper())
        private val retryRunnable = object : Runnable {
            override fun run() {
                try {
                    val ctx = appContext
                    if (ctx != null) {
                        executor.execute {
                            SmsReceiver.retryFailedMessages(ctx, forceAll = false)
                        }
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "定时重试失败", t)
                }
                // 继续循环重试，每10秒检查一次
                retryHandler.postDelayed(this, 10_000L)
            }
        }
        private var retryStarted = false

        // 使用 ApplicationContext，避免持有 Service 实例导致泄漏
        // ApplicationContext 生命周期与进程一致，不会造成内存泄漏
        private var appContext: Context? = null

        fun startPeriodicRetry(ctx: Context) {
            appContext = ctx.applicationContext
            if (!retryStarted) {
                retryHandler.post(retryRunnable)
                retryStarted = true
                LogStore.append(ctx, "定时重试已启动")
            } else {
                // 已启动，立即触发一次重试（确保新保存的失败消息尽快被重试）
                executor.execute {
                    SmsReceiver.retryFailedMessages(appContext ?: return@execute, forceAll = false)
                }
            }
        }

        fun stopPeriodicRetry() {
            if (retryStarted) {
                retryHandler.removeCallbacks(retryRunnable)
                retryStarted = false
                LogStore.append(appContext ?: return, "定时重试已停止")
            }
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
                // 检查转发状态，动态调整重试机制
                val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                val isEnabled = prefs.getBoolean(Constants.PREF_ENABLED, false)
                if (isEnabled) {
                    startPeriodicRetry(applicationContext)
                } else {
                    stopPeriodicRetry()
                }
                updateBatteryMonitorRegistration()
                updateNotification()
            } catch (t: Throwable) {
                Log.w(TAG, "更新通知失败", t)
            }
        }
    }

    private var lastNotifState: Boolean? = null

    // 网络状态追踪（实例级别，与服务生命周期一致）
    private var lastNetworkAvailable = false
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: android.net.Network) {
            if (!lastNetworkAvailable) {
                lastNetworkAvailable = true
                LogStore.append(applicationContext, "网络已恢复，正在重试失败转发")
                executor.execute {
                    SmsReceiver.retryFailedMessages(applicationContext, forceAll = true)
                }
            }
        }

        override fun onLost(network: android.net.Network) {
            lastNetworkAvailable = false
        }
    }

    // 电量监控器（从 Service 中抽离的独立模块）
    private lateinit var batteryMonitor: BatteryMonitor

    override fun onCreate() {
        super.onCreate()

        // 初始化电量监控器
        batteryMonitor = BatteryMonitor(this)

        createChannel()
        try {
            val filter = IntentFilter().apply {
                addAction(ACTION_UPDATE)
                addAction(ACTION_STOP)
            }
            registerReceiver(updateReceiver, filter)
        } catch (t: Throwable) {
            Log.w(TAG, "注册接收器失败", t)
        }
        updateBatteryMonitorRegistration()

        // 注册 NetworkCallback 监听网络变化（比广播更可靠）
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, networkCallback)
        } catch (t: Throwable) {
            Log.w(TAG, "注册网络回调失败", t)
        }
    }

    /**
     * 根据配置更新电量监控器的注册状态
     */
    private fun updateBatteryMonitorRegistration() {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val batteryEnabled = prefs.getBoolean(Constants.PREF_BATTERY_REMINDER_ENABLED, false)
        val chargingReminderEnabled = prefs.getBoolean(Constants.PREF_CHARGING_REMINDER_ENABLED, false)
        val shouldRegister = batteryEnabled || chargingReminderEnabled

        if (shouldRegister) {
            if (!batteryMonitor.isActive()) {
                batteryMonitor.register(initializeChargingState = true)
            }
        } else {
            if (batteryMonitor.isActive()) {
                batteryMonitor.unregister()
            }
        }
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
                    channel.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
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
            val notifAllowed = NotificationManagerCompat.from(this).areNotificationsEnabled()
            if (notifAllowed != lastNotifState) {
                lastNotifState = notifAllowed
                if (notifAllowed) {
                    LogStore.append(applicationContext, "通知权限已开启")
                } else {
                    LogStore.append(applicationContext, "通知权限未开启")
                }
            }
        } catch (t: Throwable) {
            LogStore.append(applicationContext, "检查通知权限失败")
        }

        try {
            val nm2 = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm2?.notify(Constants.NOTIFICATION_ID, notification)
        } catch (t: Throwable) {
            Log.w(TAG, "额外通知失败", t)
        }

        // 启动定时重试机制
        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean(Constants.PREF_ENABLED, false)
        if (isEnabled) {
            startPeriodicRetry(this)
        }

        // 刷新电量监听器注册状态（确保在服务运行中切换提醒开关时能正确注册/注销）
        updateBatteryMonitorRegistration()

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
        stopPeriodicRetry()
        try { unregisterReceiver(updateReceiver) } catch (e: Exception) { /* ignore */ }
        // 注销电量监控器
        try { batteryMonitor.unregister() } catch (e: Exception) { /* ignore */ }
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(networkCallback)
        } catch (_: Exception) { /* ignore */ }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
