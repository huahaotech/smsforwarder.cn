package com.lanbing.smsforwarder.ui.tabs

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lanbing.smsforwarder.ui.components.ModernCard
import com.lanbing.smsforwarder.ui.components.PermissionItem

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
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            ModernCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                context.getString(R.string.service_title),
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
                                    if (isEnabled) context.getString(R.string.app_subtitle) else context.getString(R.string.app_subtitle_stopped),
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

                    PermissionItem(
                        icon = Icons.Outlined.Notifications,
                        title = context.getString(R.string.notification_permission),
                        granted = notifGranted,
                        onClick = onRequestNotificationPermission
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PermissionItem(
                        icon = Icons.Outlined.Sms,
                        title = context.getString(R.string.sms_permission),
                        granted = smsGranted,
                        onClick = onRequestSmsPermission
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    PermissionItem(
                        icon = Icons.Outlined.BatteryFull,
                        title = context.getString(R.string.battery_optimization),
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
                                context.getString(R.string.start_on_boot),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                context.getString(R.string.start_on_boot_desc),
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

        item {
            ModernCard(modifier = Modifier.fillMaxWidth()) {
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
                        context.getString(R.string.security_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}