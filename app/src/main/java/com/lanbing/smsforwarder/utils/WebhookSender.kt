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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
     * @param messageTemplate 自定义消息模板（null 则使用默认模板）
     * @param matchedKeyword 命中的关键词（用于模板占位符）
     * @param channelName 通道名称（用于模板占位符）
     * @return 转发结果
     */
    fun sendSmsForward(
        webhookUrl: String,
        sender: String,
        content: String,
        receiverPhoneNumber: String?,
        type: ChannelType,
        showSenderPhone: Boolean,
        highlightVerificationCode: Boolean,
        messageTemplate: String? = null,
        matchedKeyword: String? = null,
        channelName: String? = null
    ): ForwardResult {
        val params = MessageTemplateRenderer.TemplateParams(
            sender = sender,
            content = content,
            receiverPhone = receiverPhoneNumber,
            verificationCode = if (highlightVerificationCode) VerificationCodeExtractor.extract(content) else null,
            matchedKeyword = matchedKeyword,
            channelName = channelName
        )

        val json = when (type) {
            ChannelType.FEISHU -> buildFeishuSmsMessage(params, showSenderPhone, highlightVerificationCode, messageTemplate)
            ChannelType.WECHAT -> buildWechatSmsMessage(params, showSenderPhone, highlightVerificationCode, messageTemplate)
            ChannelType.DINGTALK -> buildDingtalkSmsMessage(params, showSenderPhone, highlightVerificationCode, messageTemplate)
            ChannelType.GENERIC_WEBHOOK -> buildGenericSmsMessage(params, showSenderPhone, highlightVerificationCode, messageTemplate)
        }

        return sendJsonRequest(webhookUrl, json)
    }

    /**
     * 发送测试消息到指定 Webhook（用于验证通道配置是否正确）
     */
    fun sendTestMessage(webhookUrl: String, type: ChannelType, channelName: String? = null): ForwardResult {
        val params = MessageTemplateRenderer.TemplateParams(
            sender = "10086",
            content = "【短信转发助手】这是一条测试消息，用于验证通道配置是否正常。\n如果您收到此消息，说明配置成功！",
            receiverPhone = null,
            verificationCode = "123456",
            matchedKeyword = "测试",
            channelName = channelName
        )

        val json = when (type) {
            ChannelType.WECHAT -> buildWechatSmsMessage(params, showSenderPhone = true, highlightVerificationCode = true, template = null)
            ChannelType.DINGTALK -> buildDingtalkSmsMessage(params, showSenderPhone = true, highlightVerificationCode = true, template = null)
            ChannelType.FEISHU -> buildFeishuSmsMessage(params, showSenderPhone = true, highlightVerificationCode = true, template = null)
            ChannelType.GENERIC_WEBHOOK -> buildGenericSmsMessage(params, showSenderPhone = true, highlightVerificationCode = true, template = null)
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

    /**
     * 构建消息正文文本：优先使用自定义模板，否则使用默认格式
     */
    private fun buildMessageText(
        params: MessageTemplateRenderer.TemplateParams,
        showSenderPhone: Boolean,
        highlightVerificationCode: Boolean,
        template: String?
    ): String {
        return if (!template.isNullOrBlank()) {
            MessageTemplateRenderer.render(template, params)
        } else {
            MessageTemplateRenderer.buildDefaultMessage(params, showSenderPhone, highlightVerificationCode)
        }
    }

    private fun buildWechatSmsMessage(
        params: MessageTemplateRenderer.TemplateParams,
        showSenderPhone: Boolean,
        highlightVerificationCode: Boolean,
        template: String?
    ): JSONObject {
        val json = JSONObject()
        json.put("msgtype", "text")
        val text = JSONObject()
        text.put("content", buildMessageText(params, showSenderPhone, highlightVerificationCode, template))
        json.put("text", text)
        return json
    }

    private fun buildDingtalkSmsMessage(
        params: MessageTemplateRenderer.TemplateParams,
        showSenderPhone: Boolean,
        highlightVerificationCode: Boolean,
        template: String?
    ): JSONObject {
        val json = JSONObject()
        json.put("msgtype", "text")
        val text = JSONObject()
        text.put("content", buildMessageText(params, showSenderPhone, highlightVerificationCode, template))
        json.put("text", text)
        return json
    }

    private fun buildFeishuSmsMessage(
        params: MessageTemplateRenderer.TemplateParams,
        showSenderPhone: Boolean,
        highlightVerificationCode: Boolean,
        template: String?
    ): JSONObject {
        val json = JSONObject()
        json.put("msg_type", "text")
        val text = JSONObject()
        text.put("text", buildMessageText(params, showSenderPhone, highlightVerificationCode, template))
        json.put("content", text)
        return json
    }

    private fun buildGenericSmsMessage(
        params: MessageTemplateRenderer.TemplateParams,
        showSenderPhone: Boolean,
        highlightVerificationCode: Boolean,
        template: String?
    ): JSONObject {
        val json = JSONObject()
        if (showSenderPhone) {
            json.put("sender", params.sender)
        }
        if (params.receiverPhone != null) {
            json.put("receiver", params.receiverPhone)
        }
        // 如果有自定义模板，content 为渲染后的完整内容；否则为原始内容
        if (!template.isNullOrBlank()) {
            json.put("content", MessageTemplateRenderer.render(template, params))
        } else {
            json.put("content", params.content)
        }
        if (highlightVerificationCode) {
            json.put("verificationCode", params.verificationCode)
        }
        json.put("timestamp", params.timestamp)
        if (params.matchedKeyword != null) {
            json.put("matchedKeyword", params.matchedKeyword)
        }
        if (params.channelName != null) {
            json.put("channelName", params.channelName)
        }
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
