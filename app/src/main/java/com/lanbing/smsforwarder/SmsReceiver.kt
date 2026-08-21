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
import com.lanbing.smsforwarder.utils.ChannelLoader
import com.lanbing.smsforwarder.utils.MessageMatcher
import com.lanbing.smsforwarder.utils.NetworkMonitor
import com.lanbing.smsforwarder.utils.SimInfoUtils
import com.lanbing.smsforwarder.utils.WebhookSender
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

        // 固定线程池避免线程爆炸
        private val executor = Executors.newFixedThreadPool(Constants.THREAD_POOL_SIZE)

        // 消息去重缓存：key = sender + "|" + content，value = timestamp
        // 使用完整内容而非 hashCode，避免哈希碰撞导致不同短信被误判为重复
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
            val errorMessage: String = "",
            val messageTemplate: String? = null,
            val matchedKeyword: String? = null
        ) {
            fun isRetryable(): Boolean = retryCount < Constants.RETRY_SCHEDULE.size

            fun isReadyForRetry(): Boolean {
                if (retryCount >= Constants.RETRY_SCHEDULE.size) return false
                val delay = Constants.RETRY_SCHEDULE[retryCount]
                return System.currentTimeMillis() >= timestamp + delay
            }

            fun shouldDiscard(): Boolean = retryCount >= Constants.RETRY_SCHEDULE.size

            fun nextRetryDelay(): Long {
                if (retryCount >= Constants.RETRY_SCHEDULE.size) return 0L
                return (timestamp + Constants.RETRY_SCHEDULE[retryCount]) - System.currentTimeMillis()
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
                obj.put("errorMessage", errorMessage)
                if (messageTemplate != null) obj.put("messageTemplate", messageTemplate)
                if (matchedKeyword != null) obj.put("matchedKeyword", matchedKeyword)
                return obj
            }

            companion object {
                fun fromJSONObject(obj: JSONObject): FailedMessage {
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
                        retryCount = obj.optInt("retryCount", 0),
                        errorMessage = obj.optString("errorMessage", ""),
                        messageTemplate = if (obj.has("messageTemplate")) obj.getString("messageTemplate") else null,
                        matchedKeyword = if (obj.has("matchedKeyword")) obj.getString("matchedKeyword") else null
                    )
                }

                fun fromChannel(
                    channel: Channel, sender: String, content: String, receiverPhoneNumber: String?,
                    showSenderPhone: Boolean, highlightVerificationCode: Boolean, timestamp: Long,
                    errorMessage: String = "", matchedKeyword: String? = null
                ): FailedMessage {
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
                        retryCount = 0,
                        errorMessage = errorMessage,
                        messageTemplate = channel.messageTemplate,
                        matchedKeyword = matchedKeyword
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
                        LogStore.append(context, "已清除 $discarded 条过期或超重试的失败转发")
                        saveFailedMessages(context)
                    } else {
                        // no discards, nothing to save
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

        // 供 NetworkMonitor 和定时任务调用，重试失败的消息
        @JvmStatic
        fun retryFailedMessages(context: Context, forceAll: Boolean = false) {
            loadFailedMessages(context)
            synchronized(failedMessageLock) {
                if (failedMessages.isEmpty()) return

                val toDiscard = failedMessages.filter { it.shouldDiscard() }
                if (toDiscard.isNotEmpty()) {
                    failedMessages.removeAll(toDiscard)
                    LogStore.append(context, "已清除 ${toDiscard.size} 条放弃的失败转发")
                }

                // 定时重试时，先检查网络是否可用，没网就跳过，不消耗重试次数
                if (!forceAll && !NetworkMonitor.isNetworkAvailable(context)) {
                    return
                }

                val toRetry = failedMessages.filter {
                    it.isRetryable() &&
                    (forceAll || it.isReadyForRetry())
                }
                if (toRetry.isEmpty()) {
                    if (failedMessages.isNotEmpty()) {
                        LogStore.append(context, "${failedMessages.size} 条失败转发等待下次重试时间")
                    }
                    saveFailedMessages(context)
                    return
                }
                failedMessages.removeAll(toRetry)

                LogStore.append(context, "正在重试 ${toRetry.size} 条失败转发")

                val latch = java.util.concurrent.CountDownLatch(toRetry.size)
                toRetry.forEach { failed ->
                    executor.execute {
                        try {
                            val channel = failed.toChannel()
                            // 优化：直接使用 WebhookSender 工具类，不再创建 SmsReceiver 实例
                            val result = WebhookSender.sendSmsForward(
                                webhookUrl = failed.channelTarget,
                                sender = failed.sender,
                                content = failed.content,
                                receiverPhoneNumber = failed.receiverPhoneNumber,
                                type = channel.type,
                                showSenderPhone = failed.showSenderPhone,
                                highlightVerificationCode = failed.highlightVerificationCode,
                                messageTemplate = failed.messageTemplate,
                                matchedKeyword = failed.matchedKeyword,
                                channelName = failed.channelName
                            )

                            if (result.success) {
                                LogStore.append(context, "重试转发成功 -> ${failed.channelName}")
                            } else if (result.errorType == ForwardErrorType.NON_RETRYABLE) {
                                LogStore.append(context, "重试转发失败（不可重试）-> ${failed.channelName}")
                            } else {
                                val newRetryCount = failed.retryCount + 1
                                if (newRetryCount < Constants.RETRY_SCHEDULE.size) {
                                    synchronized(failedMessageLock) {
                                        failedMessages.add(
                                            failed.copy(
                                                retryCount = newRetryCount,
                                                errorMessage = result.errorMessage
                                            )
                                        )
                                    }
                                } else {
                                    LogStore.append(context, "重试转发失败（已放弃）-> ${failed.channelName}")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "重试失败", e)
                        } finally {
                            latch.countDown()
                        }
                    }
                }

                executor.execute {
                    try {
                        latch.await(Constants.BROADCAST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    } catch (_: InterruptedException) { }
                    saveFailedMessages(context)
                }
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

        // 使用 ChannelLoader 工具类加载配置（带内存缓存）
        val channels = ChannelLoader.loadChannels(prefs)
        val configs = ChannelLoader.loadConfigs(prefs)
        val channelMap = ChannelLoader.getChannelMap(prefs)

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

        // 全局发送者过滤（白名单/黑名单）
        val senderWhitelist = loadSenderFilterList(prefs, Constants.PREF_SENDER_WHITELIST)
        val senderBlacklist = loadSenderFilterList(prefs, Constants.PREF_SENDER_BLACKLIST)
        if (!MessageMatcher.passesGlobalSenderFilter(sender, senderWhitelist, senderBlacklist)) {
            LogStore.append(context, "发送者 $sender 被全局过滤规则拦截，跳过转发")
            return
        }

        // 获取接收短信的本机号码
        val receiverPhoneNumber = if (showReceiverPhone) SimInfoUtils.getReceiverPhoneNumber(context, subscriptionId, prefs) else null

        // 消息去重检查：使用发送者 + 完整内容作为 key，避免哈希碰撞
        val messageKey = "${sender}|${fullMessage}"
        val now = System.currentTimeMillis()
        synchronized(recentMessages) {
            cleanupRecentMessages()
            val lastTime = recentMessages[messageKey]
            if (lastTime != null && (now - lastTime) < Constants.DUPLICATE_WINDOW_MS) {
                LogStore.append(context, "跳过重复短信")
                return
            }
            recentMessages[messageKey] = now
        }

        // 收集所有匹配项：使用 MessageMatcher 引擎（支持多种匹配模式、多关键词组合、发送者过滤）
        val matched = mutableListOf<Pair<Channel, KeywordConfig>>()
        configs.forEach { cfg ->
            if (MessageMatcher.matches(cfg, fullMessage, sender)) {
                val ch = channelMap[cfg.channelId]
                if (ch != null) matched.add(Pair(ch, cfg))
            }
        }

        if (matched.isEmpty()) return

        // 加载持久化的失败消息
        loadFailedMessages(context)

        val pendingResult = goAsync()

        // 并行发送：每个通道只发一次，失败立即保存到失败队列
        executor.execute {
            val latch = java.util.concurrent.CountDownLatch(matched.size)
            try {
                matched.forEach { (ch, cfg) ->
                    executor.execute {
                        try {
                            // 使用 WebhookSender 工具类验证 URL 和发送消息
                            if (!WebhookSender.isValidUrl(ch.target)) {
                                LogStore.append(context, "通道 ${ch.name} webhook 格式无效")
                            } else {
                                val matchedKw = MessageMatcher.getAllKeywords(cfg).firstOrNull() ?: cfg.keyword
                                val result = WebhookSender.sendSmsForward(
                                    webhookUrl = ch.target,
                                    sender = sender,
                                    content = fullMessage,
                                    receiverPhoneNumber = receiverPhoneNumber,
                                    type = ch.type,
                                    showSenderPhone = showSenderPhone,
                                    highlightVerificationCode = highlightVerificationCode,
                                    messageTemplate = ch.messageTemplate,
                                    matchedKeyword = matchedKw,
                                    channelName = ch.name
                                )
                                if (result.success) {
                                    LogStore.append(context, "转发成功 — 来自: $sender -> ${ch.name} (规则: ${cfg.keyword})")
                                } else if (result.errorType == ForwardErrorType.NON_RETRYABLE) {
                                    LogStore.append(context, "转发失败 — 来自: $sender -> ${ch.name} (规则: ${cfg.keyword})")
                                } else {
                                    LogStore.append(context, "转发失败 — 来自: $sender -> ${ch.name} (规则: ${cfg.keyword}) | ${result.errorMessage}")
                                    synchronized(failedMessageLock) {
                                        if (failedMessages.size < Constants.MAX_FAILED_MESSAGES) {
                                            failedMessages.add(
                                                FailedMessage.fromChannel(
                                                    ch, sender, fullMessage, receiverPhoneNumber,
                                                    showSenderPhone, highlightVerificationCode, now,
                                                    result.errorMessage, matchedKw
                                                )
                                            )
                                        }
                                    }
                                    // 立即保存，防止进程被杀导致丢失
                                    saveFailedMessages(context)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "发送异常", e)
                            LogStore.append(context, "转发异常 — 来自: $sender -> ${ch.name} (规则: ${cfg.keyword})")
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
                    LogStore.append(context, "部分转发任务超时")
                }

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

    // 从 SharedPreferences 加载发送者过滤列表（JSON 数组格式）
    private fun loadSenderFilterList(prefs: android.content.SharedPreferences, key: String): List<String>? {
        val jsonStr = prefs.getString(key, null) ?: return null
        if (jsonStr.isBlank()) return null
        return try {
            val arr = org.json.JSONArray(jsonStr)
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
            list.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            null
        }
    }
}
