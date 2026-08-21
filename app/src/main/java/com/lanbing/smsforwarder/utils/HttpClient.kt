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

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 全局共享的 OkHttpClient 单例
 *
 * 统一管理 HTTP 客户端实例，避免多处创建导致的连接池无法复用。
 * 所有需要发送网络请求的地方都应使用此单例。
 */
object HttpClient {

    /**
     * 共享的 OkHttpClient 实例
     *
     * 超时配置与 Constants 保持一致：
     * - 调用超时：20秒
     * - 连接超时：10秒
     * - 读取超时：20秒
     */
    val instance: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(com.lanbing.smsforwarder.Constants.CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .connectTimeout(com.lanbing.smsforwarder.Constants.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(com.lanbing.smsforwarder.Constants.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }
}
