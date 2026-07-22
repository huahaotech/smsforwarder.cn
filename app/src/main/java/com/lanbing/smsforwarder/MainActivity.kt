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
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.asImageBitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.FileObserver
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.*

/**
 * MainActivity: 现代简洁风格的短信转发助手主界面
 * - Material Design 3 设计
 * - 支持规则测试功能
 * - 添加电量优化白名单引导
 * - 更现代的UI设计
 */

class MainActivity : ComponentActivity() {

    private var onPermissionChanged: (() -> Unit)? = null
    private var onConfigChanged: (() -> Unit)? = null
    private lateinit var configImportLauncher: androidx.activity.result.ActivityResultLauncher<String>
    private lateinit var imagePickerLauncher: androidx.activity.result.ActivityResultLauncher<PickVisualMediaRequest>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        configImportLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                importConfig(this, it) {
                    onPermissionChanged?.invoke()
                    onConfigChanged?.invoke()
                }
            }
        }

        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let {
                decodeQrCodeFromImage(this, uri) { content ->
                    content?.let { jsonStr ->
                        importConfigFromJson(this, jsonStr) {
                            onPermissionChanged?.invoke()
                            onConfigChanged?.invoke()
                        }
                    } ?: run {
                        Toast.makeText(this, "未能识别图片中的二维码", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        setContent {
            val isDarkTheme = isSystemInDarkTheme()
            val colorScheme = if (isDarkTheme) darkColorScheme() else lightColorScheme()
            var permissionUpdateTrigger by remember { mutableStateOf(0) }
            var configUpdateTrigger by remember { mutableStateOf(0) }

            MaterialTheme(
                colorScheme = colorScheme,
                typography = Typography()
            ) {
                val activity = LocalContext.current as Activity
                SideEffect {
                    try {
                        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                        activity.window.statusBarColor = AndroidColor.TRANSPARENT
                        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
                        controller.isAppearanceLightStatusBars = !isDarkTheme
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            controller.isAppearanceLightNavigationBars = !isDarkTheme
                        }
                    } catch (_: Throwable) {}
                }

                SideEffect {
                    onPermissionChanged = { permissionUpdateTrigger++ }
                    onConfigChanged = { configUpdateTrigger++ }
                }

                SmsForwarderApp(
                    permissionUpdateTrigger = permissionUpdateTrigger,
                    configUpdateTrigger = configUpdateTrigger,
                    onRequestSmsPermission = {
                        try {
                            val intent = Intent().apply {
                                action = "android.settings.APPLICATION_DETAILS_SETTINGS"
                                data = Uri.fromParts("package", packageName, null)
                            }
                            startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(this, "请手动打开系统设置", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onRequestNotificationPermission = {
                        try {
                            val intent = Intent().apply {
                                action = "android.settings.APPLICATION_DETAILS_SETTINGS"
                                data = Uri.fromParts("package", packageName, null)
                            }
                            startActivity(intent)
                        } catch (_: Exception) {
                            Toast.makeText(this, "请手动打开系统设置", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onStartService = { startServiceWithNotificationCheck() },
                    onStopService = { onStopService() },
                    onExportConfig = { channels, configs, showReceiverPhone, showSenderPhone, highlightVerificationCode, batteryReminderEnabled, lowBatteryReminderEnabled, highBatteryReminderEnabled, chargingReminderEnabled, batteryReminderChannelId, lowBatteryThreshold, highBatteryThreshold, customSim1Phone, customSim2Phone, startOnBoot ->
                        exportConfig(this, channels, configs, showReceiverPhone, showSenderPhone, highlightVerificationCode, batteryReminderEnabled, lowBatteryReminderEnabled, highBatteryReminderEnabled, chargingReminderEnabled, batteryReminderChannelId, lowBatteryThreshold, highBatteryThreshold, customSim1Phone, customSim2Phone, startOnBoot)
                    },
                    onImportConfig = {
                        val intent = Intent(this, ScanActivity::class.java)
                        startActivity(intent)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 从设置页面回来时，刷新权限状态
        onPermissionChanged?.invoke()
    }

    private fun onStopService() {
        val svc = Intent(this, SmsForegroundService::class.java)
        stopService(svc)
    }

    private fun startServiceWithNotificationCheck() {
        val pkg = packageName
        val notifEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
        if (!notifEnabled) {
            Toast.makeText(this, "请允许应用通知（将打开通知设置）", Toast.LENGTH_LONG).show()
            val i = Intent().apply {
                action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
            }
            startActivity(i)
            return
        }

        val smsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        if (!smsGranted) return

        val svc = Intent(this, SmsForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc) else startService(svc)
    }
}

fun isValidWebhookUrl(url: String): Boolean {
    return try {
        val u = java.net.URL(url)
        (u.protocol == "http" || u.protocol == "https") && u.host.isNotBlank()
    } catch (e: java.net.MalformedURLException) {
        false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsForwarderApp(
    permissionUpdateTrigger: Int,
    configUpdateTrigger: Int,
    onRequestSmsPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onExportConfig: (
        channels: List<Channel>,
        configs: List<KeywordConfig>,
        showReceiverPhone: Boolean,
        showSenderPhone: Boolean,
        highlightVerificationCode: Boolean,
        batteryReminderEnabled: Boolean,
        lowBatteryReminderEnabled: Boolean,
        highBatteryReminderEnabled: Boolean,
        chargingReminderEnabled: Boolean,
        batteryReminderChannelId: String?,
        lowBatteryThreshold: Int,
        highBatteryThreshold: Int,
        customSim1Phone: String?,
        customSim2Phone: String?,
        startOnBoot: Boolean
    ) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
    onImportConfig: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    var isEnabled by remember(configUpdateTrigger) { mutableStateOf(prefs.getBoolean(Constants.PREF_ENABLED, false)) }
    var startOnBoot by remember(configUpdateTrigger) { mutableStateOf(prefs.getBoolean(Constants.PREF_START_ON_BOOT, false)) }
    var showReceiverPhone by remember(configUpdateTrigger) { mutableStateOf(prefs.getBoolean(Constants.PREF_SHOW_RECEIVER_PHONE, true)) }
    var showSenderPhone by remember(configUpdateTrigger) { mutableStateOf(prefs.getBoolean(Constants.PREF_SHOW_SENDER_PHONE, true)) }
    var highlightVerificationCode by remember(configUpdateTrigger) { mutableStateOf(prefs.getBoolean(Constants.PREF_HIGHLIGHT_VERIFICATION_CODE, true)) }
    var batteryReminderEnabled by remember(configUpdateTrigger) { mutableStateOf(prefs.getBoolean(Constants.PREF_BATTERY_REMINDER_ENABLED, false)) }
    var lowBatteryReminderEnabled by remember(configUpdateTrigger) { mutableStateOf(prefs.getBoolean(Constants.PREF_LOW_BATTERY_REMINDER_ENABLED, true)) }
    var highBatteryReminderEnabled by remember(configUpdateTrigger) { mutableStateOf(prefs.getBoolean(Constants.PREF_HIGH_BATTERY_REMINDER_ENABLED, true)) }
    var chargingReminderEnabled by remember(configUpdateTrigger) { mutableStateOf(prefs.getBoolean(Constants.PREF_CHARGING_REMINDER_ENABLED, false)) }
    var batteryReminderChannelId by remember(configUpdateTrigger) { mutableStateOf(prefs.getString(Constants.PREF_BATTERY_REMINDER_CHANNEL_ID, null)) }
    var lowBatteryThreshold by remember(configUpdateTrigger) { mutableStateOf(prefs.getInt(Constants.PREF_LOW_BATTERY_THRESHOLD, Constants.DEFAULT_LOW_BATTERY_THRESHOLD)) }
    var highBatteryThreshold by remember(configUpdateTrigger) { mutableStateOf(prefs.getInt(Constants.PREF_HIGH_BATTERY_THRESHOLD, Constants.DEFAULT_HIGH_BATTERY_THRESHOLD)) }
    var customSim1Phone by remember(configUpdateTrigger) { mutableStateOf(prefs.getString(Constants.PREF_CUSTOM_SIM1_PHONE, null)) }
    var customSim2Phone by remember(configUpdateTrigger) { mutableStateOf(prefs.getString(Constants.PREF_CUSTOM_SIM2_PHONE, null)) }

    var channels by remember(configUpdateTrigger) { mutableStateOf(loadChannels(prefs)) }
    var configs by remember(configUpdateTrigger) { mutableStateOf(loadConfigs(prefs)) }

    // Battery reminder channel selection dialog
    var showChannelSelectionDialog by remember { mutableStateOf(false) }

    // QR Code dialog
    var showQrCodeDialog by remember { mutableStateOf(false) }
    var qrCodeBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    // Channel form state
    var newChannelName by remember { mutableStateOf("") }
    var newChannelTarget by remember { mutableStateOf("") }
    var newChannelType by remember { mutableStateOf(ChannelType.WECHAT) }
    var channelTypeExpanded by remember { mutableStateOf(false) }

    // Config form state
    var newKeywordInput by remember { mutableStateOf("") }
    var selectedChannelIdForNewCfg by remember { mutableStateOf(channels.firstOrNull()?.id ?: "") }
    var configChannelDropdownExpanded by remember { mutableStateOf(false) }

    // Editing state
    var editingChannel by remember { mutableStateOf<Channel?>(null) }
    var showChannelDialog by remember { mutableStateOf(false) }
    var editChannelName by remember { mutableStateOf("") }
    var editChannelTarget by remember { mutableStateOf("") }
    var editChannelType by remember { mutableStateOf(ChannelType.WECHAT) }

    var editingConfig by remember { mutableStateOf<KeywordConfig?>(null) }
    var showConfigDialog by remember { mutableStateOf(false) }
    var editConfigKeyword by remember { mutableStateOf("") }
    var editConfigChannelId by remember { mutableStateOf("") }

    // UI state
    var logs by remember { mutableStateOf(LogStore.readAll(context)) }
    var currentTab by remember { mutableStateOf<Int>(0) }
    var showTestDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showBootTipDialog by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        val hasAgreedPrivacy = prefs.getBoolean(Constants.PREF_PRIVACY_AGREED, false)
        if (!hasAgreedPrivacy) {
            showPrivacyDialog = true
        }
    }
    
    DisposableEffect(Unit) {
        val logFile = File(context.filesDir, Constants.LOG_FILE_NAME)
        val observer = object : FileObserver(logFile.parent ?: context.filesDir.absolutePath) {
            override fun onEvent(event: Int, path: String?) {
                if (path == Constants.LOG_FILE_NAME && event and (MODIFY or CREATE) != 0) {
                    logs = LogStore.readAll(context)
                }
            }
        }
        observer.startWatching()
        onDispose {
            observer.stopWatching()
        }
    }
    
    // 定义5个标签页
    val tabs = listOf(
        "首页" to Icons.Default.Home,
        "关键词" to Icons.Default.Label,
        "通道" to Icons.Default.Cloud,
        "设置" to Icons.Default.Settings,
        "日志" to Icons.Default.History
    )

    // Permission states (use key to trigger recomposition)
    val smsGranted by remember(permissionUpdateTrigger) {
        derivedStateOf {
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        }
    }
    val notifGranted by remember(permissionUpdateTrigger) {
        derivedStateOf {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    // Battery optimization state
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val isIgnoringBatteryOptimizations by remember(permissionUpdateTrigger) {
        derivedStateOf {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            } else true
        }
    }

    Scaffold(
        modifier = Modifier.background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.surface,
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            )
        ),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(Color(0xFF667EEA), Color(0xFF764BA2))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Message,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "短信转发助手",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (isEnabled) "服务运行中" else "服务已停止",
                                fontSize = 12.sp,
                                color = if (isEnabled) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showAboutDialog = true }) {
                        Icon(Icons.Outlined.Info, contentDescription = "关于")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                tabs.forEachIndexed { index, (label, icon) ->
                    NavigationBarItem(
                        selected = currentTab == index,
                        onClick = { currentTab = index },
                        icon = {
                            val filledIcon = when (index) {
                                0 -> Icons.Filled.Home
                                1 -> Icons.Filled.Label
                                2 -> Icons.Filled.Cloud
                                3 -> Icons.Filled.Settings
                                4 -> Icons.Filled.History
                                else -> Icons.Filled.Home
                            }
                            val outlinedIcon = when (index) {
                                0 -> Icons.Outlined.Home
                                1 -> Icons.Outlined.Label
                                2 -> Icons.Outlined.Cloud
                                3 -> Icons.Outlined.Settings
                                4 -> Icons.Outlined.History
                                else -> Icons.Outlined.Home
                            }
                            Icon(
                                if (currentTab == index) filledIcon else outlinedIcon,
                                contentDescription = label
                            )
                        },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            AnimatedContent(
                targetState = currentTab,
                label = "tabAnimation"
            ) { tabIndex ->
                when (tabIndex) {
                    0 -> HomeTab(
                        isEnabled = isEnabled,
                        onEnabledChange = { checked ->
                            if (checked) {
                                val hasNotif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                                } else {
                                    true
                                }
                                val hasSms = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
                                
                                if (!hasNotif || !hasSms) {
                                    showPermissionDialog = true
                                    return@HomeTab
                                }
                                
                                isEnabled = true
                                prefs.edit().putBoolean(Constants.PREF_ENABLED, isEnabled).apply()
                                onStartService()
                                LogStore.append(context, "服务已启动（由用户开启）")
                                context.sendBroadcast(Intent(SmsForegroundService.ACTION_UPDATE))
                            } else {
                                isEnabled = false
                                prefs.edit().putBoolean(Constants.PREF_ENABLED, isEnabled).apply()
                                onStopService()
                                LogStore.append(context, "服务已停止（由用户关闭）")
                                context.sendBroadcast(Intent(SmsForegroundService.ACTION_UPDATE))
                            }
                        },
                        startOnBoot = startOnBoot,
                        onStartOnBootChange = {
                            startOnBoot = it
                            prefs.edit().putBoolean(Constants.PREF_START_ON_BOOT, startOnBoot).apply()
                            if (startOnBoot) {
                                LogStore.append(context, "已开启开机启动")
                                showBootTipDialog = true
                            } else {
                                LogStore.append(context, "已关闭开机启动")
                            }
                        },
                        smsGranted = smsGranted,
                        notifGranted = notifGranted,
                        isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations,
                        onRequestSmsPermission = onRequestSmsPermission,
                        onRequestNotificationPermission = onRequestNotificationPermission,
                        onRequestBatteryOptimization = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            }
                        }
                    )
                    1 -> KeywordTab(
                        channels = channels,
                        configs = configs,
                        newKeywordInput = newKeywordInput,
                        onNewKeywordInputChange = { newKeywordInput = it },
                        selectedChannelIdForNewCfg = selectedChannelIdForNewCfg,
                        onSelectedChannelIdForNewCfgChange = { selectedChannelIdForNewCfg = it },
                        configChannelDropdownExpanded = configChannelDropdownExpanded,
                        onConfigChannelDropdownExpandedChange = { configChannelDropdownExpanded = it },
                        onAddConfig = {
                            if (channels.isEmpty()) {
                                Toast.makeText(context, "请先添加通道", Toast.LENGTH_SHORT).show()
                                return@KeywordTab
                            }
                            if (selectedChannelIdForNewCfg.isBlank()) {
                                Toast.makeText(context, "请选择通道", Toast.LENGTH_SHORT).show()
                                return@KeywordTab
                            }
                            val newCfg = KeywordConfig(UUID.randomUUID().toString(), newKeywordInput.trim(), selectedChannelIdForNewCfg)
                            configs = configs + newCfg
                            saveConfigs(prefs, configs)
                            newKeywordInput = ""
                            LogStore.append(context, "添加关键词: ${newCfg.keyword} -> ${channels.find { it.id == newCfg.channelId }?.name}")
                            Toast.makeText(context, "配置已添加", Toast.LENGTH_SHORT).show()
                        },
                        onDeleteConfig = { cfg ->
                            configs = configs.filterNot { it.id == cfg.id }
                            saveConfigs(prefs, configs)
                        },
                        onEditConfig = { cfg ->
                            editingConfig = cfg
                            editConfigKeyword = cfg.keyword
                            editConfigChannelId = cfg.channelId
                            showConfigDialog = true
                        }
                    )
                    2 -> ChannelTab(
                        channels = channels,
                        newChannelName = newChannelName,
                        onNewChannelNameChange = { newChannelName = it },
                        newChannelTarget = newChannelTarget,
                        onNewChannelTargetChange = { newChannelTarget = it },
                        newChannelType = newChannelType,
                        onNewChannelTypeChange = { newChannelType = it },
                        channelTypeExpanded = channelTypeExpanded,
                        onChannelTypeExpandedChange = { channelTypeExpanded = it },
                        onAddChannel = {
                            if (newChannelName.isBlank() || newChannelTarget.isBlank()) {
                                Toast.makeText(context, "请填写通道名称和 Webhook 地址", Toast.LENGTH_SHORT).show()
                                return@ChannelTab
                            }
                            if (!isValidWebhookUrl(newChannelTarget)) {
                                Toast.makeText(context, "Webhook 地址格式无效，请输入有效的 http:// 或 https:// 地址", Toast.LENGTH_SHORT).show()
                                return@ChannelTab
                            }
                            val newChannel = Channel(UUID.randomUUID().toString(), newChannelName.trim(), newChannelType, newChannelTarget.trim())
                            channels = channels + newChannel
                            saveChannels(prefs, channels)
                            selectedChannelIdForNewCfg = channels.firstOrNull()?.id ?: ""
                            newChannelName = ""
                            newChannelTarget = ""
                            LogStore.append(context, "添加通道: ${newChannel.name} (${newChannel.type})")
                            Toast.makeText(context, "通道已添加", Toast.LENGTH_SHORT).show()
                        },
                        onDeleteChannel = { ch ->
                            channels = channels.filterNot { it.id == ch.id }
                            saveChannels(prefs, channels)
                            configs = configs.filterNot { it.channelId == ch.id }
                            saveConfigs(prefs, configs)
                            LogStore.append(context, "删除通道: ${ch.name}")
                        },
                        onEditChannel = { ch ->
                            editingChannel = ch
                            editChannelName = ch.name
                            editChannelTarget = ch.target
                            editChannelType = ch.type
                            showChannelDialog = true
                        }
                    )
                    3 -> SettingsTab(
                        showReceiverPhone = showReceiverPhone,
                        onShowReceiverPhoneChange = {
                            showReceiverPhone = it
                            prefs.edit().putBoolean(Constants.PREF_SHOW_RECEIVER_PHONE, showReceiverPhone).apply()
                            if (showReceiverPhone) LogStore.append(context, "已开启显示本机号码") else LogStore.append(context, "已关闭显示本机号码")
                        },
                        showSenderPhone = showSenderPhone,
                        onShowSenderPhoneChange = {
                            showSenderPhone = it
                            prefs.edit().putBoolean(Constants.PREF_SHOW_SENDER_PHONE, showSenderPhone).apply()
                            if (showSenderPhone) LogStore.append(context, "已开启显示发送者号码") else LogStore.append(context, "已关闭显示发送者号码")
                        },
                        highlightVerificationCode = highlightVerificationCode,
                        onHighlightVerificationCodeChange = {
                            highlightVerificationCode = it
                            prefs.edit().putBoolean(Constants.PREF_HIGHLIGHT_VERIFICATION_CODE, highlightVerificationCode).apply()
                            if (highlightVerificationCode) LogStore.append(context, "已开启突出显示验证码") else LogStore.append(context, "已关闭突出显示验证码")
                        },
                        batteryReminderEnabled = batteryReminderEnabled,
                        onBatteryReminderEnabledChange = {
                            batteryReminderEnabled = it
                            prefs.edit().putBoolean(Constants.PREF_BATTERY_REMINDER_ENABLED, batteryReminderEnabled).apply()
                            if (batteryReminderEnabled) LogStore.append(context, "已开启电量提醒") else LogStore.append(context, "已关闭电量提醒")
                        },
                        lowBatteryReminderEnabled = lowBatteryReminderEnabled,
                        onLowBatteryReminderEnabledChange = {
                            lowBatteryReminderEnabled = it
                            prefs.edit().putBoolean(Constants.PREF_LOW_BATTERY_REMINDER_ENABLED, lowBatteryReminderEnabled).apply()
                            if (lowBatteryReminderEnabled) LogStore.append(context, "已开启低电量提醒") else LogStore.append(context, "已关闭低电量提醒")
                        },
                        highBatteryReminderEnabled = highBatteryReminderEnabled,
                        onHighBatteryReminderEnabledChange = {
                            highBatteryReminderEnabled = it
                            prefs.edit().putBoolean(Constants.PREF_HIGH_BATTERY_REMINDER_ENABLED, highBatteryReminderEnabled).apply()
                            if (highBatteryReminderEnabled) LogStore.append(context, "已开启高电量提醒") else LogStore.append(context, "已关闭高电量提醒")
                        },
                        chargingReminderEnabled = chargingReminderEnabled,
                        onChargingReminderEnabledChange = {
                            chargingReminderEnabled = it
                            prefs.edit().putBoolean(Constants.PREF_CHARGING_REMINDER_ENABLED, chargingReminderEnabled).apply()
                            if (chargingReminderEnabled) LogStore.append(context, "已开启充电提醒") else LogStore.append(context, "已关闭充电提醒")
                        },
                        batteryReminderChannelId = batteryReminderChannelId,
                        onBatteryReminderChannelIdChange = {
                            batteryReminderChannelId = it
                            prefs.edit().putString(Constants.PREF_BATTERY_REMINDER_CHANNEL_ID, batteryReminderChannelId).apply()
                        },
                        channels = channels,
                        lowBatteryThreshold = lowBatteryThreshold,
                        onLowBatteryThresholdChange = {
                            lowBatteryThreshold = it
                            prefs.edit().putInt(Constants.PREF_LOW_BATTERY_THRESHOLD, lowBatteryThreshold).apply()
                        },
                        highBatteryThreshold = highBatteryThreshold,
                        onHighBatteryThresholdChange = {
                            highBatteryThreshold = it
                            prefs.edit().putInt(Constants.PREF_HIGH_BATTERY_THRESHOLD, highBatteryThreshold).apply()
                        },
                        onShowTestDialog = { showTestDialog = true },
                        onRevokePrivacyConsent = {
                            prefs.edit().remove(Constants.PREF_PRIVACY_AGREED).apply()
                            (context as Activity).finish()
                        },
                        onShowPrivacyPolicy = { showPrivacyDialog = true },
                        permissionUpdateTrigger = permissionUpdateTrigger,
                        startOnBoot = startOnBoot,
                        onExportConfig = {
                            val jsonStr = generateConfigJson(channels, configs, showReceiverPhone, showSenderPhone, highlightVerificationCode, batteryReminderEnabled, lowBatteryReminderEnabled, highBatteryReminderEnabled, chargingReminderEnabled, batteryReminderChannelId, lowBatteryThreshold, highBatteryThreshold, customSim1Phone, customSim2Phone, startOnBoot)
                            qrCodeBitmap = QrCodeUtil.generateQrCode(jsonStr, 512)
                            showQrCodeDialog = true
                        },
                        onImportConfig = onImportConfig,
                        onImportFromGallery = {
                            (context as MainActivity).imagePickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    )
                    4 -> LogTab(
                        logs = logs,
                        onRefresh = { logs = LogStore.readAll(context) },
                        onClear = {
                            LogStore.clear(context)
                            logs = emptyList()
                            Toast.makeText(context, "日志已清除", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    // Edit Channel Dialog
    if (showChannelDialog && editingChannel != null) {
        ModernAlertDialog(
            onDismissRequest = { showChannelDialog = false; editingChannel = null },
            title = "编辑通道",
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editChannelName,
                        onValueChange = { editChannelName = it },
                        label = { Text("通道名称") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    var editTypeExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = editTypeExpanded,
                        onExpandedChange = { editTypeExpanded = !editTypeExpanded }
                    ) {
                        OutlinedTextField(
                            value = getChannelTypeLabel(editChannelType),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("通道类型") },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(editTypeExpanded) },
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = editTypeExpanded,
                            onDismissRequest = { editTypeExpanded = false }
                        ) {
                            ChannelType.entries.forEach { t ->
                                DropdownMenuItem(
                                    text = { Text(getChannelTypeLabel(t)) },
                                    onClick = {
                                        editChannelType = t
                                        editTypeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = editChannelTarget,
                        onValueChange = { editChannelTarget = it },
                        label = { Text("Webhook 地址") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val ch = editingChannel ?: return@Button
                        val updated = Channel(ch.id, editChannelName.trim(), editChannelType, editChannelTarget.trim())
                        channels = channels.map { if (it.id == ch.id) updated else it }
                        saveChannels(prefs, channels)
                        LogStore.append(context, "编辑通道: ${updated.name}")
                        showChannelDialog = false
                        editingChannel = null
                    },
                    shape = RoundedCornerShape(12.dp)
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showChannelDialog = false; editingChannel = null }) { Text("取消") }
            }
        )
    }

    // Edit Config Dialog
    if (showConfigDialog && editingConfig != null) {
        ModernAlertDialog(
            onDismissRequest = { showConfigDialog = false; editingConfig = null },
            title = "编辑关键词配置",
            content = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editConfigKeyword,
                        onValueChange = { editConfigKeyword = it },
                        label = { Text("关键词（留空表示全部）") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    var editCfgExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = editCfgExpanded,
                        onExpandedChange = { editCfgExpanded = !editCfgExpanded }
                    ) {
                        OutlinedTextField(
                            value = channels.find { it.id == editConfigChannelId }?.name ?: "选择通道",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("转发通道") },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(editCfgExpanded) },
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = editCfgExpanded,
                            onDismissRequest = { editCfgExpanded = false }
                        ) {
                            channels.forEach { ch ->
                                DropdownMenuItem(
                                    text = { Text(ch.name) },
                                    onClick = {
                                        editConfigChannelId = ch.id
                                        editCfgExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cfg = editingConfig ?: return@Button
                        if (editConfigChannelId.isBlank()) {
                            Toast.makeText(context, "请选择通道", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val updated = KeywordConfig(cfg.id, editConfigKeyword.trim(), editConfigChannelId)
                        configs = configs.map { if (it.id == cfg.id) updated else it }
                        saveConfigs(prefs, configs)
                        LogStore.append(context, "编辑关键词: ${updated.keyword} -> ${channels.find { it.id == updated.channelId }?.name}")
                        showConfigDialog = false
                        editingConfig = null
                    },
                    shape = RoundedCornerShape(12.dp)
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showConfigDialog = false; editingConfig = null }) { Text("取消") }
            }
        )
    }

    // Test Dialog
    if (showTestDialog) {
        TestRuleDialog(
            channels = channels,
            configs = configs,
            onDismiss = { showTestDialog = false }
        )
    }

    // 关于对话框
    if (showAboutDialog) {
        AboutDialog(
            onDismiss = { showAboutDialog = false }
        )
    }
    
    // 开机自启动提示对话框
    if (showBootTipDialog) {
        val ctx = LocalContext.current
        ModernAlertDialog(
            onDismissRequest = { showBootTipDialog = false },
            title = "重要提示",
            content = {
                Text(
                    text = "要让开机自启动正常工作，您还需要在系统设置中开启应用的自启动权限。\n\n通常在：设置 → 应用管理 → 短信转发助手 → 权限管理/自启动管理",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = { showBootTipDialog = false }) {
                    Text("我知道了")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showBootTipDialog = false
                    try {
                        val intent = Intent().apply {
                            action = "android.settings.APPLICATION_DETAILS_SETTINGS"
                            data = Uri.fromParts("package", ctx.packageName, null)
                        }
                        ctx.startActivity(intent)
                    } catch (_: Exception) {
                        Toast.makeText(ctx, "请手动打开系统设置", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("去设置")
                }
            }
        )
    }
    
    // 权限说明对话框
    if (showPermissionDialog) {
        val ctx = LocalContext.current
        PermissionExplanationDialog(
            onDismiss = { showPermissionDialog = false },
            onGoToSettings = {
                showPermissionDialog = false
                try {
                    val intent = Intent().apply {
                        action = "android.settings.APPLICATION_DETAILS_SETTINGS"
                        data = Uri.fromParts("package", ctx.packageName, null)
                    }
                    ctx.startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(ctx, "请手动打开系统设置", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
    
    // 隐私政策弹窗
    if (showPrivacyDialog) {
        val isPrivacyRequired = !prefs.getBoolean(Constants.PREF_PRIVACY_AGREED, false)
        PrivacyPolicyDialog(
            onAgree = {
                showPrivacyDialog = false
                if (isPrivacyRequired) {
                    prefs.edit().putBoolean(Constants.PREF_PRIVACY_AGREED, true).apply()
                }
            },
            onDisagree = {
                (context as Activity).finish()
            },
            isViewOnly = !isPrivacyRequired
        )
    }

    // 二维码导出弹窗
    if (showQrCodeDialog) {
        Dialog(onDismissRequest = { showQrCodeDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "配置二维码",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "截图保存，在另一台设备扫码导入",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .border(1.dp, Color.LightGray, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
                            qrCodeBitmap?.let { bitmap ->
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "配置二维码",
                                    modifier = Modifier.fillMaxSize().padding(12.dp)
                                )
                            } ?: run {
                                CircularProgressIndicator(modifier = Modifier.size(48.dp).align(Alignment.Center))
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Button(
                        onClick = { showQrCodeDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Text("关闭", fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionExplanationDialog(
    onDismiss: () -> Unit,
    onGoToSettings: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF667EEA).copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Security,
                        contentDescription = null,
                        tint = Color(0xFF667EEA),
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    "需要权限",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "短信转发助手需要以下权限才能正常工作：\n\n• 短信权限：用于接收并识别短信内容\n• 通知权限：用于显示服务运行状态和提醒\n\n请在系统设置中开启这些权限。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("稍后") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onGoToSettings,
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("去设置") }
                }
            }
        }
    }
}

@Composable
fun PrivacyPolicyDialog(
    onAgree: () -> Unit,
    onDisagree: () -> Unit,
    isViewOnly: Boolean = false
) {
    Dialog(onDismissRequest = { if (isViewOnly) onAgree() }) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            shadowElevation = 16.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxHeight()) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        "隐私政策",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "最后更新时间：2026年4月6日",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                
                PolicyScrollableColumn(modifier = Modifier.weight(1f)) {
                    PolicySection(title = "概述") {
                        Text(
                            "短信转发助手（以下简称\"我们\"）非常重视用户的隐私保护。本隐私政策说明了我们如何收集、使用、存储和保护您的个人信息。使用我们的应用即表示您同意本政策中描述的做法。",
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        PolicyBullet("应用名称：短信转发助手")
                        PolicyBullet("开发者：华昊科技有限公司")
                        PolicyBullet("联系邮箱：support@smsforwarder.cn")
                        PolicyBullet("官方网站：https://smsforwarder.cn/")
                        PolicyBullet("备案号：鲁ICP备2026018166号-2A")
                    }
                    
                    PolicySection(title = "核心原则") {
                        PolicyBullet("不上云：所有数据都在您的手机本地处理，不会上传到我们的服务器")
                        PolicyBullet("不收集：不会收集您的个人信息、短信内容等敏感数据")
                        PolicyBullet("不追踪：不集成任何统计、分析或广告 SDK")
                        PolicyBullet("完全可控：所有权限和数据都由您自己掌控")
                    }
                    
                    PolicySection(title = "信息收集与使用") {
                        Text(
                            "*短信内容（敏感信息）",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Text("用途：仅在您的手机本地用于匹配关键词规则和执行转发", style = MaterialTheme.typography.bodyMedium, lineHeight = 20.sp)
                        Text("存储：不会保存到任何服务器，仅在转发时临时处理", style = MaterialTheme.typography.bodyMedium, lineHeight = 20.sp)
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                .padding(16.dp)
                                .clip(RoundedCornerShape(12.dp))
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    Icons.Filled.Warning,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "重要：唯一会发送短信内容的情况是您主动配置了 Webhook 转发目标（如企业微信、钉钉、飞书或自定义 Webhook），应用会将短信直接发送到您指定的目标，不会经过我们的服务器。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            "配置信息",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Text("您设置的转发通道、关键词规则等配置信息保存在您手机的本地存储中，不会上传。", style = MaterialTheme.typography.bodyMedium, lineHeight = 20.sp)
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            "转发日志",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Text("应用会在本地记录转发历史（最多200条），方便您查看和调试，这些日志仅存储在您的手机上。", style = MaterialTheme.typography.bodyMedium, lineHeight = 20.sp)
                    }
                    
                    PolicySection(title = "权限说明") {
                        PolicyBullet("接收短信权限：监听设备收到的短信，用于执行转发功能")
                        PolicyBullet("通知权限：显示前台服务通知，让您知道服务正在运行")
                        PolicyBullet("读取手机状态权限：用于识别双卡手机的 SIM 卡信息和获取本机号码（可选）")
                        PolicyBullet("网络权限：仅用于转发到您配置的 Webhook")
                        PolicyBullet("前台服务权限：保持应用在后台稳定运行")
                        PolicyBullet("开机自启权限：让应用在开机后自动启动转发服务（可选）")
                        PolicyBullet("忽略电池优化权限：防止系统杀死后台服务（可选）")
                        PolicyBullet("访问网络状态权限：检测网络连接状态")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("所有权限都需要您主动授权，您可以随时在系统设置中撤销。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    
                    PolicySection(title = "数据存储") {
                        Text(
                            "本地存储",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Text("所有数据都存储在您手机的私有目录中，包括：", style = MaterialTheme.typography.bodyMedium, lineHeight = 20.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        PolicyBullet("转发通道和关键词配置")
                        PolicyBullet("转发历史日志")
                        PolicyBullet("应用设置")
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            "服务器存储",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF22C55E).copy(alpha = 0.08f))
                                .padding(16.dp)
                                .clip(RoundedCornerShape(12.dp))
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = Color(0xFF22C55E)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "我们没有服务器存储您的数据！应用是纯本地运行的工具，我们不收集、不存储、不上传任何用户数据。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                    
                    PolicySection(title = "您的权利") {
                        PolicyBullet("查看数据：可以在应用内查看所有转发日志")
                        PolicyBullet("删除数据：可以在应用内清空日志，或卸载应用删除所有数据")
                        PolicyBullet("控制权限：可以在系统设置中随时授予或撤销权限")
                        PolicyBullet("撤回同意：可以在应用设置中撤回隐私政策同意")
                    }
                    
                    PolicySection(title = "第三方服务") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                .padding(16.dp)
                                .clip(RoundedCornerShape(12.dp))
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(
                                    Icons.Filled.Link,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    "关于转发目标：如果您配置了 Webhook 或其他第三方服务作为转发目标，短信内容会发送到该第三方。请您谨慎选择转发目标，并确保了解其隐私政策。我们不对第三方的数据处理负责。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            "第三方 SDK",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Text("当前版本未集成任何第三方 SDK（包括统计、广告、崩溃分析等）。", style = MaterialTheme.typography.bodyMedium, lineHeight = 20.sp)
                    }
                    
                    PolicySection(title = "政策更新") {
                        Text("我们可能会不时更新本隐私政策。重大变更时，我们会通过应用内通知或其他方式告知您。建议您定期查看本政策以了解最新信息。", style = MaterialTheme.typography.bodyMedium, lineHeight = 20.sp)
                    }
                    
                    PolicySection(title = "联系我们") {
                        Text("如果您对本隐私政策有任何疑问或建议，请通过以下方式联系我们：", style = MaterialTheme.typography.bodyMedium, lineHeight = 20.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Mail, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("support@smsforwarder.cn", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                
                Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
                
                Column(modifier = Modifier.padding(16.dp)) {
                    if (isViewOnly) {
                        Button(
                            onClick = onAgree,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 14.dp)
                        ) { Text("关闭", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = onAgree,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF667EEA),
                                    contentColor = Color.White
                                )
                            ) { Text("同意", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                            OutlinedButton(
                                onClick = onDisagree,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 14.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                            ) { Text("不同意", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PolicyScrollableColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp)
    ) {
        content()
    }
}

@Composable
private fun PolicySection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(bottom = 24.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        content()
    }
}

@Composable
private fun PolicySubtitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 14.dp, bottom = 8.dp)
    )
}

@Composable
private fun PolicyBullet(text: String) {
    Row(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(
            "• ",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 22.sp
        )
    }
}

@Composable
fun ModernAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(20.dp))
                content()
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    dismissButton()
                    Spacer(modifier = Modifier.width(8.dp))
                    confirmButton()
                }
            }
        }
    }
}



@Composable
fun LogTab(
    logs: List<String>,
    onRefresh: () -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        ModernCard(
            modifier = Modifier.weight(1f)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF59E0B).copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "转发日志",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${logs.size} 条记录",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row {
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "刷新")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = onClear,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFEE4444).copy(alpha = 0.1f))
                        ) {
                            Icon(Icons.Filled.ClearAll, contentDescription = "清除", tint = Color(0xFFEE4444))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (logs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Outlined.Description,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "暂无日志",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(logs) { line ->
                            LogItem(line = line)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModernCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column {
            content()
        }
    }
}

@Composable
fun PermissionItem(
    icon: ImageVector,
    title: String,
    granted: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (granted) Color(0xFF10B981).copy(alpha = 0.1f) else Color(0xFFEE4444).copy(alpha = 0.1f))
            .padding(12.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (granted) Color(0xFF10B981) else Color(0xFFEE4444)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                if (granted) "已授权" else "未授权",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (granted) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "已授权",
                tint = Color(0xFF10B981)
            )
        } else {
            TextButton(
                onClick = onClick,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("去设置")
                Icon(Icons.Filled.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun ConfigItem(
    keyword: String,
    channelName: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Rule,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (keyword.isBlank()) "全部消息" else keyword,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "→ $channelName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Filled.Edit, contentDescription = "编辑")
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "删除", tint = Color(0xFFEE4444))
            }
        }
    }
}

@Composable
fun ChannelItem(
    name: String,
    type: String,
    target: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val channelColor = when (type) {
                "企业微信" -> Color(0xFF07C160)
                "钉钉" -> Color(0xFF2080F0)
                "飞书" -> Color(0xFF2064E5)
                else -> Color(0xFF667EEA)
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(channelColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Cloud,
                    contentDescription = null,
                    tint = channelColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "$type → ${target.take(30)}${if (target.length > 30) "..." else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Filled.Edit, contentDescription = "编辑")
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "删除", tint = Color(0xFFEE4444))
            }
        }
    }
}

@Composable
fun LogItem(line: String) {
    val tsRegex = """^\[(.*?)\]\s*(.*)$""".toRegex()
    val match = tsRegex.find(line)
    val time = match?.groups?.get(1)?.value ?: ""
    val msg = match?.groups?.get(2)?.value ?: line
    val isSuccess = msg.contains("成功") || msg.contains("已启动")
    val isError = msg.contains("失败") || msg.contains("异常") || msg.contains("错误")
    val iconTint = when {
        isSuccess -> Color(0xFF10B981)
        isError -> Color(0xFFEE4444)
        else -> MaterialTheme.colorScheme.primary
    }
    val bgColor = when {
        isSuccess -> Color(0xFF10B981).copy(alpha = 0.05f)
        isError -> Color(0xFFEE4444).copy(alpha = 0.05f)
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(iconTint, shape = CircleShape)
                .padding(top = 6.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                msg,
                style = MaterialTheme.typography.bodyMedium
            )
            if (time.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    time,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun TestRuleDialog(
    channels: List<Channel>,
    configs: List<KeywordConfig>,
    onDismiss: () -> Unit
) {
    var testContent by remember { mutableStateOf("") }
    var testResults by remember { mutableStateOf<List<Pair<Channel, KeywordConfig>>>(emptyList()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(Color(0xFFF59E0B), Color(0xFFD97706))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Science,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            "测试转发规则",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "输入短信内容测试匹配",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = testContent,
                    onValueChange = {
                        testContent = it
                        testResults = if (it.isNotBlank()) {
                            configs.mapNotNull { cfg ->
                                val kw = cfg.keyword.trim()
                                val match = if (kw.isEmpty()) true else it.contains(kw, ignoreCase = true)
                                if (match) {
                                    channels.find { ch -> ch.id == cfg.channelId }?.let { ch ->
                                        ch to cfg
                                    }
                                } else null
                            }
                        } else emptyList()
                    },
                    label = { Text("输入测试短信内容") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    "匹配结果",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (testResults.isEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Outlined.Rule,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    if (testContent.isBlank()) "请输入内容开始测试" else "没有匹配的规则",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        testResults.forEach { (ch, cfg) ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFF10B981).copy(alpha = 0.1f)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "关键词: ${if (cfg.keyword.isBlank()) "全部" else cfg.keyword}",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            "→ ${ch.name} (${getChannelTypeLabel(ch.type)})",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) { Text("关闭", fontSize = 16.sp) }
            }
        }
    }
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val packageName = context.packageName
    val versionName = try {
        packageManager.getPackageInfo(packageName, 0).versionName
    } catch (e: Exception) {
        "未知版本"
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF667EEA), Color(0xFF764BA2))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Message,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    "短信转发助手",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "版本 $versionName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    "轻量、稳定、开源的 Android 短信转发应用。支持企业微信、钉钉、飞书和自定义 Webhook 等多种转发渠道，支持关键词过滤、验证码提取、本机号码识别等功能。",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "官方网站：https://smsforwarder.cn/",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://smsforwarder.cn/"))
                        context.startActivity(intent)
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "备案号：鲁ICP备2026018166号-2A",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "© 2026 华昊科技有限公司",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 14.dp)
                ) { Text("关闭", fontSize = 16.sp) }
            }
        }
    }
}

@Composable
fun SimCardInfoCard(permissionUpdateTrigger: Int) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE) }
    
    var customSim1Phone by remember { mutableStateOf(prefs.getString(Constants.PREF_CUSTOM_SIM1_PHONE, null)) }
    var customSim2Phone by remember { mutableStateOf(prefs.getString(Constants.PREF_CUSTOM_SIM2_PHONE, null)) }
    
    val hasPhonePermission by remember(permissionUpdateTrigger) {
        derivedStateOf {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    val simCards = remember(customSim1Phone, customSim2Phone, permissionUpdateTrigger) { 
        getSimCardInfo(context, customSim1Phone, customSim2Phone) 
    }
    
    var showEditDialog by remember { mutableStateOf<Int?>(null) }
    var editPhoneNumber by remember { mutableStateOf("") }

    ModernCard {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF3B82F6).copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Phone,
                        contentDescription = null,
                        tint = Color(0xFF3B82F6)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "SIM 卡信息",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "点击编辑按钮手动输入本机号码",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            // 电话权限提示
            if (!hasPhonePermission) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            try {
                                val intent = Intent().apply {
                                    action = "android.settings.APPLICATION_DETAILS_SETTINGS"
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "请手动打开系统设置", Toast.LENGTH_SHORT).show()
                            }
                        },
                    color = Color(0xFFFEF2F2).copy(alpha = 0.9f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = Color(0xFFDC2626)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "需要电话权限才能自动获取本机号码",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF991B1B)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "点击前往设置开启权限，或手动输入号码",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFB91C1C)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = Color(0xFFDC2626)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            // 提示信息
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFFFF3CD).copy(alpha = 0.8f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "小米/澎湃OS等定制ROM通常无法自动获取本机号码，请手动输入",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF92400E)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (simCards.isEmpty()) {
                Text(
                    "无法读取 SIM 卡信息",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                simCards.forEachIndexed { index, simInfo ->
                    SimCardItem(
                        slot = index + 1,
                        phoneNumber = simInfo.phoneNumber,
                        carrierName = simInfo.carrierName,
                        isCustom = simInfo.isCustom,
                        onEdit = {
                            showEditDialog = index + 1
                            editPhoneNumber = simInfo.phoneNumber ?: ""
                        },
                        onClear = {
                            if (index + 1 == 1) {
                                customSim1Phone = null
                                prefs.edit().remove(Constants.PREF_CUSTOM_SIM1_PHONE).apply()
                            } else {
                                customSim2Phone = null
                                prefs.edit().remove(Constants.PREF_CUSTOM_SIM2_PHONE).apply()
                            }
                        }
                    )
                    if (index < simCards.size - 1) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }

    // 编辑 SIM 号码对话框
    showEditDialog?.let { slot ->
        EditSimPhoneDialog(
            slot = slot,
            currentPhoneNumber = editPhoneNumber,
            onDismiss = { showEditDialog = null },
            onSave = { newPhoneNumber ->
                if (slot == 1) {
                    customSim1Phone = newPhoneNumber.takeIf { it.isNotBlank() }
                    if (newPhoneNumber.isNotBlank()) {
                        prefs.edit().putString(Constants.PREF_CUSTOM_SIM1_PHONE, newPhoneNumber).apply()
                    } else {
                        prefs.edit().remove(Constants.PREF_CUSTOM_SIM1_PHONE).apply()
                    }
                } else {
                    customSim2Phone = newPhoneNumber.takeIf { it.isNotBlank() }
                    if (newPhoneNumber.isNotBlank()) {
                        prefs.edit().putString(Constants.PREF_CUSTOM_SIM2_PHONE, newPhoneNumber).apply()
                    } else {
                        prefs.edit().remove(Constants.PREF_CUSTOM_SIM2_PHONE).apply()
                    }
                }
                showEditDialog = null
            }
        )
    }
}

private data class SimCardInfo(
    val phoneNumber: String?,
    val carrierName: String?,
    val isCustom: Boolean = false
)

private fun getSimCardInfo(context: Context, customSim1Phone: String? = null, customSim2Phone: String? = null): List<SimCardInfo> {
    val simCards = mutableListOf<SimCardInfo>()
    try {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            // 即使没有权限，也显示自定义号码
            if (customSim1Phone != null) {
                simCards.add(SimCardInfo(phoneNumber = customSim1Phone, carrierName = null, isCustom = true))
            }
            if (customSim2Phone != null) {
                simCards.add(SimCardInfo(phoneNumber = customSim2Phone, carrierName = null, isCustom = true))
            }
            return simCards
        }

        val subscriptionManager = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                SubscriptionManager.from(context)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("SimCardInfo", "获取 SubscriptionManager 失败", e)
            null
        }

        var addedSim1 = false
        var addedSim2 = false
        
        if (subscriptionManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            try {
                val activeSubscriptions = subscriptionManager.activeSubscriptionInfoList
                activeSubscriptions?.forEachIndexed { index, subInfo ->
                    try {
                        val slotIndex = index + 1
                        val customPhone = if (slotIndex == 1) customSim1Phone else if (slotIndex == 2) customSim2Phone else null
                        val phoneNumber = customPhone ?: subInfo?.number?.takeIf { it.isNotBlank() }
                        simCards.add(
                            SimCardInfo(
                                phoneNumber = phoneNumber,
                                carrierName = subInfo?.carrierName?.toString(),
                                isCustom = customPhone != null
                            )
                        )
                        if (slotIndex == 1) addedSim1 = true
                        if (slotIndex == 2) addedSim2 = true
                    } catch (e: Exception) {
                        Log.e("SimCardInfo", "处理 subscriptionInfo 失败", e)
                    }
                }
            } catch (e: Exception) {
                Log.e("SimCardInfo", "获取 activeSubscriptions 失败", e)
            }
        }

        // 如果 SIM1 没有添加但有自定义号码，添加
        if (!addedSim1 && customSim1Phone != null) {
            simCards.add(SimCardInfo(phoneNumber = customSim1Phone, carrierName = null, isCustom = true))
            addedSim1 = true
        }
        // 如果 SIM2 没有添加但有自定义号码，添加
        if (!addedSim2 && customSim2Phone != null) {
            simCards.add(SimCardInfo(phoneNumber = customSim2Phone, carrierName = null, isCustom = true))
            addedSim2 = true
        }

        // 回退方案：如果没有获取到任何 SIM 信息但有自定义号码
        if (simCards.isEmpty()) {
            if (customSim1Phone != null) {
                simCards.add(SimCardInfo(phoneNumber = customSim1Phone, carrierName = null, isCustom = true))
            }
            if (customSim2Phone != null) {
                simCards.add(SimCardInfo(phoneNumber = customSim2Phone, carrierName = null, isCustom = true))
            }
            
            // 如果没有自定义号码，尝试获取默认号码
            if (simCards.isEmpty()) {
                try {
                    val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
                    if (telephonyManager != null) {
                        val phoneNumber = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            telephonyManager.line1Number
                        } else {
                            @Suppress("DEPRECATION")
                            telephonyManager.line1Number
                        }
                        val carrierName = try {
                            telephonyManager.networkOperatorName
                        } catch (e: Exception) {
                            null
                        }
                        if (!phoneNumber.isNullOrBlank()) {
                            simCards.add(
                                SimCardInfo(
                                    phoneNumber = phoneNumber,
                                    carrierName = carrierName,
                                    isCustom = false
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SimCardInfo", "获取默认号码失败", e)
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        // 即使出错，也显示自定义号码（避免重复添加）
        val addedSim1InCatch = simCards.any { it.isCustom }
        if (!addedSim1InCatch && customSim1Phone != null) {
            simCards.add(SimCardInfo(phoneNumber = customSim1Phone, carrierName = null, isCustom = true))
        }
        val addedSim2InCatch = simCards.size > 1
        if (!addedSim2InCatch && customSim2Phone != null) {
            simCards.add(SimCardInfo(phoneNumber = customSim2Phone, carrierName = null, isCustom = true))
        }
    }
    return simCards
}

@Composable
fun SimCardItem(
    slot: Int, 
    phoneNumber: String?, 
    carrierName: String?,
    isCustom: Boolean = false,
    onEdit: () -> Unit = {},
    onClear: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isCustom) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFF3B82F6).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "SIM$slot",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isCustom) Color(0xFF10B981) else Color(0xFF3B82F6)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        phoneNumber ?: "无法获取号码",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = if (phoneNumber != null) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    if (isCustom) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = Color(0xFF10B981).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "自定义",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF10B981),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                carrierName?.takeIf { it.isNotBlank() }?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "编辑",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                if (isCustom) {
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "清除",
                            tint = Color(0xFFEE4444)
                        )
                    }
                } else if (phoneNumber != null) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF10B981)
                    )
                } else {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B)
                    )
                }
            }
        }
    }
}

@Composable
fun EditSimPhoneDialog(
    slot: Int,
    currentPhoneNumber: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var phoneNumber by remember { mutableStateOf(currentPhoneNumber) }
    
    ModernAlertDialog(
        onDismissRequest = onDismiss,
        title = "设置 SIM$slot 号码",
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "如果无法自动获取本机号码，可以手动输入",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("本机号码") },
                    placeholder = { Text("例如：13800138000") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Outlined.Phone, contentDescription = null)
                    },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(phoneNumber) },
                shape = RoundedCornerShape(12.dp)
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

// ========================================
// 新的标签页组件
// ========================================

@Composable
fun HomeTab(
    isEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    startOnBoot: Boolean,
    onStartOnBootChange: (Boolean) -> Unit,
    smsGranted: Boolean,
    notifGranted: Boolean,
    isIgnoringBatteryOptimizations: Boolean,
    onRequestSmsPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onRequestBatteryOptimization: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // Service Status Card
        item {
            ModernCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "转发服务",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val statusColor = if (isEnabled) Color(0xFF10B981) else Color(0xFF9CA3AF)
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(statusColor, shape = CircleShape)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (isEnabled) "服务运行中" else "服务已停止",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        val switchScale by animateFloatAsState(
                            targetValue = if (isEnabled) 1.1f else 1f,
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                            label = "switchAnimation"
                        )
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = onEnabledChange,
                            modifier = Modifier.scale(switchScale),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF667EEA)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Permission items
                    PermissionItem(
                        icon = Icons.Outlined.Notifications,
                        title = "通知权限",
                        granted = notifGranted,
                        onClick = onRequestNotificationPermission
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PermissionItem(
                        icon = Icons.Outlined.Sms,
                        title = "短信权限",
                        granted = smsGranted,
                        onClick = onRequestSmsPermission
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PermissionItem(
                        icon = Icons.Outlined.BatteryFull,
                        title = "电池优化白名单",
                        granted = isIgnoringBatteryOptimizations,
                        onClick = onRequestBatteryOptimization
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    HorizontalDivider()

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Outlined.PowerSettingsNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "开机自启动",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "设备启动后自动运行",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = startOnBoot,
                            onCheckedChange = onStartOnBootChange
                        )
                    }
                }
            }
        }

        // Security Warning
        item {
            ModernCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(20.dp)
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "请确保转发通道是您信任的来源，避免验证码泄露",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun KeywordTab(
    channels: List<Channel>,
    configs: List<KeywordConfig>,
    newKeywordInput: String,
    onNewKeywordInputChange: (String) -> Unit,
    selectedChannelIdForNewCfg: String,
    onSelectedChannelIdForNewCfgChange: (String) -> Unit,
    configChannelDropdownExpanded: Boolean,
    onConfigChannelDropdownExpandedChange: (Boolean) -> Unit,
    onAddConfig: () -> Unit,
    onDeleteConfig: (KeywordConfig) -> Unit,
    onEditConfig: (KeywordConfig) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            ModernCard {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF10B981).copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Rule,
                                contentDescription = null,
                                tint = Color(0xFF10B981)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "关键词配置",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "设置转发关键词",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedTextField(
                        value = newKeywordInput,
                        onValueChange = onNewKeywordInputChange,
                        label = { Text("输入关键词（留空表示全部）") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(Icons.Outlined.Search, contentDescription = null)
                        },
                        shape = RoundedCornerShape(12.dp)
                    )

                    ExposedDropdownMenuBox(
                        expanded = configChannelDropdownExpanded,
                        onExpandedChange = onConfigChannelDropdownExpandedChange
                    ) {
                        OutlinedTextField(
                            value = channels.find { it.id == selectedChannelIdForNewCfg }?.name ?: "选择转发通道",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("转发通道") },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            leadingIcon = {
                                Icon(Icons.Outlined.Send, contentDescription = null)
                            },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(configChannelDropdownExpanded) },
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = configChannelDropdownExpanded,
                            onDismissRequest = { onConfigChannelDropdownExpandedChange(false) }
                        ) {
                            channels.forEach { ch ->
                                DropdownMenuItem(
                                    text = { Text(ch.name) },
                                    onClick = {
                                        onSelectedChannelIdForNewCfgChange(ch.id)
                                        onConfigChannelDropdownExpandedChange(false)
                                    }
                                )
                            }
                            if (channels.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("请先添加通道") },
                                    onClick = { onConfigChannelDropdownExpandedChange(false) }
                                )
                            }
                        }
                    }

                    Button(
                        onClick = onAddConfig,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("添加配置", fontSize = 16.sp)
                    }

                    // Config list
                    if (configs.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        configs.forEach { cfg ->
                            val chName = channels.find { it.id == cfg.channelId }?.name ?: "(已删除通道)"
                            ConfigItem(
                                keyword = cfg.keyword,
                                channelName = chName,
                                onEdit = { onEditConfig(cfg) },
                                onDelete = { onDeleteConfig(cfg) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelTab(
    channels: List<Channel>,
    newChannelName: String,
    onNewChannelNameChange: (String) -> Unit,
    newChannelTarget: String,
    onNewChannelTargetChange: (String) -> Unit,
    newChannelType: ChannelType,
    onNewChannelTypeChange: (ChannelType) -> Unit,
    channelTypeExpanded: Boolean,
    onChannelTypeExpandedChange: (Boolean) -> Unit,
    onAddChannel: () -> Unit,
    onDeleteChannel: (Channel) -> Unit,
    onEditChannel: (Channel) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            ModernCard {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF667EEA).copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Cloud,
                                contentDescription = null,
                                tint = Color(0xFF667EEA)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "转发通道管理",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "添加和管理转发通道",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedTextField(
                        value = newChannelName,
                        onValueChange = onNewChannelNameChange,
                        label = { Text("通道名称") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(Icons.Outlined.Label, contentDescription = null)
                        },
                        shape = RoundedCornerShape(12.dp)
                    )

                    ExposedDropdownMenuBox(
                        expanded = channelTypeExpanded,
                        onExpandedChange = onChannelTypeExpandedChange
                    ) {
                        OutlinedTextField(
                            value = getChannelTypeLabel(newChannelType),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("通道类型") },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            leadingIcon = {
                                Icon(Icons.Outlined.Category, contentDescription = null)
                            },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(channelTypeExpanded) },
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = channelTypeExpanded,
                            onDismissRequest = { onChannelTypeExpandedChange(false) }
                        ) {
                            ChannelType.entries.forEach { t ->
                                DropdownMenuItem(
                                    text = { Text(getChannelTypeLabel(t)) },
                                    onClick = {
                                        onNewChannelTypeChange(t)
                                        onChannelTypeExpandedChange(false)
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = newChannelTarget,
                        onValueChange = onNewChannelTargetChange,
                        label = { Text("Webhook 地址") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(Icons.Outlined.Link, contentDescription = null)
                        },
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Security warning
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFEF3C7).copy(alpha = 0.6f)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "请确保 Webhook 地址是您信任的来源，切勿使用陌生人提供的地址，以免验证码被窃取",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF92400E)
                            )
                        }
                    }

                    Button(
                        onClick = onAddChannel,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("添加通道", fontSize = 16.sp)
                    }

                    // Channel list
                    if (channels.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        channels.forEach { ch ->
                            ChannelItem(
                                name = ch.name,
                                type = getChannelTypeLabel(ch.type),
                                target = ch.target,
                                onEdit = { onEditChannel(ch) },
                                onDelete = { onDeleteChannel(ch) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsTab(
    showReceiverPhone: Boolean,
    onShowReceiverPhoneChange: (Boolean) -> Unit,
    showSenderPhone: Boolean,
    onShowSenderPhoneChange: (Boolean) -> Unit,
    highlightVerificationCode: Boolean,
    onHighlightVerificationCodeChange: (Boolean) -> Unit,
    batteryReminderEnabled: Boolean,
    onBatteryReminderEnabledChange: (Boolean) -> Unit,
    lowBatteryReminderEnabled: Boolean,
    onLowBatteryReminderEnabledChange: (Boolean) -> Unit,
    highBatteryReminderEnabled: Boolean,
    onHighBatteryReminderEnabledChange: (Boolean) -> Unit,
    chargingReminderEnabled: Boolean,
    onChargingReminderEnabledChange: (Boolean) -> Unit,
    batteryReminderChannelId: String?,
    onBatteryReminderChannelIdChange: (String?) -> Unit,
    channels: List<Channel>,
    lowBatteryThreshold: Int,
    onLowBatteryThresholdChange: (Int) -> Unit,
    highBatteryThreshold: Int,
    onHighBatteryThresholdChange: (Int) -> Unit,
    onShowTestDialog: () -> Unit,
    onRevokePrivacyConsent: () -> Unit,
    onShowPrivacyPolicy: () -> Unit,
    permissionUpdateTrigger: Int,
    startOnBoot: Boolean,
    onExportConfig: () -> Unit,
    onImportConfig: () -> Unit,
    onImportFromGallery: () -> Unit
) {
    var showChannelSelectionDialog by remember { mutableStateOf(false) }
    var showImportOptionsDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // SIM 卡信息卡片
        item {
            SimCardInfoCard(permissionUpdateTrigger)
        }

        // 电量提醒配置
        item {
            ModernCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "电量提醒",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 电量提醒开关
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Outlined.BatteryAlert,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "开启电量提醒",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "电量变化时发送通知提醒",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = batteryReminderEnabled,
                            onCheckedChange = onBatteryReminderEnabledChange
                        )
                    }

                    if (batteryReminderEnabled) {
                        Spacer(modifier = Modifier.height(16.dp))

                        // 低电量提醒开关
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Outlined.BatteryAlert,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "低电量提醒",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "电量低于阈值时提醒",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = lowBatteryReminderEnabled,
                                onCheckedChange = onLowBatteryReminderEnabledChange
                            )
                        }

                        if (lowBatteryReminderEnabled) {
                            Spacer(modifier = Modifier.height(12.dp))
                            // 低电量阈值
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "低电量提醒阈值",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "$lowBatteryThreshold%",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Slider(
                                        value = lowBatteryThreshold.toFloat(),
                                        onValueChange = { onLowBatteryThresholdChange(it.toInt()) },
                                        valueRange = 5f..50f,
                                        steps = 8,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Text(
                                    "当电量低于此阈值时提醒",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 高电量提醒开关
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Outlined.BatteryFull,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "高电量提醒",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "电量高于阈值时提醒",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = highBatteryReminderEnabled,
                                onCheckedChange = onHighBatteryReminderEnabledChange
                            )
                        }

                        if (highBatteryReminderEnabled) {
                            Spacer(modifier = Modifier.height(12.dp))
                            // 高电量阈值
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "高电量提醒阈值",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "$highBatteryThreshold%",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Slider(
                                        value = highBatteryThreshold.toFloat(),
                                        onValueChange = { onHighBatteryThresholdChange(it.toInt()) },
                                        valueRange = 50f..100f,
                                        steps = 8,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Text(
                                    "当电量高于此阈值时提醒",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 充电提醒开关
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Filled.Power,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "充电提醒",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "设备开始或结束充电时发送提醒",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = chargingReminderEnabled,
                            onCheckedChange = onChargingReminderEnabledChange
                        )
                    }

                    // 电池提醒通道选择
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "提醒通道",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "选择发送电量提醒的通道，不选择则发送到所有通道",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    showChannelSelectionDialog = true
                                }
                                .padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Send,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = if (batteryReminderChannelId.isNullOrEmpty()) {
                                        "所有通道"
                                    } else {
                                        channels.find { it.id == batteryReminderChannelId }?.name ?: "未知通道"
                                    },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(
                                    Icons.Filled.ArrowDropDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // 消息格式配置
        item {
            ModernCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "消息格式",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 显示本机号码
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Outlined.Phone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "显示本机号码",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "转发时显示接收短信的本机号码",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = showReceiverPhone,
                            onCheckedChange = onShowReceiverPhoneChange
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 显示发送者号码
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Outlined.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "显示发送者号码",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "转发时显示短信发送者号码",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = showSenderPhone,
                            onCheckedChange = onShowSenderPhoneChange
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 突出显示验证码
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Outlined.VpnKey,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "突出显示验证码",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "自动识别并突出显示短信验证码",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = highlightVerificationCode,
                            onCheckedChange = onHighlightVerificationCodeChange
                        )
                    }
                }
            }
        }

        // 工具
        item {
            ModernCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "工具",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onExportConfig,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        Icon(Icons.Filled.QrCode, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("导出配置", fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { showImportOptionsDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("导入配置", fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onShowTestDialog,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        Icon(Icons.Outlined.Science, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("测试规则", fontSize = 16.sp)
                    }
                }
            }
        }

        // 权限管理
        item {
            ModernCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "权限管理",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val ctx = LocalContext.current

                    val smsGranted by remember(permissionUpdateTrigger) {
                        derivedStateOf {
                            ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
                        }
                    }
                    val notifGranted by remember(permissionUpdateTrigger) {
                        derivedStateOf {
                            NotificationManagerCompat.from(ctx).areNotificationsEnabled()
                        }
                    }
                    val phoneGranted by remember(permissionUpdateTrigger) {
                        derivedStateOf {
                            ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
                        }
                    }
                    val batteryGranted by remember(permissionUpdateTrigger) {
                        derivedStateOf {
                            (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)
                        }
                    }

                    PermissionManagementItem(
                        icon = Icons.Outlined.Message,
                        title = "短信权限",
                        description = "用于接收并识别短信内容",
                        granted = smsGranted,
                        onClick = {
                            try {
                                val intent = Intent().apply {
                                    action = "android.settings.APPLICATION_DETAILS_SETTINGS"
                                    data = Uri.fromParts("package", ctx.packageName, null)
                                }
                                ctx.startActivity(intent)
                            } catch (_: Exception) {
                                Toast.makeText(ctx, "请手动打开系统设置", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    PermissionManagementItem(
                        icon = Icons.Outlined.Notifications,
                        title = "通知权限",
                        description = "用于显示服务运行状态和提醒",
                        granted = notifGranted,
                        onClick = {
                            try {
                                val intent = Intent().apply {
                                    action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        Settings.ACTION_APP_NOTIFICATION_SETTINGS
                                    } else {
                                        "android.settings.APPLICATION_DETAILS_SETTINGS"
                                    }
                                    data = Uri.fromParts("package", ctx.packageName, null)
                                }
                                ctx.startActivity(intent)
                            } catch (_: Exception) {
                                Toast.makeText(ctx, "请手动打开系统设置", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    PermissionManagementItem(
                        icon = Icons.Outlined.Phone,
                        title = "读取手机状态",
                        description = "用于识别双卡手机的SIM卡信息（可选）",
                        granted = phoneGranted,
                        onClick = {
                            try {
                                val intent = Intent().apply {
                                    action = "android.settings.APPLICATION_DETAILS_SETTINGS"
                                    data = Uri.fromParts("package", ctx.packageName, null)
                                }
                                ctx.startActivity(intent)
                            } catch (_: Exception) {
                                Toast.makeText(ctx, "请手动打开系统设置", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    PermissionManagementItem(
                        icon = Icons.Outlined.BatteryFull,
                        title = "忽略电池优化",
                        description = "防止系统杀死后台服务（可选）",
                        granted = batteryGranted,
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${ctx.packageName}")
                                }
                                ctx.startActivity(intent)
                            } catch (_: Exception) {
                                Toast.makeText(ctx, "请手动打开系统设置", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }

        // 隐私设置
        item {
            ModernCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "隐私设置",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onShowPrivacyPolicy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 14.dp)
                    ) {
                        Icon(Icons.Outlined.FilePresent, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("隐私政策", fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = onRevokePrivacyConsent,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFEE4444)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFEE4444).copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Outlined.Shield, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("撤销隐私政策同意", fontSize = 16.sp)
                    }
                }
            }
        }
    }

    // 通道选择弹窗
    if (showChannelSelectionDialog) {
        AlertDialog(
            onDismissRequest = { showChannelSelectionDialog = false },
            title = { Text("选择提醒通道") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "选择发送电量提醒的通道，选择\"所有通道\"则发送到全部已配置通道",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    LazyColumn(modifier = Modifier.height(200.dp)) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onBatteryReminderChannelIdChange(null)
                                        showChannelSelectionDialog = false
                                    }
                                    .padding(vertical = 12.dp)
                            ) {
                                RadioButton(
                                    selected = batteryReminderChannelId.isNullOrEmpty(),
                                    onClick = {
                                        onBatteryReminderChannelIdChange(null)
                                        showChannelSelectionDialog = false
                                    }
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("所有通道")
                            }
                        }
                        channels.forEach { channel ->
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onBatteryReminderChannelIdChange(channel.id)
                                            showChannelSelectionDialog = false
                                        }
                                        .padding(vertical = 12.dp)
                                ) {
                                    RadioButton(
                                        selected = batteryReminderChannelId == channel.id,
                                        onClick = {
                                            onBatteryReminderChannelIdChange(channel.id)
                                            showChannelSelectionDialog = false
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(channel.name)
                                        Text(
                                            channel.type.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showChannelSelectionDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showImportOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showImportOptionsDialog = false },
            title = { Text("选择导入方式") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = {
                            showImportOptionsDialog = false
                            onImportConfig()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 16.dp, horizontal = 24.dp)
                    ) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("扫码导入", fontSize = 16.sp)
                    }
                    TextButton(
                        onClick = {
                            showImportOptionsDialog = false
                            onImportFromGallery()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 16.dp, horizontal = 24.dp)
                    ) {
                        Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("从相册选取", fontSize = 16.sp)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showImportOptionsDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun PermissionManagementItem(
    icon: ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (granted) Color(0xFF22C55E).copy(alpha = 0.1f)
                    else Color(0xFFF59E0B).copy(alpha = 0.1f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (granted) Color(0xFF22C55E) else Color(0xFFF59E0B),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (granted) "已开启" else "去设置",
                style = MaterialTheme.typography.bodyMedium,
                color = if (granted) Color(0xFF22C55E) else MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

fun getChannelTypeLabel(type: ChannelType): String = when (type) {
    ChannelType.WECHAT -> "企业微信"
    ChannelType.DINGTALK -> "钉钉"
    ChannelType.FEISHU -> "飞书"
    ChannelType.GENERIC_WEBHOOK -> "Webhook"
}

private fun loadChannels(prefs: android.content.SharedPreferences): List<Channel> {
    val arrStr = prefs.getString(Constants.PREF_CHANNELS, "[]") ?: "[]"
    return try {
        val arr = JSONArray(arrStr)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val typeStr = o.optString("type", "WECHAT")
            val type = try { ChannelType.valueOf(typeStr) } catch (t: Throwable) { ChannelType.WECHAT }
            Channel(o.getString("id"), o.getString("name"), type, o.getString("target"))
        }
    } catch (t: Throwable) {
        emptyList()
    }
}

internal fun saveChannels(prefs: android.content.SharedPreferences, channels: List<Channel>) {
    val arr = JSONArray()
    channels.forEach {
        val o = JSONObject()
        o.put("id", it.id)
        o.put("name", it.name)
        o.put("type", it.type.name)
        o.put("target", it.target)
        arr.put(o)
    }
    prefs.edit().putString(Constants.PREF_CHANNELS, arr.toString()).apply()
}

private fun loadConfigs(prefs: android.content.SharedPreferences): List<KeywordConfig> {
    val arrStr = prefs.getString(Constants.PREF_KEYWORD_CONFIGS, "[]") ?: "[]"
    return try {
        val arr = JSONArray(arrStr)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            KeywordConfig(o.getString("id"), o.getString("keyword"), o.getString("channelId"))
        }
    } catch (t: Throwable) {
        emptyList()
    }
}

internal fun saveConfigs(prefs: android.content.SharedPreferences, configs: List<KeywordConfig>) {
    val arr = JSONArray()
    configs.forEach {
        val o = JSONObject()
        o.put("id", it.id)
        o.put("keyword", it.keyword)
        o.put("channelId", it.channelId)
        arr.put(o)
    }
    prefs.edit().putString(Constants.PREF_KEYWORD_CONFIGS, arr.toString()).apply()
}

private fun generateConfigJson(
    channels: List<Channel>,
    configs: List<KeywordConfig>,
    showReceiverPhone: Boolean,
    showSenderPhone: Boolean,
    highlightVerificationCode: Boolean,
    batteryReminderEnabled: Boolean,
    lowBatteryReminderEnabled: Boolean,
    highBatteryReminderEnabled: Boolean,
    chargingReminderEnabled: Boolean,
    batteryReminderChannelId: String?,
    lowBatteryThreshold: Int,
    highBatteryThreshold: Int,
    customSim1Phone: String?,
    customSim2Phone: String?,
    startOnBoot: Boolean
): String {
    return JSONObject().apply {
        put("version", "2.7.11")
        put("exportTime", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()))
        
        val channelsArr = JSONArray()
        channels.forEach { ch ->
            channelsArr.put(JSONObject().apply {
                put("id", ch.id)
                put("name", ch.name)
                put("type", ch.type.name)
                put("target", ch.target)
            })
        }
        put("channels", channelsArr)
        
        val configsArr = JSONArray()
        configs.forEach { cfg ->
            configsArr.put(JSONObject().apply {
                put("id", cfg.id)
                put("keyword", cfg.keyword)
                put("channelId", cfg.channelId)
            })
        }
        put("keywordConfigs", configsArr)
        
        put("showReceiverPhone", showReceiverPhone)
        put("showSenderPhone", showSenderPhone)
        put("highlightVerificationCode", highlightVerificationCode)
        put("batteryReminderEnabled", batteryReminderEnabled)
        put("lowBatteryReminderEnabled", lowBatteryReminderEnabled)
        put("highBatteryReminderEnabled", highBatteryReminderEnabled)
        put("chargingReminderEnabled", chargingReminderEnabled)
        put("batteryReminderChannelId", batteryReminderChannelId ?: JSONObject.NULL)
        put("lowBatteryThreshold", lowBatteryThreshold)
        put("highBatteryThreshold", highBatteryThreshold)
        put("customSim1Phone", customSim1Phone ?: JSONObject.NULL)
        put("customSim2Phone", customSim2Phone ?: JSONObject.NULL)
        put("startOnBoot", startOnBoot)
    }.toString()
}

private fun exportConfig(
    context: Context,
    channels: List<Channel>,
    configs: List<KeywordConfig>,
    showReceiverPhone: Boolean,
    showSenderPhone: Boolean,
    highlightVerificationCode: Boolean,
    batteryReminderEnabled: Boolean,
    lowBatteryReminderEnabled: Boolean,
    highBatteryReminderEnabled: Boolean,
    chargingReminderEnabled: Boolean,
    batteryReminderChannelId: String?,
    lowBatteryThreshold: Int,
    highBatteryThreshold: Int,
    customSim1Phone: String?,
    customSim2Phone: String?,
    startOnBoot: Boolean
) {
    val jsonStr = generateConfigJson(channels, configs, showReceiverPhone, showSenderPhone, highlightVerificationCode, batteryReminderEnabled, lowBatteryReminderEnabled, highBatteryReminderEnabled, chargingReminderEnabled, batteryReminderChannelId, lowBatteryThreshold, highBatteryThreshold, customSim1Phone, customSim2Phone, startOnBoot)
    
    try {
        
        val downloadsDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        val fileName = "sms_forwarder_config_${System.currentTimeMillis()}.json"
        val file = java.io.File(downloadsDir, fileName)
        file.writeText(jsonStr, Charsets.UTF_8)
        
        val fileUri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            putExtra(Intent.EXTRA_SUBJECT, "短信转发助手配置")
            putExtra(Intent.EXTRA_TEXT, "这是我的短信转发助手配置文件")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        val chooser = Intent.createChooser(shareIntent, "分享配置文件")
        context.startActivity(chooser)
        
        Toast.makeText(context, "配置已导出并准备分享", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

internal fun importConfigFromJson(
    context: Context,
    jsonStr: String,
    onImportSuccess: () -> Unit
) {
    try {
        val json = JSONObject(jsonStr)
        
        val channels = mutableListOf<Channel>()
        val channelsArr = json.optJSONArray("channels") ?: JSONArray()
        for (i in 0 until channelsArr.length()) {
            val chObj = channelsArr.getJSONObject(i)
            val typeStr = chObj.optString("type", "WECHAT")
            val type = try { ChannelType.valueOf(typeStr) } catch (_: Throwable) { ChannelType.WECHAT }
            channels.add(Channel(
                id = chObj.getString("id"),
                name = chObj.getString("name"),
                type = type,
                target = chObj.getString("target")
            ))
        }
        
        val configs = mutableListOf<KeywordConfig>()
        val configsArr = json.optJSONArray("keywordConfigs") ?: JSONArray()
        for (i in 0 until configsArr.length()) {
            val cfgObj = configsArr.getJSONObject(i)
            configs.add(KeywordConfig(
                id = cfgObj.getString("id"),
                keyword = cfgObj.getString("keyword"),
                channelId = cfgObj.getString("channelId")
            ))
        }
        
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        saveChannels(prefs, channels)
        saveConfigs(prefs, configs)
        
        editor.putBoolean(Constants.PREF_SHOW_RECEIVER_PHONE, json.optBoolean("showReceiverPhone", true))
        editor.putBoolean(Constants.PREF_SHOW_SENDER_PHONE, json.optBoolean("showSenderPhone", true))
        editor.putBoolean(Constants.PREF_HIGHLIGHT_VERIFICATION_CODE, json.optBoolean("highlightVerificationCode", true))
        editor.putBoolean(Constants.PREF_BATTERY_REMINDER_ENABLED, json.optBoolean("batteryReminderEnabled", false))
        editor.putBoolean(Constants.PREF_LOW_BATTERY_REMINDER_ENABLED, json.optBoolean("lowBatteryReminderEnabled", true))
        editor.putBoolean(Constants.PREF_HIGH_BATTERY_REMINDER_ENABLED, json.optBoolean("highBatteryReminderEnabled", true))
        editor.putBoolean(Constants.PREF_CHARGING_REMINDER_ENABLED, json.optBoolean("chargingReminderEnabled", true))
        val reminderChannelId = json.optString("batteryReminderChannelId", null)
        if (reminderChannelId.isNullOrEmpty()) {
            editor.remove(Constants.PREF_BATTERY_REMINDER_CHANNEL_ID)
        } else {
            editor.putString(Constants.PREF_BATTERY_REMINDER_CHANNEL_ID, reminderChannelId)
        }
        editor.putInt(Constants.PREF_LOW_BATTERY_THRESHOLD, json.optInt("lowBatteryThreshold", Constants.DEFAULT_LOW_BATTERY_THRESHOLD))
        editor.putInt(Constants.PREF_HIGH_BATTERY_THRESHOLD, json.optInt("highBatteryThreshold", Constants.DEFAULT_HIGH_BATTERY_THRESHOLD))
        editor.putBoolean(Constants.PREF_START_ON_BOOT, json.optBoolean("startOnBoot", false))
        
        val sim1Phone = json.optString("customSim1Phone", null)
        if (sim1Phone.isNullOrEmpty()) {
            editor.remove(Constants.PREF_CUSTOM_SIM1_PHONE)
        } else {
            editor.putString(Constants.PREF_CUSTOM_SIM1_PHONE, sim1Phone)
        }
        
        val sim2Phone = json.optString("customSim2Phone", null)
        if (sim2Phone.isNullOrEmpty()) {
            editor.remove(Constants.PREF_CUSTOM_SIM2_PHONE)
        } else {
            editor.putString(Constants.PREF_CUSTOM_SIM2_PHONE, sim2Phone)
        }
        
        editor.apply()
        
        LogStore.append(context, "通过二维码导入配置成功")
        onImportSuccess()
        Toast.makeText(context, "配置导入成功", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        e.printStackTrace()
        LogStore.append(context, "通过二维码导入配置失败: ${e.message}")
        Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun decodeQrCodeFromImage(context: Context, uri: Uri, callback: (String?) -> Unit) {
    try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()
        
        if (bitmap == null) {
            callback(null)
            return
        }
        
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val binaryBitmap = com.google.zxing.BinaryBitmap(
            com.google.zxing.common.HybridBinarizer(
                com.google.zxing.RGBLuminanceSource(width, height, pixels)
            )
        )
        
        val reader = com.google.zxing.qrcode.QRCodeReader()
        val result = reader.decode(binaryBitmap)
        callback(result.text)
    } catch (e: Exception) {
        e.printStackTrace()
        callback(null)
    }
}

private fun importConfig(
    context: Context,
    fileUri: Uri,
    onImportSuccess: () -> Unit
) {
    try {
        val inputStream = context.contentResolver.openInputStream(fileUri)
            ?: throw Exception("无法打开文件")
        val jsonStr = inputStream.bufferedReader().use { it.readText() }
        inputStream.close()
        
        val json = JSONObject(jsonStr)
        
        val channels = mutableListOf<Channel>()
        val channelsArr = json.optJSONArray("channels") ?: JSONArray()
        for (i in 0 until channelsArr.length()) {
            val chObj = channelsArr.getJSONObject(i)
            val typeStr = chObj.optString("type", "WECHAT")
            val type = try { ChannelType.valueOf(typeStr) } catch (_: Throwable) { ChannelType.WECHAT }
            channels.add(Channel(
                id = chObj.getString("id"),
                name = chObj.getString("name"),
                type = type,
                target = chObj.getString("target")
            ))
        }
        
        val configs = mutableListOf<KeywordConfig>()
        val configsArr = json.optJSONArray("keywordConfigs") ?: JSONArray()
        for (i in 0 until configsArr.length()) {
            val cfgObj = configsArr.getJSONObject(i)
            configs.add(KeywordConfig(
                id = cfgObj.getString("id"),
                keyword = cfgObj.getString("keyword"),
                channelId = cfgObj.getString("channelId")
            ))
        }
        
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        saveChannels(prefs, channels)
        saveConfigs(prefs, configs)
        
        editor.putBoolean(Constants.PREF_SHOW_RECEIVER_PHONE, json.optBoolean("showReceiverPhone", true))
        editor.putBoolean(Constants.PREF_SHOW_SENDER_PHONE, json.optBoolean("showSenderPhone", true))
        editor.putBoolean(Constants.PREF_HIGHLIGHT_VERIFICATION_CODE, json.optBoolean("highlightVerificationCode", true))
        editor.putBoolean(Constants.PREF_BATTERY_REMINDER_ENABLED, json.optBoolean("batteryReminderEnabled", false))
        editor.putBoolean(Constants.PREF_LOW_BATTERY_REMINDER_ENABLED, json.optBoolean("lowBatteryReminderEnabled", true))
        editor.putBoolean(Constants.PREF_HIGH_BATTERY_REMINDER_ENABLED, json.optBoolean("highBatteryReminderEnabled", true))
        editor.putBoolean(Constants.PREF_CHARGING_REMINDER_ENABLED, json.optBoolean("chargingReminderEnabled", true))
        val reminderChannelId = json.optString("batteryReminderChannelId", null)
        if (reminderChannelId.isNullOrEmpty()) {
            editor.remove(Constants.PREF_BATTERY_REMINDER_CHANNEL_ID)
        } else {
            editor.putString(Constants.PREF_BATTERY_REMINDER_CHANNEL_ID, reminderChannelId)
        }
        editor.putInt(Constants.PREF_LOW_BATTERY_THRESHOLD, json.optInt("lowBatteryThreshold", Constants.DEFAULT_LOW_BATTERY_THRESHOLD))
        editor.putInt(Constants.PREF_HIGH_BATTERY_THRESHOLD, json.optInt("highBatteryThreshold", Constants.DEFAULT_HIGH_BATTERY_THRESHOLD))
        editor.putBoolean(Constants.PREF_START_ON_BOOT, json.optBoolean("startOnBoot", false))
        
        val sim1 = json.optString("customSim1Phone", null)
        val sim2 = json.optString("customSim2Phone", null)
        if (sim1.isNullOrEmpty()) {
            editor.remove(Constants.PREF_CUSTOM_SIM1_PHONE)
        } else {
            editor.putString(Constants.PREF_CUSTOM_SIM1_PHONE, sim1)
        }
        if (sim2.isNullOrEmpty()) {
            editor.remove(Constants.PREF_CUSTOM_SIM2_PHONE)
        } else {
            editor.putString(Constants.PREF_CUSTOM_SIM2_PHONE, sim2)
        }
        
        editor.apply()
        
        Toast.makeText(context, "配置导入成功", Toast.LENGTH_SHORT).show()
        onImportSuccess()
    } catch (e: Exception) {
        Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
