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

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.lanbing.smsforwarder.Constants

/**
 * SIM 卡信息工具类
 *
 * 统一封装 SIM 卡号码获取、卡槽识别等逻辑，消除 SmsReceiver、BatteryMonitor、MainActivity
 * 三处重复的 SIM 卡相关代码。
 *
 * 废弃 API 使用说明（迁移计划）：
 * - SubscriptionManager.from() 在 API 33 已废弃，替代方案为
 *   context.getSystemService(SubscriptionManager::class.java)，但后者需要 API 22+。
 *   目前 minSdk 为 21，因此保留 from() 兼容旧版本。当 minSdk 提升至 22+ 时可统一替换。
 * - SubscriptionInfo.getNumber() / TelephonyManager.line1Number 在 API 33 废弃，
 *   替代方案需要 READ_PHONE_NUMBERS 权限（API 31+）。当前使用 READ_PHONE_STATE 兼容，
 *   当 minSdk 提升至 31+ 时可迁移到新权限和 API。
 * - 所有 @Suppress("DEPRECATION") 都标记了上述废弃 API，可在 minSdk 提升时集中清理。
 */
object SimInfoUtils {

    private const val TAG = "SimInfoUtils"

    /**
     * 获取接收短信的本机号码
     *
     * @param context 上下文
     * @param subscriptionId SIM 卡的 subscriptionId，用于确定是哪个 SIM 卡
     * @param prefs SharedPreferences，用于读取自定义 SIM 号码配置
     * @return 本机号码，如果无法获取则返回 null
     */
    fun getReceiverPhoneNumber(
        context: Context,
        subscriptionId: Int?,
        prefs: android.content.SharedPreferences
    ): String? {
        try {
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
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取本机号码失败", e)
        }
        return null
    }

    /**
     * 获取 SIM 卡号码信息字符串（用于在提醒消息中标识设备）
     *
     * @param context 上下文
     * @param prefs SharedPreferences
     * @return 格式化的 SIM 卡号码字符串，如 "138****1234 / 139****5678"，无号码则返回空字符串
     */
    fun getSimPhoneInfo(context: Context, prefs: android.content.SharedPreferences): String {
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
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
                            Log.e(TAG, "获取 SIM 卡号码失败", e)
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
            Log.e(TAG, "获取 SIM 卡信息失败", e)
        }

        return if (phoneNumbers.isNotEmpty()) {
            phoneNumbers.joinToString(" / ")
        } else {
            ""
        }
    }

    /**
     * 检查是否有 READ_PHONE_STATE 权限
     */
    fun hasReadPhoneStatePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    }
}
