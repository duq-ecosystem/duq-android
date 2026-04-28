package com.duq.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duq.android.data.model.Message
import com.duq.android.data.model.MessageRole
import com.duq.android.ui.theme.DuqColors
import java.time.format.DateTimeFormatter

/**
 * Premium Message bubble with Glassmorphism 3.0 design.
 *
 * Features:
 * - Animated slide-in entrance
 * - Glass-like translucent backgrounds with subtle glow
 * - Gradient borders with shimmer effect
 * - Streaming text support for AI responses
 * - Audio playback controls for voice messages
 */
@Composable
fun MessageBubble(
    message: Message,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false,
    audioPlaybackState: AudioPlaybackState = AudioPlaybackState.IDLE,
    audioProgress: Float = 0f,
    onAudioPlayPauseClick: () -> Unit = {}
) {
    val isUser = message.role == MessageRole.USER
    val alignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart

    // Animation state
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    // Glow animation for streaming
    val infiniteTransition = rememberInfiniteTransition(label = "bubbleGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    // Premium color schemes
    val (backgroundBrush, borderBrush, glowColor) = if (isUser) {
        Triple(
            Brush.linearGradient(
                colors = listOf(
                    DuqColors.primary.copy(alpha = 0.2f),
                    DuqColors.primary.copy(alpha = 0.08f),
                    DuqColors.primaryDim.copy(alpha = 0.05f)
                ),
                start = Offset(0f, 0f),
                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
            ),
            Brush.linearGradient(
                colors = listOf(
                    DuqColors.primary.copy(alpha = 0.5f),
                    DuqColors.primary.copy(alpha = 0.2f),
                    DuqColors.primaryDim.copy(alpha = 0.1f)
                )
            ),
            DuqColors.primary
        )
    } else {
        Triple(
            Brush.linearGradient(
                colors = listOf(
                    DuqColors.surfaceElevated,
                    DuqColors.surface.copy(alpha = 0.8f),
                    DuqColors.surfaceVariant.copy(alpha = 0.5f)
                ),
                start = Offset(0f, 0f),
                end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
            ),
            Brush.linearGradient(
                colors = listOf(
                    DuqColors.glassBorder.copy(alpha = 0.3f),
                    DuqColors.glassBorder.copy(alpha = 0.1f),
                    Color.Transparent
                )
            ),
            if (isStreaming) DuqColors.accent else DuqColors.primary
        )
    }

    val bubbleShape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = if (isUser) 20.dp else 4.dp,
        bottomEnd = if (isUser) 4.dp else 20.dp
    )

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInHorizontally(
            initialOffsetX = { if (isUser) it else -it },
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)
        ) + fadeIn(animationSpec = tween(300))
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 12.dp),
            contentAlignment = alignment
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    // Subtle glow effect behind bubble
                    .drawBehind {
                        if (isStreaming || !isUser) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        glowColor.copy(alpha = if (isStreaming) glowAlpha else 0.05f),
                                        Color.Transparent
                                    ),
                                    center = Offset(size.width * 0.3f, size.height * 0.5f),
                                    radius = size.width * 0.8f
                                )
                            )
                        }
                    }
                    .clip(bubbleShape)
                    .background(backgroundBrush)
                    .border(
                        width = 1.dp,
                        brush = borderBrush,
                        shape = bubbleShape
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Sender label for Duq with icon
                if (!isUser) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        // Mini duck icon indicator
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            DuqColors.primary,
                                            DuqColors.primaryDim
                                        )
                                    ),
                                    shape = RoundedCornerShape(50)
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Duq",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = DuqColors.primary,
                            letterSpacing = 1.sp
                        )
                        if (isStreaming) {
                            Spacer(modifier = Modifier.width(8.dp))
                            // Typing indicator dots
                            TypingIndicator()
                        }
                    }
                }

                // Message content
                if (!isUser && (isStreaming || message.content.isNotEmpty())) {
                    StreamingText(
                        text = message.content,
                        isStreaming = isStreaming,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = DuqColors.textPrimary
                    )
                } else {
                    Text(
                        text = message.content,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = DuqColors.textPrimary,
                        fontWeight = FontWeight.Normal
                    )
                }

                // Audio playback controls for voice messages
                if (message.hasAudio) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AudioMessageControls(
                        state = audioPlaybackState,
                        durationMs = message.audioDurationMs,
                        progress = audioProgress,
                        onPlayPauseClick = onAudioPlayPauseClick
                    )
                }

                // Timestamp
                val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
                Text(
                    text = message.createdAt.atZone(java.time.ZoneId.systemDefault()).format(timeFormatter),
                    fontSize = 10.sp,
                    color = DuqColors.textMuted,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .align(Alignment.End)
                )
            }
        }
    }
}

/**
 * Animated typing indicator (three bouncing dots)
 */
@Composable
private fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")

    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) { index ->
            val delay = index * 150
            val offsetY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -4f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 600
                        0f at 0
                        -4f at 150
                        0f at 300
                        0f at 600
                    },
                    repeatMode = RepeatMode.Restart,
                    initialStartOffset = StartOffset(delay)
                ),
                label = "dot$index"
            )

            Box(
                modifier = Modifier
                    .offset(y = offsetY.dp)
                    .size(4.dp)
                    .background(
                        color = DuqColors.accent,
                        shape = RoundedCornerShape(50)
                    )
            )
        }
    }
}
