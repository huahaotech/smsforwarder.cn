package com.lanbing.smsforwarder.ui.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lanbing.smsforwarder.Channel
import com.lanbing.smsforwarder.KeywordConfig
import com.lanbing.smsforwarder.ui.components.ConfigItem
import com.lanbing.smsforwarder.ui.components.ModernCard

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
                                .background(Color(0xFF10B981).copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Rule,
                                contentDescription = null,
                                tint = Color(0xFF10B981)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                context.getString(R.string.keyword_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                context.getString(R.string.select_channel),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedTextField(
                        value = newKeywordInput,
                        onValueChange = onNewKeywordInputChange,
                        label = { Text(context.getString(R.string.keyword_input_hint)) },
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
                            value = channels.find { it.id == selectedChannelIdForNewCfg }?.name ?: context.getString(R.string.select_channel),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(context.getString(R.string.select_channel)) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null)
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
                                    text = { Text(context.getString(R.string.please_add_channel)) },
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
                        Text(context.getString(R.string.add_keyword), fontSize = 16.sp)
                    }

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