package com.lanbing.smsforwarder.ui.tabs

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lanbing.smsforwarder.Channel
import com.lanbing.smsforwarder.ui.components.ModernCard
import com.lanbing.smsforwarder.ui.components.PermissionManagementItem

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
    onExportConfig: () -> Unit,
    onImportConfig: () -> Unit,
    onImportFromGallery: () -> Unit,
    simCardInfoContent: @Composable () -> Unit
) {
    var showChannelSelectionDialog by remember { mutableStateOf(false) }
    var showImportOptionsDialog by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item { simCardInfoContent() }

        item {
            ModernCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        ctx.getString(R.string.battery_reminder_section),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

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
                                ctx.getString(R.string.enable_battery_reminder),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                ctx.getString(R.string.enable_battery_reminder_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = batteryReminderEnabled, onCheckedChange = onBatteryReminderEnabledChange)
                    }

                    if (batteryReminderEnabled) {
                        Spacer(modifier = Modifier.height(16.dp))

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
                                    ctx.getString(R.string.low_battery_reminder_title),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    ctx.getString(R.string.low_battery_reminder_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = lowBatteryReminderEnabled, onCheckedChange = onLowBatteryReminderEnabledChange)
                        }

                        if (lowBatteryReminderEnabled) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(ctx.getString(R.string.low_battery_threshold_title), style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Text("$lowBatteryThreshold%", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Slider(
                                        value = lowBatteryThreshold.toFloat(),
                                        onValueChange = { onLowBatteryThresholdChange(it.toInt()) },
                                        valueRange = 5f..50f,
                                        steps = 8,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Text(ctx.getString(R.string.low_battery_threshold_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

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
                                    ctx.getString(R.string.high_battery_reminder_title),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    ctx.getString(R.string.high_battery_reminder_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = highBatteryReminderEnabled, onCheckedChange = onHighBatteryReminderEnabledChange)
                        }

                        if (highBatteryReminderEnabled) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(ctx.getString(R.string.high_battery_threshold_title), style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Text("$highBatteryThreshold%", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Slider(
                                        value = highBatteryThreshold.toFloat(),
                                        onValueChange = { onHighBatteryThresholdChange(it.toInt()) },
                                        valueRange = 50f..100f,
                                        steps = 8,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Text(ctx.getString(R.string.high_battery_threshold_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

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
                                ctx.getString(R.string.charging_reminder_title),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                ctx.getString(R.string.charging_reminder_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = chargingReminderEnabled, onCheckedChange = onChargingReminderEnabledChange)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(ctx.getString(R.string.reminder_channel), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(ctx.getString(R.string.reminder_channel_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                .clickable { showChannelSelectionDialog = true }
                                .padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = if (batteryReminderChannelId.isNullOrEmpty()) ctx.getString(R.string.all_channels)
                                    else channels.find { it.id == batteryReminderChannelId }?.name ?: ctx.getString(R.string.unknown_channel),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        item {
            ModernCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(ctx.getString(R.string.message_format), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Phone, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ctx.getString(R.string.show_receiver_phone), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(ctx.getString(R.string.show_receiver_phone_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = showReceiverPhone, onCheckedChange = onShowReceiverPhoneChange)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ctx.getString(R.string.show_sender_phone), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(ctx.getString(R.string.show_sender_phone_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = showSenderPhone, onCheckedChange = onShowSenderPhoneChange)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ctx.getString(R.string.highlight_verification_code), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(ctx.getString(R.string.highlight_code_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = highlightVerificationCode, onCheckedChange = onHighlightVerificationCodeChange)
                    }
                }
            }
        }

        item {
            ModernCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(ctx.getString(R.string.tools), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(onClick = onExportConfig, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(vertical = 14.dp)) {
                        Icon(Icons.Filled.QrCode, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(ctx.getString(R.string.export_config), fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(onClick = { showImportOptionsDialog = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(vertical = 14.dp)) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(ctx.getString(R.string.import_config), fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(onClick = onShowTestDialog, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(vertical = 14.dp)) {
                        Icon(Icons.Outlined.Science, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(ctx.getString(R.string.test_rule), fontSize = 16.sp)
                    }
                }
            }
        }

        item {
            ModernCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(ctx.getString(R.string.permission_management), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    val smsGranted by remember(permissionUpdateTrigger) {
                        derivedStateOf { ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECEIVE_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED }
                    }
                    val notifGranted by remember(permissionUpdateTrigger) {
                        derivedStateOf { NotificationManagerCompat.from(ctx).areNotificationsEnabled() }
                    }
                    val phoneGranted by remember(permissionUpdateTrigger) {
                        derivedStateOf { ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED }
                    }
                    val batteryGranted by remember(permissionUpdateTrigger) {
                        derivedStateOf {
                            (ctx.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)
                        }
                    }

                    PermissionManagementItem(
                        icon = Icons.AutoMirrored.Outlined.Message,
                        title = ctx.getString(R.string.permission_sms_title),
                        description = ctx.getString(R.string.permission_sms_desc),
                        granted = smsGranted,
                        onClick = {
                            try {
                                ctx.startActivity(Intent().apply {
                                    action = "android.settings.APPLICATION_DETAILS_SETTINGS"
                                    data = Uri.fromParts("package", ctx.packageName, null)
                                })
                            } catch (_: Exception) {
                                Toast.makeText(ctx, ctx.getString(R.string.open_system_settings), Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    PermissionManagementItem(
                        icon = Icons.Outlined.Notifications,
                        title = ctx.getString(R.string.permission_notification_title),
                        description = ctx.getString(R.string.permission_notification_desc),
                        granted = notifGranted,
                        onClick = {
                            try {
                                ctx.startActivity(Intent().apply {
                                    action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Settings.ACTION_APP_NOTIFICATION_SETTINGS
                                    else "android.settings.APPLICATION_DETAILS_SETTINGS"
                                    data = Uri.fromParts("package", ctx.packageName, null)
                                })
                            } catch (_: Exception) {
                                Toast.makeText(ctx, ctx.getString(R.string.open_system_settings), Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    PermissionManagementItem(
                        icon = Icons.Outlined.Phone,
                        title = ctx.getString(R.string.permission_phone_title),
                        description = ctx.getString(R.string.permission_phone_desc),
                        granted = phoneGranted,
                        onClick = {
                            try {
                                ctx.startActivity(Intent().apply {
                                    action = "android.settings.APPLICATION_DETAILS_SETTINGS"
                                    data = Uri.fromParts("package", ctx.packageName, null)
                                })
                            } catch (_: Exception) {
                                Toast.makeText(ctx, ctx.getString(R.string.open_system_settings), Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    PermissionManagementItem(
                        icon = Icons.Outlined.BatteryFull,
                        title = ctx.getString(R.string.permission_battery_title),
                        description = ctx.getString(R.string.permission_battery_desc),
                        granted = batteryGranted,
                        onClick = {
                            try {
                                ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${ctx.packageName}")
                                })
                            } catch (_: Exception) {
                                Toast.makeText(ctx, ctx.getString(R.string.open_system_settings), Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }

        item {
            ModernCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(ctx.getString(R.string.privacy_settings), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(onClick = onShowPrivacyPolicy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(vertical = 14.dp)) {
                        Icon(Icons.Outlined.FilePresent, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(ctx.getString(R.string.privacy_policy), fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = onRevokePrivacyConsent,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEE4444)),
                        border = BorderStroke(1.dp, Color(0xFFEE4444).copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Outlined.Shield, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(ctx.getString(R.string.revoke_consent), fontSize = 16.sp)
                    }
                }
            }
        }
    }

    if (showChannelSelectionDialog) {
        AlertDialog(
            onDismissRequest = { showChannelSelectionDialog = false },
            title = { Text(ctx.getString(R.string.select_reminder_channel)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        ctx.getString(R.string.select_reminder_channel_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    LazyColumn(modifier = Modifier.height(200.dp)) {
                        item {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    onBatteryReminderChannelIdChange(null)
                                    showChannelSelectionDialog = false
                                }.padding(vertical = 12.dp)
                            ) {
                                RadioButton(selected = batteryReminderChannelId.isNullOrEmpty(), onClick = {
                                    onBatteryReminderChannelIdChange(null)
                                    showChannelSelectionDialog = false
                                })
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(ctx.getString(R.string.all_channels))
                            }
                        }
                        channels.forEach { channel ->
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        onBatteryReminderChannelIdChange(channel.id)
                                        showChannelSelectionDialog = false
                                    }.padding(vertical = 12.dp)
                                ) {
                                    RadioButton(selected = batteryReminderChannelId == channel.id, onClick = {
                                        onBatteryReminderChannelIdChange(channel.id)
                                        showChannelSelectionDialog = false
                                    })
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(channel.name)
                                        Text(channel.type.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showChannelSelectionDialog = false }) { Text(ctx.getString(R.string.dialog_cancel)) } }
        )
    }

    if (showImportOptionsDialog) {
        AlertDialog(
            onDismissRequest = { showImportOptionsDialog = false },
            title = { Text(ctx.getString(R.string.select_import_method)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = { showImportOptionsDialog = false; onImportConfig() },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 16.dp, horizontal = 24.dp)
                    ) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(ctx.getString(R.string.import_config), fontSize = 16.sp)
                    }
                    TextButton(
                        onClick = { showImportOptionsDialog = false; onImportFromGallery() },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 16.dp, horizontal = 24.dp)
                    ) {
                        Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(ctx.getString(R.string.import_from_gallery), fontSize = 16.sp)
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showImportOptionsDialog = false }) { Text(ctx.getString(R.string.dialog_cancel)) } }
        )
    }
}