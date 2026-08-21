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

import com.lanbing.smsforwarder.ChannelType
import com.lanbing.smsforwarder.ForwardResult
import com.lanbing.smsforwarder.ForwardErrorType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Webhook 发送工具类
 *
 * 封装所有 Webhook 消息构建和发送逻辑，供 SmsReceiver 和 SmsForegroundService 共享使用，
 * 消除重复代码，统一错误处理和消息格式。
 */
object WebhookSender {

    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val urlRegex = Regex("""^https?://[^\s/$.?#].[^\s]*$""", RegexOption.IGNORE_CASE)

    /**
     * 验证 Webhook URL 格式是否有效
     */
    fun isValidUrl(url: String): Boolean {
        return urlRegex.matches(url)
    }

    /**
     * 发送短信转发消息到指定 Webhook
     *
     * @param webhookUrl Webhook 地址
     * @param sender 发送方号码
     * @param content 短信内容
     * @param receiverPhoneNumber 接收方本机号码（可选）
     * @param type 通道类型
     * @param showSenderPhone 是否显示发送方号码
     * @param highlightVerificationCode 是否突出显示验证码
     * @return 转发结果
     */
    fun sendSmsForward(
        webhookUrl: String,
        sender: String,
        content: String,
        receiverPhoneNumber: String?,
        type: ChannelType,
        showSenderPhone: Boolean,
        highlightVerificationCode: Boolean
    ): ForwardResult {
        val json = when (type) {
            ChannelType.FEISHU -> buildFeishuSmsMessage(sender, content, receiverPhoneNumber, showSenderPhone, highlightVerificationCode)
            ChannelType.WECHAT -> buildWechatSmsMessage(sender, content, receiverPhoneNumber, showSenderPhone, highlightVerificationCode)
            ChannelType.DINGTALK -> buildDingtalkSmsMessage(sender, content, receiverPhoneNumber, showSenderPhone, highlightVerificationCode)
            ChannelType.GENERIC_WEBHOOK -> buildGenericSmsMessage(sender, content, receiverPhoneNumber, showSenderPhone, highlightVerificationCode)
        }

        return sendJsonRequest(webhookUrl, json)
    }

    /**
     * 发送简单文本消息到指定 Webhook（用于电量提醒、充电提醒等）
     */
    fun sendTextMessage(webhookUrl: String, message: String, type: ChannelType): ForwardResult {
        val json = when (type) {
            ChannelType.WECHAT -> buildWechatTextMessage(message)
            ChannelType.DINGTALK -> buildDingtalkTextMessage(message)
            ChannelType.FEISHU -> buildFeishuTextMessage(message)
            ChannelType.GENERIC_WEBHOOK -> buildGenericTextMessage(message)
        }

        return sendJsonRequest(webhookUrl, json)
    }

    /**
     * 发送 JSON 请求并返回结果
     */
    private fun sendJsonRequest(webhookUrl: String, json: JSONObject): ForwardResult {
        val body = json.toString().toRequestBody(JSON)
        val req = Request.Builder()
            .url(webhookUrl)
            .post(body)
            .build()

        return try {
            HttpClient.instance.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    ForwardResult.success()
                } else {
                    val errorBody = try { resp.body?.string()?.take(500) } catch (_: Exception) { "无法读取响应" }
                    val errorMsg = "HTTP ${resp.code}: ${errorBody ?: "无响应体"}"

                    // 4xx 客户端错误不可重试，其他可重试
                    val errorType = if (resp.code in 400..499) ForwardErrorType.NON_RETRYABLE else ForwardErrorType.RETRYABLE

                    ForwardResult.failure(errorType, errorMsg)
                }
            }
        } catch (e: java.net.SocketTimeoutException) {
            ForwardResult.failure(ForwardErrorType.RETRYABLE, "连接超时")
        } catch (e: java.net.UnknownHostException) {
            ForwardResult.failure(ForwardErrorType.RETRYABLE, "域名解析失败")
        } catch (e: java.net.ConnectException) {
            ForwardResult.failure(ForwardErrorType.RETRYABLE, "连接被拒绝")
        } catch (e: java.io.IOException) {
            ForwardResult.failure(ForwardErrorType.RETRYABLE, "网络错误: ${e.message ?: e.javaClass.simpleName}")
        } catch (e: Exception) {
            ForwardResult.failure(ForwardErrorType.RETRYABLE, "未知错误: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    // ==================== 短信转发消息构建 ====================

    private fun buildMessageWithHighlightedCode(
        sender: String,
        content: String,
        receiverPhoneNumber: String?,
        showSenderPhone: Boolean,
        highlightVerificationCode: Boolean
    ): String {
        val parts = mutableListOf<String>()
        val code = if (highlightVerificationCode) VerificationCodeExtractor.extract(content) else null

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

    private fun buildWechatSmsMessage(
        sender: String, content: String, receiverPhoneNumber: String?,
        showSenderPhone: Boolean, highlightVerificationCode: Boolean
    ): JSONObject {
        val json = JSONObject()
        json.put("msgtype", "text")
        val text = JSONObject()
        text.put("content", buildMessageWithHighlightedCode(sender, content, receiverPhoneNumber, showSenderPhone, highlightVerificationCode))
        json.put("text", text)
        return json
    }

    private fun buildDingtalkSmsMessage(
        sender: String, content: String, receiverPhoneNumber: String?,
        showSenderPhone: Boolean, highlightVerificationCode: Boolean
    ): JSONObject {
        val json = JSONObject()
        json.put("msgtype", "text")
        val text = JSONObject()
        text.put("content", buildMessageWithHighlightedCode(sender, content, receiverPhoneNumber, showSenderPhone, highlightVerificationCode))
        json.put("text", text)
        return json
    }

    private fun buildFeishuSmsMessage(
        sender: String, content: String, receiverPhoneNumber: String?,
        showSenderPhone: Boolean, highlightVerificationCode: Boolean
    ): JSONObject {
        val json = JSONObject()
        json.put("msg_type", "text")
        val text = JSONObject()
        text.put("text", buildMessageWithHighlightedCode(sender, content, receiverPhoneNumber, showSenderPhone, highlightVerificationCode))
        json.put("content", text)
        return json
    }

    private fun buildGenericSmsMessage(
        sender: String, content: String, receiverPhoneNumber: String?,
        showSenderPhone: Boolean, highlightVerificationCode: Boolean
    ): JSONObject {
        val json = JSONObject()
        if (showSenderPhone) {
            json.put("sender", sender)
        }
        if (receiverPhoneNumber != null) {
            json.put("receiver", receiverPhoneNumber)
        }
        json.put("content", content)
        if (highlightVerificationCode) {
            json.put("verificationCode", VerificationCodeExtractor.extract(content))
        }
        json.put("timestamp", System.currentTimeMillis())
        return json
    }

    // ==================== 纯文本消息构建（电量/充电提醒等） ====================

    private fun buildWechatTextMessage(message: String): JSONObject {
        val json = JSONObject()
        val text = JSONObject()
        text.put("content", message)
        json.put("msgtype", "text")
        json.put("text", text)
        return json
    }

    private fun buildDingtalkTextMessage(message: String): JSONObject {
        val json = JSONObject()
        val text = JSONObject()
        text.put("content", message)
        json.put("msgtype", "text")
        json.put("text", text)
        return json
    }

    private fun buildFeishuTextMessage(message: String): JSONObject {
        val json = JSONObject()
        val text = JSONObject()
        text.put("text", message)
        json.put("msg_type", "text")
        json.put("content", text)
        return json
    }

    private fun buildGenericTextMessage(message: String): JSONObject {
        val json = JSONObject()
        json.put("message", message)
        return json
    }
}
