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
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log

/**
 * 网络状态工具类
 *
 * 迁移说明：
 * 原 NetworkChangeReceiver 通过监听 CONNECTIVITY_ACTION 广播实现网络状态监听，
 * 但该 API 在 Android 7.0 (API 24) 已废弃，且目标 API 26+ 上静态注册完全失效。
 *
 * 当前网络恢复重试已由 SmsForegroundService 中的 ConnectivityManager.NetworkCallback
 * 承担（动态注册，与服务生命周期一致），因此本类不再继承 BroadcastReceiver，
 * 仅保留 isNetworkAvailable 静态工具方法供 SmsReceiver 等组件查询使用。
 *
 * 如未来需要更细粒度的网络监听，应统一通过 NetworkCallback 扩展，不要再使用
 * CONNECTIVITY_ACTION 广播。
 */
object NetworkMonitor {

    private const val TAG = "NetworkMonitor"

    /**
     * 检查当前网络是否可用
     *
     * 只要求具备 INTERNET 能力即可尝试转发。
     * 不强制 NET_CAPABILITY_VALIDATED：国内网络环境下该状态常不稳定
     * （移动网络切换延迟、部分 WiFi/运营商长期未验证），会导致定时重试被误判为无网而永远跳过。
     * 即使实际无法上网，sendToWebhook 失败后消息仍会保留（retryCount+1），不会丢失。
     */
    fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork ?: return false
                val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                networkInfo?.isConnected == true
            }
        } catch (t: Throwable) {
            Log.e(TAG, "检查网络可用性时出错", t)
            false
        }
    }
}
