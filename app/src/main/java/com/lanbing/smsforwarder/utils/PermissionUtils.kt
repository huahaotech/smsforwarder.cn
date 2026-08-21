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
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

/**
 * 权限跳转工具类
 *
 * 统一封装跳转到系统设置页面的逻辑，消除 MainActivity 中 8 处重复的权限跳转代码。
 * 所有需要跳转到设置页面的地方都应使用此类。
 */
object PermissionUtils {

    /**
     * 打开应用详情设置页面
     *
     * 用于引导用户在应用详情中授予权限（通知、电话、存储等）。
     *
     * @param context 上下文
     * @param onError 跳转失败时的回调，默认弹出 Toast 提示
     */
    fun openAppSettings(context: Context, onError: (() -> Unit)? = null) {
        try {
            val intent = Intent().apply {
                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                data = Uri.fromParts("package", context.packageName, null)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            if (onError != null) {
                onError()
            } else {
                Toast.makeText(context, "请手动打开系统设置", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 打开电池优化设置页面
     *
     * 用于引导用户将应用加入电池优化白名单，保证后台服务稳定运行。
     *
     * @param context 上下文
     * @param onError 跳转失败时的回调，默认弹出 Toast 提示
     */
    fun openBatteryOptimizationSettings(context: Context, onError: (() -> Unit)? = null) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            if (onError != null) {
                onError()
            } else {
                Toast.makeText(context, "请手动打开系统设置", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 检查电池优化是否已忽略（即应用是否在白名单中）
     *
     * @param context 上下文
     * @return true 表示已忽略电池优化，false 表示未忽略
     */
    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }
}
