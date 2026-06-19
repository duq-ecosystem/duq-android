package com.duq.android.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duq.android.ui.theme.DuqColors

/**
 * Streaming text for AI replies. The server already streams the reply incrementally
 * (cumulative text grows per delta), so this renders [text] directly and shows a
 * blinking cursor while [isStreaming]. (A local char-by-char typewriter was removed:
 * its counter reset on every delta and made the text flicker.)
 *
 * @param text Current (cumulative) text to display
 * @param isStreaming Whether the reply is still streaming (shows the cursor)
 */
@Composable
fun StreamingText(
    text: String,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 15.sp,
    lineHeight: TextUnit = 21.sp,
    color: Color = DuqColors.textPrimary
) {
    // The SERVER already streams the reply incrementally (cumulative text grows
    // with each delta). Render that text directly — a second char-by-char
    // typewriter on top reset on every delta and made the text flicker. Just show
    // the current text and a blinking cursor while streaming.
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            lineHeight = lineHeight,
            color = color,
            fontWeight = FontWeight.Normal
        )

        if (isStreaming) {
            BlinkingCursor(
                color = DuqColors.cursorBlink,
                modifier = Modifier.padding(start = 2.dp)
            )
        }
    }
}

/**
 * Blinking cursor for streaming text effect.
 * Uses cyan/primary color matching AI aesthetic.
 */
@Composable
fun BlinkingCursor(
    color: Color = DuqColors.primary,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor_blink")

    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_alpha"
    )

    Text(
        text = "|",
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = modifier
            .alpha(alpha)
            .width(8.dp)
    )
}

/**
 * Preview helper - shows streaming text with different states
 */
@Composable
fun StreamingTextPreview() {
    StreamingText(
        text = "Hello, I'm Duq, your AI assistant. How can I help you today?",
        isStreaming = true
    )
}
