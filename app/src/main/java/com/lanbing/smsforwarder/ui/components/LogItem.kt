package com.lanbing.smsforwarder.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

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