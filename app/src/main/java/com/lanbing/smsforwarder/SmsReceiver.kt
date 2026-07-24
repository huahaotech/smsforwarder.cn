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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * SmsReceiver:
 * - 读取 SharedPreferences 中的 channels / keyword_configs
 * - 对所有规则逐条匹配（空 keyword 表示匹配全部）
 * - 对每条匹配项并行发送（允许同一条短信被多次发送到相同/不同通道）
 * - 支持 webhook 类型：企业微信、钉钉、飞书、通用 Webhook
 * - 添加消息去重机制和失败重试队列
 * - 失败消息持久化到文件
 */

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"

        val client: OkHttpClient = OkHttpClient.Builder()
            .callTimeout(Constants.CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .connectTimeout(Constants.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(Constants.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        // 固定线程池避免线程爆炸
        private val executor = Executors.newFixedThreadPool(Constants.THREAD_POOL_SIZE)

        // 消息去重缓存：key = sender+content_hash, value = timestamp
        private val recentMessages = ConcurrentHashMap<String, Long>()
        private var lastCleanupTime = 0L
        private const val CLEANUP_INTERVAL_MS = 60000L // 1分钟清理一次

        // 失败消息队列，等待网络恢复时重试
        private val failedMessages = mutableListOf<FailedMessage>()
        private val failedMessageLock = Object()

        data class FailedMessage(
            val channelId: String,
            val channelName: String,
            val channelType: String,
            val channelTarget: String,
            val sender: String,
            val content: String,
            val receiverPhoneNumber: String?,
            val showSenderPhone: Boolean,
            val highlightVerificationCode: Boolean,
            val timestamp: Long,
            val retryCount: Int = 0,
            val errorType: ForwardErrorType = ForwardErrorType.UNKNOWN,
            val errorMessage: String = "",
            val nextRetryTime: Long = 0L,
            val totalRetryCount: Int = 0
        ) {
            /**
             * 判断是否可重试
             * 根据错误类型决定是否应该重试
             */
            fun isRetryable(): Boolean {
                return errorType.isRetryable()
            }

            /**
             * 判断是否到达重试时间
             */
            fun isReadyForRetry(): Boolean {
                return nextRetryTime <= 0L || System.currentTimeMillis() >= nextRetryTime
            }

            /**
             * 判断消息是否已过期（距首次接收超过最大存活时间）
             */
            fun isExpired(): Boolean {
                return System.currentTimeMillis() - timestamp > Constants.FAILED_MESSAGE_MAX_AGE_MS
            }

            /**
             * 判断是否达到最大总重试次数
             */
            fun hasExceededMaxRetries(): Boolean {
                return totalRetryCount >= Constants.FAILED_MESSAGE_MAX_TOTAL_RETRIES
            }

            /**
             * 判断是否应该丢弃（过期或超过最大重试次数）
             */
            fun shouldDiscard(): Boolean {
                return isExpired() || hasExceededMaxRetries()
            }

            /**
             * 计算下一次重试时间
             */
            fun calculateNextRetryTime(): Long {
                val delay = errorType.getRetryDelay(retryCount)
                return System.currentTimeMillis() + delay
            }

            fun toJSONObject(): JSONObject {
                val obj = JSONObject()
                obj.put("channelId", channelId)
                obj.put("channelName", channelName)
                obj.put("channelType", channelType)
                obj.put("channelTarget", channelTarget)
                obj.put("sender", sender)
                obj.put("content", content)
                if (receiverPhoneNumber != null) obj.put("receiverPhoneNumber", receiverPhoneNumber)
                obj.put("showSenderPhone", showSenderPhone)
                obj.put("highlightVerificationCode", highlightVerificationCode)
                obj.put("timestamp", timestamp)
                obj.put("retryCount", retryCount)
                obj.put("errorType", errorType.name)
                obj.put("errorMessage", errorMessage)
                obj.put("nextRetryTime", nextRetryTime)
                obj.put("totalRetryCount", totalRetryCount)
                return obj
            }

            companion object {
                fun fromJSONObject(obj: JSONObject): FailedMessage {
                    val errorTypeStr = obj.optString("errorType", "UNKNOWN")
                    val errorType = try { ForwardErrorType.valueOf(errorTypeStr) } catch (t: Throwable) { ForwardErrorType.UNKNOWN }
                    return FailedMessage(
                        channelId = obj.getString("channelId"),
                        channelName = obj.getString("channelName"),
                        channelType = obj.getString("channelType"),
                        channelTarget = obj.getString("channelTarget"),
                        sender = obj.getString("sender"),
                        content = obj.getString("content"),
                        receiverPhoneNumber = if (obj.has("receiverPhoneNumber")) obj.getString("receiverPhoneNumber") else null,
                        showSenderPhone = obj.optBoolean("showSenderPhone", true),
                        highlightVerificationCode = obj.optBoolean("highlightVerificationCode", true),
                        timestamp = obj.getLong("timestamp"),
                        retryCount = obj.getInt("retryCount"),
                        errorType = errorType,
                        errorMessage = obj.optString("errorMessage", ""),
                        nextRetryTime = obj.optLong("nextRetryTime", 0L),
                        totalRetryCount = obj.optInt("totalRetryCount", 0)
                    )
                }

                fun fromChannel(channel: Channel, sender: String, content: String, receiverPhoneNumber: String?, showSenderPhone: Boolean, highlightVerificationCode: Boolean, timestamp: Long, errorType: ForwardErrorType, errorMessage: String, retryCount: Int = 0, nextRetryTime: Long = 0L, totalRetryCount: Int = 0): FailedMessage {
                    return FailedMessage(
                        channelId = channel.id,
                        channelName = channel.name,
                        channelType = channel.type.name,
                        channelTarget = channel.target,
                        sender = sender,
                        content = content,
                        receiverPhoneNumber = receiverPhoneNumber,
                        showSenderPhone = showSenderPhone,
                        highlightVerificationCode = highlightVerificationCode,
                        timestamp = timestamp,
                        retryCount = retryCount,
                        errorType = errorType,
                        errorMessage = errorMessage,
                        nextRetryTime = nextRetryTime,
                        totalRetryCount = totalRetryCount
                    )
                }
            }

            fun toChannel(): Channel {
                val type = try { ChannelType.valueOf(channelType) } catch (t: Throwable) { ChannelType.WECHAT }
                return Channel(channelId, channelName, type, channelTarget)
            }
        }

        private fun failedMessagesFile(context: Context): File {
            return File(context.filesDir, Constants.FAILED_MESSAGES_FILE)
        }

        private fun saveFailedMessages(context: Context) {
            synchronized(failedMessageLock) {
                try {
                    val file = failedMessagesFile(context)
                    val arr = JSONArray()
                    failedMessages.take(Constants.MAX_FAILED_MESSAGES).forEach { arr.put(it.toJSONObject()) }
                    file.writeText(arr.toString())
                } catch (t: Throwable) {
                    Log.e(TAG, "保存失败消息失败", t)
                }
            }
        }

        private fun loadFailedMessages(context: Context) {
            synchronized(failedMessageLock) {
                try {
                    val file = failedMessagesFile(context)
                    if (!file.exists()) return
                    val arr = JSONArray(file.readText())
                    failedMessages.clear()
                    var discarded = 0
                    for (i in 0 until arr.length()) {
                        val msg = FailedMessage.fromJSONObject(arr.getJSONObject(i))
                        if (msg.shouldDiscard()) {
                            discarded++
                        } else {
                            failedMessages.add(msg)
                        }
                    }
                    if (discarded > 0) {
                        Log.d(TAG, "加载时丢弃 $discarded 条过期或超重试的失败消息")
                        saveFailedMessages(context)
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "加载失败消息失败", t)
                }
            }
        }

        private fun cleanupRecentMessages() {
            val now = System.currentTimeMillis()
            if (now - lastCleanupTime < CLEANUP_INTERVAL_MS) return
            lastCleanupTime = now
            recentMessages.entries.removeIf { (now - it.value) > Constants.DUPLICATE_WINDOW_MS * 2 }
        }

        // 供 NetworkChangeReceiver 和定时任务调用，重试失败的消息
        @JvmStatic
        fun retryFailedMessages(context: Context, forceAll: Boolean = false) {
            loadFailedMessages(context)
            synchronized(failedMessageLock) {
                if (failedMessages.isEmpty()) return

                // 智能过滤：丢弃过期或超重试的消息，只重试可重试且到达时间的消息
                val toDiscard = failedMessages.filter { it.shouldDiscard() }
                if (toDiscard.isNotEmpty()) {
                    failedMessages.removeAll(toDiscard)
                    Log.d(TAG, "丢弃 ${toDiscard.size} 条过期或超重试的失败消息")
                }

                val toRetry = failedMessages.filter { 
                    it.isRetryable() && 
                    it.retryCount < Constants.MAX_RETRY_ATTEMPTS &&
                    !it.hasExceededMaxRetries() &&
                    (forceAll || it.isReadyForRetry())
                }
                if (toRetry.isEmpty()) {
                    saveFailedMessages(context)
                    return
                }
                failedMessages.removeAll(toRetry)

                toRetry.forEach { failed ->
                    executor.execute {
                        try {
                            val receiver = SmsReceiver()
                            val channel = failed.toChannel()
                            val result = receiver.sendToWebhook(
                                failed.channelTarget, failed.sender, failed.content, 
                                failed.receiverPhoneNumber, channel.type, 
                                failed.showSenderPhone, failed.highlightVerificationCode
                            )
                            
                            if (result.success) {
                                LogStore.append(context, "重试转发成功 -> ${failed.channelName}")
                            } else {
                                val newTotalRetryCount = failed.totalRetryCount + 1
                                val exceededTotalRetries = newTotalRetryCount >= Constants.FAILED_MESSAGE_MAX_TOTAL_RETRIES
                                
                                if (result.errorType.isRetryable() && 
                                    failed.retryCount + 1 < Constants.MAX_RETRY_ATTEMPTS &&
                                    !exceededTotalRetries) {
                                    failedMessages.add(failed.copy(
                                        retryCount = failed.retryCount + 1,
                                        totalRetryCount = newTotalRetryCount,
                                        errorType = result.errorType,
                                        errorMessage = result.errorMessage,
                                        nextRetryTime = System.currentTimeMillis() + result.errorType.getRetryDelay(failed.retryCount)
                                    ))
                                } else {
                                    val reason = when {
                                        !result.errorType.isRetryable() -> "不可重试的错误类型"
                                        exceededTotalRetries -> "已达最大总重试次数"
                                        else -> "已达最大重试次数"
                                    }
                                    LogStore.append(context, "重试转发失败（$reason）-> ${failed.channelName} | 错误: ${result.errorType.name} | 原因: ${result.errorMessage}")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "重试失败", e)
                        }
                    }
                }
                saveFailedMessages(context)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean(Constants.PREF_ENABLED, false)
        if (!isEnabled) return

        // 读取配置项
        val showReceiverPhone = prefs.getBoolean(Constants.PREF_SHOW_RECEIVER_PHONE, true)
        val showSenderPhone = prefs.getBoolean(Constants.PREF_SHOW_SENDER_PHONE, true)
        val highlightVerificationCode = prefs.getBoolean(Constants.PREF_HIGHLIGHT_VERIFICATION_CODE, true)

        val channels = loadChannels(prefs)
        val configs = loadConfigs(prefs)

        if (channels.isEmpty() || configs.isEmpty()) {
            LogStore.append(context, "未配置通道或关键词规则，已跳过转发")
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val sb = StringBuilder()
        var sender = ""
        var subscriptionId: Int? = null
        
        // 尝试从 intent 中获取 subscriptionId
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            try {
                subscriptionId = intent.getIntExtra("subscription", -1)
                if (subscriptionId == -1) {
                    subscriptionId = intent.getIntExtra("slot", -1)
                    if (subscriptionId != -1) {
                        // slot 转换为 subscriptionId
                        try {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                                @Suppress("DEPRECATION")
                                val subscriptionManager = SubscriptionManager.from(context)
                                val activeSubscriptions = subscriptionManager.activeSubscriptionInfoList
                                if (activeSubscriptions != null && subscriptionId >= 0 && activeSubscriptions.size > subscriptionId) {
                                    subscriptionId = activeSubscriptions[subscriptionId].subscriptionId
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "转换 slot 为 subscriptionId 失败", e)
                            subscriptionId = null
                        }
                    } else {
                        subscriptionId = null
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "获取 subscriptionId 失败", e)
                subscriptionId = null
            }
        }

        for (sms in messages) {
            sender = sms.displayOriginatingAddress ?: sender
            sb.append(sms.displayMessageBody)
        }
        val fullMessage = normalizeContent(sb.toString())

        // 获取接收短信的本机号码
        val receiverPhoneNumber = if (showReceiverPhone) getReceiverPhoneNumber(context, subscriptionId) else null

        // 消息去重检查
        val messageKey = "${sender}_${fullMessage.hashCode()}"
        val now = System.currentTimeMillis()
        synchronized(recentMessages) {
            cleanupRecentMessages()
            val lastTime = recentMessages[messageKey]
            if (lastTime != null && (now - lastTime) < Constants.DUPLICATE_WINDOW_MS) {
                Log.d(TAG, "跳过重复消息: 发送者=$sender")
                return
            }
            recentMessages[messageKey] = now
        }

        // 收集所有匹配项
        val matched = mutableListOf<Pair<Channel, KeywordConfig>>()
        configs.forEach { cfg ->
            val kw = cfg.keyword.trim()
            val match = if (kw.isEmpty()) true else fullMessage.contains(kw, ignoreCase = true)
            if (match) {
                val ch = channels.find { it.id == cfg.channelId }
                if (ch != null) matched.add(Pair(ch, cfg))
            }
        }

        if (matched.isEmpty()) return

        // 加载持久化的失败消息
        loadFailedMessages(context)

        val pendingResult = goAsync()

        // 并行发送
        executor.execute {
            val latch = java.util.concurrent.CountDownLatch(matched.size)
            try {
                matched.forEach { (ch, cfg) ->
                    executor.execute {
                        try {
                            if (!isValidUrl(ch.target)) {
                                LogStore.append(context, "通道 ${ch.name} webhook 格式无效: ${ch.target}")
                                // URL格式无效，不需要重试
                                synchronized(failedMessageLock) {
                                    if (failedMessages.size < Constants.MAX_FAILED_MESSAGES) {
                                        failedMessages.add(FailedMessage.fromChannel(
                                            ch, sender, fullMessage, receiverPhoneNumber,
                                            showSenderPhone, highlightVerificationCode, now,
                                            ForwardErrorType.INVALID_URL, "URL格式无效"
                                        ))
                                    }
                                }
                            } else {
                                var attempt = 0
                                var lastResult: ForwardResult? = null
                                var backoff = 0L
                                
                                // 智能重试：最多重试 MAX_RETRY_ATTEMPTS 次，但只对可重试的错误重试
                                while (attempt < Constants.MAX_RETRY_ATTEMPTS) {
                                    if (backoff > 0) {
                                        try { Thread.sleep(backoff) } catch (_: InterruptedException) { }
                                    }
                                    
                                    val result = sendToWebhook(ch.target, sender, fullMessage, receiverPhoneNumber, ch.type, showSenderPhone, highlightVerificationCode)
                                    lastResult = result
                                    
                                    if (result.success) {
                                        LogStore.append(context, "转发成功 — 来自: $sender -> ${ch.name} (规则: ${cfg.keyword})")
                                        break
                                    }
                                    
                                    // 如果错误不可重试，立即退出循环
                                    if (!result.errorType.isRetryable()) {
                                        LogStore.append(context, "转发失败（不可重试）— 来自: $sender -> ${ch.name} (规则: ${cfg.keyword}) | 错误: ${result.errorType.name} | 原因: ${result.errorMessage}")
                                        break
                                    }
                                    
                                    attempt++
                                    if (attempt < Constants.MAX_RETRY_ATTEMPTS) {
                                        backoff = Constants.INITIAL_RETRY_BACKOFF_MS * attempt
                                    } else {
                                        // 达到最大重试次数
                                        LogStore.append(context, "转发失败（已达最大重试次数）— 来自: $sender -> ${ch.name} (规则: ${cfg.keyword}) | 错误: ${result.errorType.name} | 原因: ${result.errorMessage}")
                                    }
                                }
                                
                                // 如果最终失败且是可重试的错误，添加到失败队列
                                if (lastResult != null && !lastResult.success && lastResult.errorType.isRetryable()) {
                                    synchronized(failedMessageLock) {
                                        if (failedMessages.size < Constants.MAX_FAILED_MESSAGES) {
                                            val nextRetryTime = System.currentTimeMillis() + lastResult.errorType.getRetryDelay(attempt)
                                            failedMessages.add(FailedMessage.fromChannel(
                                                ch, sender, fullMessage, receiverPhoneNumber,
                                                showSenderPhone, highlightVerificationCode, now,
                                                lastResult.errorType, lastResult.errorMessage,
                                                attempt, nextRetryTime
                                            ))
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "发送异常", e)
                            LogStore.append(context, "转发异常 — 来自: $sender -> ${ch.name} (规则: ${cfg.keyword}) | 异常: ${e.message ?: e.javaClass.simpleName}")
                        } finally {
                            latch.countDown()
                        }
                    }
                }

                val completed = try {
                    latch.await(Constants.BROADCAST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                } catch (e: InterruptedException) {
                    Log.w(TAG, "等待被中断", e)
                    false
                }
                if (!completed) {
                    LogStore.append(context, "部分转发任务超时（等待 ${Constants.BROADCAST_TIMEOUT_SECONDS}s 后返回）")
                }

                // 保存失败消息
                saveFailedMessages(context)
            } catch (t: Throwable) {
                Log.e(TAG, "并行发送工作线程中发生意外错误", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    // 归一化：删除 CR，折叠连续空行为单个换行，trim 首尾空白
    private fun normalizeContent(s: String): String {
        return s.replace("\r", "")
            .replace(Regex("\n{2,}"), "\n")
            .trim()
    }

    /**
     * 获取接收短信的本机号码
     * @param subscriptionId SIM 卡的 subscriptionId，用于确定是哪个 SIM 卡接收的短信
     */
    private fun getReceiverPhoneNumber(context: Context, subscriptionId: Int?): String? {
        try {
            val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
            
            var simSlotIndex = 1 // 默认假设是 SIM1
            var foundMatchingSim = false
            
            // 根据 subscriptionId 确定 SIM 卡槽位置
            if (subscriptionId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                try {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                        @Suppress("DEPRECATION")
                        val subscriptionManager = SubscriptionManager.from(context)
                        val activeSubscriptions = subscriptionManager.activeSubscriptionInfoList
                        if (activeSubscriptions != null) {
                            activeSubscriptions.forEachIndexed { index, subInfo ->
                                try {
                                    if (subInfo != null && subInfo.subscriptionId == subscriptionId) {
                                        simSlotIndex = index + 1 // slot 从 1 开始
                                        foundMatchingSim = true
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "检查 subscriptionInfo 失败", e)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "确定 SIM 卡槽位置失败", e)
                }
            }

            // 根据 SIM 卡槽位置返回对应的自定义号码
            if (simSlotIndex == 1) {
                val customSim1Phone = prefs.getString(Constants.PREF_CUSTOM_SIM1_PHONE, null)
                if (!customSim1Phone.isNullOrBlank()) {
                    return customSim1Phone
                }
            } else if (simSlotIndex == 2) {
                val customSim2Phone = prefs.getString(Constants.PREF_CUSTOM_SIM2_PHONE, null)
                if (!customSim2Phone.isNullOrBlank()) {
                    return customSim2Phone
                }
            }

            // 如果没有自定义号码，但找到了匹配的 SIM，尝试自动获取
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                // 如果有 subscriptionId，尝试通过 subscriptionId 获取号码
                if (subscriptionId != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 && foundMatchingSim) {
                    try {
                        @Suppress("DEPRECATION")
                        val subscriptionManager = SubscriptionManager.from(context)
                        val subInfo = subscriptionManager.getActiveSubscriptionInfo(subscriptionId)
                        if (subInfo != null) {
                            @Suppress("DEPRECATION")
                            val number = subInfo.number
                            if (!number.isNullOrBlank()) {
                                return number
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "通过 subscriptionId 获取号码失败", e)
                    }
                }

                // 回退到默认的获取方式
                try {
                    val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                    if (telephonyManager != null) {
                        @Suppress("DEPRECATION")
                        val number = telephonyManager.line1Number
                        if (!number.isNullOrBlank()) {
                            return number
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "通过 TelephonyManager 获取号码失败", e)
                }
            } else {
                Log.d(TAG, "没有 READ_PHONE_STATE 权限，无法自动获取本机号码")
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取本机号码失败", e)
        }
        return null
    }

    internal fun sendToWebhook(webhookUrl: String, sender: String, content: String, receiverPhoneNumber: String?, type: ChannelType, showSenderPhone: Boolean, highlightVerificationCode: Boolean): ForwardResult {
        val json = when (type) {
            ChannelType.FEISHU -> buildFeishuMessage(sender, content, receiverPhoneNumber, showSenderPhone, highlightVerificationCode)
            ChannelType.WECHAT -> buildWechatMessage(sender, content, receiverPhoneNumber, showSenderPhone, highlightVerificationCode)
            ChannelType.DINGTALK -> buildDingtalkMessage(sender, content, receiverPhoneNumber, showSenderPhone, highlightVerificationCode)
            ChannelType.GENERIC_WEBHOOK -> buildGenericMessage(sender, content, receiverPhoneNumber, showSenderPhone, highlightVerificationCode)
        }

        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder()
            .url(webhookUrl)
            .post(body)
            .build()

        return try {
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    ForwardResult.success()
                } else {
                    val errorBody = try { resp.body?.string()?.take(500) } catch (_: Exception) { "无法读取响应" }
                    val errorMsg = "HTTP ${resp.code}: ${errorBody ?: "无响应体"}"
                    
                    // 根据HTTP状态码分类错误类型
                    val errorType = when {
                        resp.code == 429 -> ForwardErrorType.HTTP_RATE_LIMIT
                        resp.code in 500..599 -> ForwardErrorType.HTTP_SERVER_ERROR
                        resp.code in 400..499 -> ForwardErrorType.HTTP_CLIENT_ERROR
                        else -> ForwardErrorType.UNKNOWN
                    }
                    
                    ForwardResult.failure(errorType, errorMsg)
                }
            }
        } catch (e: java.net.SocketTimeoutException) {
            ForwardResult.failure(ForwardErrorType.NETWORK_TIMEOUT, "连接超时")
        } catch (e: java.net.UnknownHostException) {
            ForwardResult.failure(ForwardErrorType.DNS_FAILURE, "域名解析失败")
        } catch (e: java.net.ConnectException) {
            ForwardResult.failure(ForwardErrorType.CONNECTION_REFUSED, "连接被拒绝")
        } catch (e: java.io.IOException) {
            ForwardResult.failure(ForwardErrorType.NETWORK_UNAVAILABLE, "网络错误: ${e.message ?: e.javaClass.simpleName}")
        } catch (e: Exception) {
            ForwardResult.failure(ForwardErrorType.UNKNOWN, "未知错误: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /**
     * 从短信内容中提取验证码
     * 匹配常见验证码格式：4-8位数字，可能带有"验证码"、"校验码"等关键词
     */
    private fun extractVerificationCode(content: String): String? {
        // 常见的验证码关键词
        val keywords = listOf("验证码", "校验码", "动态码", "验证 code", "verification code", "verify code")
        val hasKeyword = keywords.any { content.contains(it, ignoreCase = true) }

        // 优先匹配：关键词后紧跟的 4-8 位数字
        if (hasKeyword) {
            // 模式1：关键词后面直接跟数字（如"验证码是123456"）
            val pattern1 = Regex("""(?:验证码|校验码|动态码|验证|verification|verify)[^\d]*(\d{4,8})""", RegexOption.IGNORE_CASE)
            pattern1.find(content)?.let { return it.groupValues[1] }

            // 模式2：关键词附近的数字（关键词后20字符内）
            val pattern2 = Regex("""(?:验证码|校验码|动态码|验证|verification|verify).{0,30}?(\d{4,8})""", RegexOption.IGNORE_CASE)
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

    /**
     * 构建消息内容，突出显示验证码
     */
    private fun buildMessageWithHighlightedCode(sender: String, content: String, receiverPhoneNumber: String?, showSenderPhone: Boolean, highlightVerificationCode: Boolean): String {
        val parts = mutableListOf<String>()
        val code = if (highlightVerificationCode) extractVerificationCode(content) else null

        if (code != null) {
            parts.add("验证码: $code")
        }
        if (receiverPhoneNumber != null) {
            parts.add("本机: $receiverPhoneNumber")
        }
        if (showSenderPhone) {
            parts.add("来自: $sender")
        }
        parts.add(content)

        return parts.joinToString("\n")
    }

    private fun buildWechatMessage(sender: String, content: String, receiverPhoneNumber: String?, showSenderPhone: Boolean, highlightVerificationCode: Boolean): JSONObject {
        val json = JSONObject()
        json.put("msgtype", "text")
        val text = JSONObject()
        text.put("content", buildMessageWithHighlightedCode(sender, content, receiverPhoneNumber, showSenderPhone, highlightVerificationCode))
        json.put("text", text)
        return json
    }

    private fun buildDingtalkMessage(sender: String, content: String, receiverPhoneNumber: String?, showSenderPhone: Boolean, highlightVerificationCode: Boolean): JSONObject {
        val json = JSONObject()
        json.put("msgtype", "text")
        val text = JSONObject()
        text.put("content", buildMessageWithHighlightedCode(sender, content, receiverPhoneNumber, showSenderPhone, highlightVerificationCode))
        json.put("text", text)
        return json
    }

    private fun buildFeishuMessage(sender: String, content: String, receiverPhoneNumber: String?, showSenderPhone: Boolean, highlightVerificationCode: Boolean): JSONObject {
        val json = JSONObject()
        json.put("msg_type", "text")
        val text = JSONObject()
        text.put("text", buildMessageWithHighlightedCode(sender, content, receiverPhoneNumber, showSenderPhone, highlightVerificationCode))
        json.put("content", text)
        return json
    }

    private fun buildGenericMessage(sender: String, content: String, receiverPhoneNumber: String?, showSenderPhone: Boolean, highlightVerificationCode: Boolean): JSONObject {
        val json = JSONObject()
        if (showSenderPhone) {
            json.put("sender", sender)
        }
        if (receiverPhoneNumber != null) {
            json.put("receiver", receiverPhoneNumber)
        }
        json.put("content", content)
        if (highlightVerificationCode) {
            json.put("verificationCode", extractVerificationCode(content))
        }
        json.put("timestamp", System.currentTimeMillis())
        return json
    }

    private val urlRegex = Regex("""^https?://[^\s/$.?#].[^\s]*$""", RegexOption.IGNORE_CASE)

    private fun isValidUrl(s: String): Boolean {
        return urlRegex.matches(s)
    }

    private fun loadChannels(prefs: android.content.SharedPreferences): List<Channel> {
        val arrStr = prefs.getString(Constants.PREF_CHANNELS, "[]") ?: "[]"
        return try {
            val arr = org.json.JSONArray(arrStr)
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

    private fun loadConfigs(prefs: android.content.SharedPreferences): List<KeywordConfig> {
        val arrStr = prefs.getString(Constants.PREF_KEYWORD_CONFIGS, "[]") ?: "[]"
        return try {
            val arr = org.json.JSONArray(arrStr)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                KeywordConfig(o.getString("id"), o.getString("keyword"), o.getString("channelId"))
            }
        } catch (t: Throwable) {
            emptyList()
        }
    }
}
