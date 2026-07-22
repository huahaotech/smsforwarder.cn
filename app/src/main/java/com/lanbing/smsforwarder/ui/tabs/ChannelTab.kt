package com.lanbing.smsforwarder.ui.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Cloud
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
import com.lanbing.smsforwarder.Channel
import com.lanbing.smsforwarder.ChannelType
import com.lanbing.smsforwarder.getChannelTypeLabel
import com.lanbing.smsforwarder.ui.components.ChannelItem
import com.lanbing.smsforwarder.ui.components.ModernCard

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
    val context = LocalContext.current
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
                                context.getString(R.string.channel_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                context.getString(R.string.channel_title),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedTextField(
                        value = newChannelName,
                        onValueChange = onNewChannelNameChange,
                        label = { Text(context.getString(R.string.channel_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Outlined.Label, contentDescription = null)
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
                            label = { Text(context.getString(R.string.channel_type)) },
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
                        label = { Text(context.getString(R.string.webhook_url)) },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(Icons.Outlined.Link, contentDescription = null)
                        },
                        shape = RoundedCornerShape(12.dp)
                    )

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
                                context.getString(R.string.security_warning),
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
                        Text(context.getString(R.string.add_channel), fontSize = 16.sp)
                    }

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