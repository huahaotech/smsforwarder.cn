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

/**
 * 隐私政策内容集中管理
 *
 * 将隐私政策的所有文案从 MainActivity 中抽离，集中在此处管理，
 * 避免硬编码在 UI 代码中，便于后续更新和维护。
 */
object PrivacyPolicyContent {

    const val LAST_UPDATED = "2026年7月22日"
    const val APP_NAME = "短信转发助手"
    const val DEVELOPER = "华昊科技有限公司"
    const val CONTACT_EMAIL = "support@smsforwarder.cn"
    const val OFFICIAL_WEBSITE = "https://smsforwarder.cn/"
    const val ICP_NUMBER = "鲁ICP备2026018166号-2A"

    // 概述
    const val SUMMARY_INTRO =
        "短信转发助手（以下简称\"我们\"）非常重视用户的隐私保护。本隐私政策说明了我们如何收集、使用、存储和保护您的个人信息。使用我们的应用即表示您同意本政策中描述的做法。"

    val SUMMARY_ITEMS = listOf(
        "应用名称：$APP_NAME",
        "开发者：$DEVELOPER",
        "联系邮箱：$CONTACT_EMAIL",
        "官方网站：$OFFICIAL_WEBSITE",
        "备案号：$ICP_NUMBER"
    )

    // 核心原则
    val CORE_PRINCIPLES = listOf(
        "不上云：所有数据都在您的手机本地处理，不会上传到我们的服务器",
        "不收集：不会收集您的个人信息、短信内容等敏感数据",
        "不追踪：不集成任何统计、分析或广告 SDK",
        "完全可控：所有权限和数据都由您自己掌控"
    )

    // 短信内容说明
    const val SMS_CONTENT_TITLE = "短信内容（敏感信息）"
    const val SMS_CONTENT_PURPOSE = "用途：仅在您的手机本地用于匹配关键词规则和执行转发"
    const val SMS_CONTENT_STORAGE = "存储：不会保存到任何服务器，仅在转发时临时处理"
    const val SMS_CONTENT_WARNING =
        "重要：唯一会发送短信内容的情况是您主动配置了 Webhook 转发目标（如企业微信、钉钉、飞书或自定义 Webhook），应用会将短信直接发送到您指定的目标，不会经过我们的服务器。"

    // 配置信息
    const val CONFIG_TITLE = "配置信息"
    const val CONFIG_DESC = "您设置的转发通道、关键词规则等配置信息保存在您手机的本地存储中，不会上传。"

    // 转发日志
    const val LOG_TITLE = "转发日志"
    const val LOG_DESC = "应用会在本地记录转发历史（最多200条），方便您查看和调试，这些日志仅存储在您的手机上。"

    // 权限说明
    val PERMISSION_ITEMS = listOf(
        "接收短信权限：监听设备收到的短信，用于执行转发功能",
        "通知权限：显示前台服务通知，让您知道服务正在运行",
        "读取手机状态权限：用于识别双卡手机的 SIM 卡信息和获取本机号码（可选）",
        "网络权限：仅用于转发到您配置的 Webhook",
        "前台服务权限：保持应用在后台稳定运行",
        "开机自启权限：让应用在开机后自动启动转发服务（可选）",
        "忽略电池优化权限：防止系统杀死后台服务（可选）",
        "访问网络状态权限：检测网络连接状态"
    )
    const val PERMISSION_FOOTER = "所有权限都需要您主动授权，您可以随时在系统设置中撤销。"

    // 数据存储
    const val STORAGE_LOCAL_TITLE = "本地存储"
    const val STORAGE_LOCAL_INTRO = "所有数据都存储在您手机的私有目录中，包括："
    val STORAGE_LOCAL_ITEMS = listOf(
        "转发通道和关键词配置",
        "转发历史日志",
        "应用设置"
    )

    const val STORAGE_SERVER_TITLE = "服务器存储"
    const val STORAGE_SERVER_DESC =
        "我们没有服务器存储您的数据！应用是纯本地运行的工具，我们不收集、不存储、不上传任何用户数据。"

    // 用户权利
    val USER_RIGHTS = listOf(
        "查看数据：可以在应用内查看所有转发日志",
        "删除数据：可以在应用内清空日志，或卸载应用删除所有数据",
        "控制权限：可以在系统设置中随时授予或撤销权限",
        "撤回同意：可以在应用设置中撤回隐私政策同意"
    )

    // 第三方服务
    const val THIRD_PARTY_WARNING =
        "关于转发目标：如果您配置了 Webhook 或其他第三方服务作为转发目标，短信内容会发送到该第三方。请您谨慎选择转发目标，并确保了解其隐私政策。我们不对第三方的数据处理负责。"
    const val THIRD_PARTY_SDK_TITLE = "第三方 SDK"
    const val THIRD_PARTY_SDK_DESC = "当前版本未集成任何第三方 SDK（包括统计、广告、崩溃分析等）。"

    // 政策更新
    const val POLICY_UPDATE = "我们可能会不时更新本隐私政策。重大变更时，我们会通过应用内通知或其他方式告知您。建议您定期查看本政策以了解最新信息。"

    // 联系我们
    const val CONTACT_US = "如果您对本隐私政策有任何疑问或建议，请通过以下方式联系我们："
}
