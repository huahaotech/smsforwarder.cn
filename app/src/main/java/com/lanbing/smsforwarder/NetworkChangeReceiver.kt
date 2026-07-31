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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.Executors

/**
 * NetworkChangeReceiver: 监听网络状态变化，网络恢复时触发失败消息重试
 */
class NetworkChangeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NetworkChangeReceiver"
        private var lastRetryTime = 0L
        private var lastNetworkState = false
        private val executor = Executors.newSingleThreadExecutor()

        fun isNetworkAvailable(context: Context): Boolean {
            return try {
                val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val network = connectivityManager.activeNetwork ?: return false
                    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
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

    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ConnectivityManager.CONNECTIVITY_ACTION) return

        val isAvailable = isNetworkAvailable(context)
        val now = System.currentTimeMillis()

        if (isAvailable && !lastNetworkState && (now - lastRetryTime > Constants.NETWORK_DEBOUNCE_MS)) {
            lastRetryTime = now
            LogStore.append(context, "网络已恢复，正在重试失败转发")
            val ctx = context.applicationContext
            val pendingResult = goAsync()
            executor.execute {
                try {
                    SmsReceiver.retryFailedMessages(ctx, forceAll = true)
                } finally {
                    pendingResult.finish()
                }
            }
        }

        lastNetworkState = isAvailable
    }
}
