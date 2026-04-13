package com.jarvis.android.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.jarvis.android.JarvisState
import kotlin.math.cos
import kotlin.math.sin

// State-based color palettes
private object ArcReactorColors {
    // IDLE - Iron Man Red
    val idleCore = Color(0xFFE62429)
    val idleGlow = Color(0xFFFF3B3B)
    val idleDim = Color(0xFF8B1517)

    // LISTENING - Cyan Blue (active listening)
    val listeningCore = Color(0xFF00D4FF)
    val listeningGlow = Color(0xFF00F5FF)
    val listeningDim = Color(0xFF006B80)

    // PROCESSING - Orange/Gold (thinking)
    val processingCore = Color(0xFFFF9500)
    val processingGlow = Color(0xFFFFB340)
    val processingDim = Color(0xFF805000)

    // PLAYING - Green (speaking)
    val playingCore = Color(0xFF00E676)
    val playingGlow = Color(0xFF69F0AE)
    val playingDim = Color(0xFF00733D)

    // ERROR - Deep Red (error)
    val errorCore = Color(0xFF660000)
    val errorGlow = Color(0xFF990000)
    val errorDim = Color(0xFF330000)
}

@Composable
fun ArcReactor(
    state: JarvisState?,
    modifier: Modifier = Modifier
) {
    // Get colors based on state
    val (coreColor, glowColor, dimColor) = when (state) {
        JarvisState.IDLE -> Triple(ArcReactorColors.idleCore, ArcReactorColors.idleGlow, ArcReactorColors.idleDim)
        JarvisState.LISTENING, JarvisState.RECORDING -> Triple(ArcReactorColors.listeningCore, ArcReactorColors.listeningGlow, ArcReactorColors.listeningDim)
        JarvisState.PROCESSING -> Triple(ArcReactorColors.processingCore, ArcReactorColors.processingGlow, ArcReactorColors.processingDim)
        JarvisState.PLAYING -> Triple(ArcReactorColors.playingCore, ArcReactorColors.playingGlow, ArcReactorColors.playingDim)
        JarvisState.ERROR -> Triple(ArcReactorColors.errorCore, ArcReactorColors.errorGlow, ArcReactorColors.errorDim)
        null -> Triple(ArcReactorColors.idleDim, ArcReactorColors.idleDim, ArcReactorColors.idleDim)
    }

    // Animate color transitions
    val animatedCore by animateColorAsState(
        targetValue = coreColor,
        animationSpec = tween(300),
        label = "coreColor"
    )
    val animatedGlow by animateColorAsState(
        targetValue = glowColor,
        animationSpec = tween(300),
        label = "glowColor"
    )
    val animatedDim by animateColorAsState(
        targetValue = dimColor,
        animationSpec = tween(300),
        label = "dimColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val pulseSpeed = when (state) {
        JarvisState.LISTENING, JarvisState.RECORDING -> 400
        JarvisState.PROCESSING -> 200
        JarvisState.PLAYING -> 600
        JarvisState.ERROR -> 150
        else -> 2000
    }

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(pulseSpeed, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state == JarvisState.PROCESSING) 1000 else 3000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = when (state) {
            JarvisState.LISTENING, JarvisState.RECORDING -> 1.08f
            JarvisState.PLAYING -> 1.05f
            else -> 1f
        },
        animationSpec = infiniteRepeatable(
            animation = tween(pulseSpeed, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val isActive = state != null && state != JarvisState.ERROR
    val isProcessing = state == JarvisState.PROCESSING

    Canvas(modifier = modifier.size(200.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 2

        // Outer glow
        if (state != null) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        animatedGlow.copy(alpha = pulseAlpha * 0.4f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius * 1.3f
                ),
                radius = radius * 1.3f,
                center = center
            )
        }

        // Main outer ring
        drawCircle(
            color = animatedDim.copy(alpha = 0.8f),
            radius = radius * 0.95f,
            center = center,
            style = Stroke(width = 4.dp.toPx())
        )

        // Secondary ring with segments
        if (isProcessing) {
            rotate(rotationAngle, center) {
                drawArcSegments(center, radius * 0.85f, animatedGlow.copy(alpha = pulseAlpha))
            }
        } else {
            drawArcSegments(center, radius * 0.85f, animatedCore.copy(alpha = if (isActive) pulseAlpha else 0.5f))
        }

        // Inner ring
        drawCircle(
            color = if (isActive) animatedCore else animatedDim.copy(alpha = 0.4f),
            radius = radius * 0.65f,
            center = center,
            style = Stroke(width = 3.dp.toPx())
        )

        // Triangular elements
        if (isProcessing) {
            rotate(rotationAngle * 0.7f, center) {
                drawTriangularElements(center, radius * 0.75f, animatedGlow.copy(alpha = pulseAlpha))
            }
        } else {
            drawTriangularElements(center, radius * 0.75f, animatedCore.copy(alpha = if (isActive) 0.8f else 0.3f))
        }

        // Core circle with gradient
        val coreRadius = radius * 0.35f * scale
        drawCircle(
            brush = Brush.radialGradient(
                colors = if (isActive) {
                    listOf(animatedGlow.copy(alpha = pulseAlpha), animatedCore, animatedDim)
                } else {
                    listOf(animatedDim.copy(alpha = 0.5f), Color(0xFF1A0A0A))
                },
                center = center,
                radius = coreRadius
            ),
            radius = coreRadius,
            center = center
        )

        // Inner core highlight
        if (isActive) {
            drawCircle(
                color = Color.White.copy(alpha = pulseAlpha * 0.4f),
                radius = coreRadius * 0.3f,
                center = center
            )
        }
    }
}

@Composable
private fun animateColorAsState(
    targetValue: Color,
    animationSpec: AnimationSpec<Color>,
    label: String
): State<Color> {
    return androidx.compose.animation.animateColorAsState(
        targetValue = targetValue,
        animationSpec = animationSpec,
        label = label
    )
}

private fun DrawScope.drawArcSegments(center: Offset, radius: Float, color: Color) {
    val segmentCount = 8
    val gapAngle = 8f
    val segmentAngle = (360f / segmentCount) - gapAngle

    for (i in 0 until segmentCount) {
        val startAngle = i * (360f / segmentCount) - 90f
        drawArc(
            color = color,
            startAngle = startAngle,
            sweepAngle = segmentAngle,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = Stroke(width = 6f, cap = StrokeCap.Round)
        )
    }
}

private fun DrawScope.drawTriangularElements(center: Offset, radius: Float, color: Color) {
    val count = 3
    for (i in 0 until count) {
        val angle = Math.toRadians((i * 120.0) - 90.0)
        val innerRadius = radius * 0.5f
        val outerRadius = radius * 0.7f

        val innerX = center.x + innerRadius * cos(angle).toFloat()
        val innerY = center.y + innerRadius * sin(angle).toFloat()
        val outerX = center.x + outerRadius * cos(angle).toFloat()
        val outerY = center.y + outerRadius * sin(angle).toFloat()

        drawLine(
            color = color,
            start = Offset(innerX, innerY),
            end = Offset(outerX, outerY),
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )
    }
}
