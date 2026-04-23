package com.duq.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duq.android.data.model.Message
import com.duq.android.data.model.MessageRole
import com.duq.android.ui.theme.DuqColors
import java.time.format.DateTimeFormatter

@Composable
fun MessageBubble(
    message: Message,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == MessageRole.USER
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart

    // User messages: subtle primary gradient
    // Duq messages: glass-like surface effect
    val backgroundBrush = if (isUser) {
        Brush.linearGradient(
            colors = listOf(
                DuqColors.primary.copy(alpha = 0.15f),
                DuqColors.primary.copy(alpha = 0.08f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                DuqColors.surfaceElevated,
                DuqColors.surfaceVariant
            )
        )
    }

    val bubbleShape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomStart = if (isUser) 16.dp else 4.dp,
        bottomEnd = if (isUser) 4.dp else 16.dp
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 12.dp),
        contentAlignment = alignment
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(bubbleShape)
                .background(backgroundBrush)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Sender label for Duq
            if (!isUser) {
                Text(
                    text = "Duq",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = DuqColors.primary,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            Text(
                text = message.content,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                color = DuqColors.textPrimary,
                fontWeight = FontWeight.Normal
            )

            val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
            Text(
                text = message.createdAt.atZone(java.time.ZoneId.systemDefault()).format(timeFormatter),
                fontSize = 10.sp,
                color = DuqColors.textTertiary,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .align(Alignment.End)
            )
        }
    }
}
