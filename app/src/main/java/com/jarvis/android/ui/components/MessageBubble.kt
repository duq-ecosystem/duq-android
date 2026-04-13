package com.jarvis.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jarvis.android.data.model.Message
import com.jarvis.android.data.model.MessageRole
import java.time.format.DateTimeFormatter

@Composable
fun MessageBubble(
    message: Message,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == MessageRole.USER
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val backgroundColor = if (isUser) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    } else {
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = alignment
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .background(backgroundColor, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
            Text(
                text = message.createdAt.atZone(java.time.ZoneId.systemDefault()).format(timeFormatter),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
