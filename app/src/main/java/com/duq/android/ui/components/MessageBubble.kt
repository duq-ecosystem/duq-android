package com.duq.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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

/**
 * Message bubble with Glassmorphism 2.0 design.
 *
 * Features:
 * - Glass-like translucent backgrounds
 * - Subtle gradient borders
 * - Streaming text support for AI responses
 *
 * @param message The message to display
 * @param isStreaming Whether AI response is currently streaming
 * @param modifier Modifier for the component
 */
@Composable
fun MessageBubble(
    message: Message,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false
) {
    val isUser = message.role == MessageRole.USER
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart

    // Glassmorphism 2.0 design:
    // User messages: subtle primary gradient with glass border
    // Duq messages: frosted glass surface effect
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
                DuqColors.glassSurface,
                DuqColors.glassSurface.copy(alpha = 0.05f)
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
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = if (isUser) {
                            listOf(
                                DuqColors.primary.copy(alpha = 0.3f),
                                DuqColors.primary.copy(alpha = 0.1f)
                            )
                        } else {
                            listOf(
                                DuqColors.glassBorder,
                                DuqColors.glassBorder.copy(alpha = 0.1f)
                            )
                        }
                    ),
                    shape = bubbleShape
                )
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

            // Use StreamingText for AI responses, regular Text for user
            if (!isUser && (isStreaming || message.content.isNotEmpty())) {
                StreamingText(
                    text = message.content,
                    isStreaming = isStreaming,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    color = DuqColors.textPrimary
                )
            } else {
                Text(
                    text = message.content,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    color = DuqColors.textPrimary,
                    fontWeight = FontWeight.Normal
                )
            }

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
