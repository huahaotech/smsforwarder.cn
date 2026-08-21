/*
 * 短信转发助手
 *
 * 著作权人：华昊科技有限公司
 * 开发者：王士辉
 *
 * Copyright (c) 2026 华昊科技有限公司. All rights reserved.
 * 联系邮箱：huahao@email.cn
 */

package com.lanbing.smsforwarder.ui

import androidx.compose.ui.graphics.Color

/**
 * 应用全局颜色常量
 *
 * 集中管理 UI 中使用的魔法颜色值，避免散落各处难以维护。
 * 命名遵循语义化原则（按用途而非具体色值）。
 */
object AppColors {

    // 主色调
    val Primary = Color(0xFF667EEA)
    val PrimaryDark = Color(0xFF764BA2)

    // 状态色
    val Success = Color(0xFF10B981)
    val SuccessLight = Color(0xFF22C55E)
    val Error = Color(0xFFEE4444)
    val ErrorDark = Color(0xFFDC2626)
    val Warning = Color(0xFFF59E0B)
    val WarningDark = Color(0xFFD97706)
    val Info = Color(0xFF3B82F6)
    val Gray = Color(0xFF9CA3AF)

    // 背景色
    val SurfaceMuted = Color(0xFFF5F5F5)
    val WarningSurface = Color(0xFFFFF3CD)
    val ErrorSurface = Color(0xFFFEF2F2)
    val WarningSurfaceSoft = Color(0xFFFEF3C7)

    // 文字色
    val WarningTextDark = Color(0xFF92400E)
    val ErrorTextDark = Color(0xFF991B1B)
    val ErrorTextDarker = Color(0xFFB91C1C)

    // 通道品牌色
    val ChannelWeCom = Color(0xFF07C160)    // 企业微信
    val ChannelDingTalk = Color(0xFF2080F0) // 钉钉
    val ChannelFeiShu = Color(0xFF2064E5)   // 飞书

    // 警告/提醒色
    val ImportWarning = Color(0xFFE67E22)
}
