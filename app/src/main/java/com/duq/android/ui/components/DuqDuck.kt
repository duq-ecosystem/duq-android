package com.duq.android.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp
import com.duq.android.DuqState
import com.duq.android.ui.theme.DuqColors
import kotlin.math.*
import kotlin.random.Random

/**
 * DuqDuck - Premium animated rubber duck mascot
 * Professional design with 3D effects, particles, and smooth animations
 */
@Composable
fun DuqDuck(
    state: DuqState?,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "duckAnimation")

    // Bobbing animation (floating on water)
    val bobSpeed = when (state) {
        DuqState.LISTENING, DuqState.RECORDING -> 500
        DuqState.PROCESSING -> 250
        DuqState.PLAYING -> 700
        else -> 1200
    }

    val bob by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(bobSpeed, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bob"
    )

    // Smooth head tilt
    val headTilt by infiniteTransition.animateFloat(
        initialValue = -8f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state == DuqState.LISTENING || state == DuqState.RECORDING) 350 else 1800,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "headTilt"
    )

    // Glow pulse with breathing effect
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    DuqState.PROCESSING -> 350
                    DuqState.PLAYING -> 500
                    DuqState.LISTENING, DuqState.RECORDING -> 400
                    else -> 2000
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )

    // Water ripple animation
    val ripple by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple"
    )

    // Particle rotation
    val particleRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particleRotation"
    )

    // Eye blink animation
    val blink by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4000
                0f at 0
                0f at 3700
                1f at 3850
                0f at 4000
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "blink"
    )

    // State-based glow color
    val glowColor = when (state) {
        DuqState.IDLE -> DuqColors.primary
        DuqState.LISTENING, DuqState.RECORDING -> DuqColors.primaryBright
        DuqState.PROCESSING -> DuqColors.accent
        DuqState.PLAYING -> DuqColors.success
        DuqState.ERROR -> DuqColors.error
        null -> DuqColors.textMuted
    }

    // Particle seeds
    val particles = remember { List(12) { Random.nextFloat() to Random.nextFloat() } }

    Canvas(modifier = modifier.size(180.dp)) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val duckSize = size.minDimension * 0.65f
        val bobOffset = bob * 10f

        // === BACKGROUND GLOW ===
        // Multiple layered glows for depth
        for (i in 3 downTo 0) {
            val glowRadius = duckSize * (0.7f + i * 0.15f)
            val alpha = (0.15f - i * 0.03f) * glowPulse
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        glowColor.copy(alpha = alpha),
                        glowColor.copy(alpha = alpha * 0.5f),
                        Color.Transparent
                    ),
                    center = Offset(centerX, centerY),
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = Offset(centerX, centerY)
            )
        }

        // === FLOATING PARTICLES ===
        if (state == DuqState.PROCESSING || state == DuqState.LISTENING || state == DuqState.RECORDING) {
            particles.forEachIndexed { index, (seed1, seed2) ->
                val angle = (index * 30f + particleRotation * (0.5f + seed1 * 0.5f)) * (PI / 180f).toFloat()
                val distance = duckSize * (0.45f + seed2 * 0.35f)
                val particleX = centerX + cos(angle) * distance
                val particleY = centerY + sin(angle) * distance
                val particleSize = 2f + seed1 * 4f
                val particleAlpha = (0.3f + seed2 * 0.5f) * glowPulse

                drawCircle(
                    color = glowColor.copy(alpha = particleAlpha),
                    radius = particleSize,
                    center = Offset(particleX, particleY)
                )
            }
        }

        // === WATER RIPPLES ===
        for (i in 0..2) {
            val ripplePhase = (ripple + i * 0.33f) % 1f
            val rippleRadius = duckSize * 0.4f + ripplePhase * duckSize * 0.5f
            val rippleAlpha = (1f - ripplePhase) * 0.25f

            drawCircle(
                color = DuqColors.primary.copy(alpha = rippleAlpha),
                radius = rippleRadius,
                center = Offset(centerX, centerY + duckSize * 0.28f + bobOffset),
                style = Stroke(width = 2f - ripplePhase)
            )
        }

        // Duck body position
        val bodyY = centerY + bobOffset

        // === SHADOW ===
        drawOval(
            color = Color.Black.copy(alpha = 0.2f),
            topLeft = Offset(centerX - duckSize * 0.25f, centerY + duckSize * 0.32f),
            size = Size(duckSize * 0.5f, duckSize * 0.12f)
        )

        // === DUCK BODY ===
        val bodyPath = Path().apply {
            val bodyWidth = duckSize * 0.55f
            val bodyHeight = duckSize * 0.42f
            val bodyTop = bodyY - bodyHeight * 0.3f

            // Smooth body curve
            moveTo(centerX - bodyWidth * 0.1f, bodyTop)
            // Top curve
            cubicTo(
                centerX + bodyWidth * 0.5f, bodyTop - bodyHeight * 0.1f,
                centerX + bodyWidth * 0.55f, bodyY + bodyHeight * 0.2f,
                centerX + bodyWidth * 0.1f, bodyY + bodyHeight * 0.35f
            )
            // Tail
            cubicTo(
                centerX - bodyWidth * 0.2f, bodyY + bodyHeight * 0.45f,
                centerX - bodyWidth * 0.55f, bodyY + bodyHeight * 0.2f,
                centerX - bodyWidth * 0.45f, bodyY - bodyHeight * 0.1f
            )
            // Back to top
            cubicTo(
                centerX - bodyWidth * 0.35f, bodyTop - bodyHeight * 0.05f,
                centerX - bodyWidth * 0.15f, bodyTop - bodyHeight * 0.05f,
                centerX - bodyWidth * 0.1f, bodyTop
            )
            close()
        }

        // Body with gradient
        drawPath(
            path = bodyPath,
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFE566), // Bright highlight
                    DuqColors.primary,
                    DuqColors.primaryDim
                ),
                center = Offset(centerX - duckSize * 0.1f, bodyY - duckSize * 0.15f),
                radius = duckSize * 0.45f
            )
        )

        // Body highlight (glossy effect)
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.4f),
                    Color.Transparent
                ),
                center = Offset(centerX - duckSize * 0.08f, bodyY - duckSize * 0.1f),
                radius = duckSize * 0.15f
            ),
            topLeft = Offset(centerX - duckSize * 0.2f, bodyY - duckSize * 0.2f),
            size = Size(duckSize * 0.25f, duckSize * 0.15f)
        )

        // === DUCK HEAD ===
        rotate(headTilt, pivot = Offset(centerX + duckSize * 0.05f, bodyY - duckSize * 0.2f)) {
            val headCenterX = centerX + duckSize * 0.08f
            val headCenterY = bodyY - duckSize * 0.28f
            val headRadius = duckSize * 0.2f

            // Head with gradient
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFE566),
                        DuqColors.primary,
                        DuqColors.primaryDim
                    ),
                    center = Offset(headCenterX - headRadius * 0.3f, headCenterY - headRadius * 0.3f),
                    radius = headRadius * 1.3f
                ),
                radius = headRadius,
                center = Offset(headCenterX, headCenterY)
            )

            // Head highlight
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.5f),
                        Color.Transparent
                    )
                ),
                radius = headRadius * 0.4f,
                center = Offset(headCenterX - headRadius * 0.3f, headCenterY - headRadius * 0.35f)
            )

            // === BEAK ===
            val beakPath = Path().apply {
                val beakStartX = headCenterX + headRadius * 0.7f
                val beakY = headCenterY + headRadius * 0.15f
                val beakLength = duckSize * 0.15f
                val beakHeight = duckSize * 0.08f

                // Upper beak
                moveTo(beakStartX - duckSize * 0.02f, beakY - beakHeight * 0.3f)
                quadraticBezierTo(
                    beakStartX + beakLength * 0.7f, beakY - beakHeight * 0.5f,
                    beakStartX + beakLength, beakY
                )
                // Lower beak
                quadraticBezierTo(
                    beakStartX + beakLength * 0.7f, beakY + beakHeight * 0.6f,
                    beakStartX - duckSize * 0.02f, beakY + beakHeight * 0.4f
                )
                close()
            }

            drawPath(
                path = beakPath,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFF9500),
                        DuqColors.accent,
                        DuqColors.accentDim
                    ),
                    start = Offset(headCenterX + headRadius, headCenterY - duckSize * 0.02f),
                    end = Offset(headCenterX + headRadius + duckSize * 0.12f, headCenterY + duckSize * 0.02f)
                )
            )

            // Beak highlight
            drawPath(
                path = Path().apply {
                    val beakStartX = headCenterX + headRadius * 0.7f
                    val beakY = headCenterY + headRadius * 0.05f
                    moveTo(beakStartX, beakY)
                    quadraticBezierTo(
                        beakStartX + duckSize * 0.05f, beakY - duckSize * 0.02f,
                        beakStartX + duckSize * 0.08f, beakY
                    )
                },
                color = Color.White.copy(alpha = 0.3f),
                style = Stroke(width = 2f)
            )

            // === EYE ===
            val eyeX = headCenterX + headRadius * 0.25f
            val eyeY = headCenterY - headRadius * 0.15f
            val eyeRadius = headRadius * 0.28f

            // Eye socket shadow
            drawCircle(
                color = DuqColors.primaryDark.copy(alpha = 0.3f),
                radius = eyeRadius * 1.1f,
                center = Offset(eyeX + 1f, eyeY + 1f)
            )

            // Eye white
            val eyeHeight = eyeRadius * 2f * (1f - blink * 0.9f)
            drawOval(
                color = Color.White,
                topLeft = Offset(eyeX - eyeRadius, eyeY - eyeHeight / 2),
                size = Size(eyeRadius * 2f, eyeHeight)
            )

            if (blink < 0.5f) {
                // Pupil with state-based movement
                val pupilOffsetX = when (state) {
                    DuqState.LISTENING, DuqState.RECORDING -> eyeRadius * 0.25f * sin(bob * PI.toFloat())
                    DuqState.PROCESSING -> sin(particleRotation * PI.toFloat() / 180f) * eyeRadius * 0.3f
                    else -> eyeRadius * 0.1f
                }
                val pupilOffsetY = when (state) {
                    DuqState.PROCESSING -> cos(particleRotation * PI.toFloat() / 180f) * eyeRadius * 0.15f
                    else -> 0f
                }

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1A1A1A),
                            Color.Black
                        )
                    ),
                    radius = eyeRadius * 0.55f,
                    center = Offset(eyeX + pupilOffsetX, eyeY + pupilOffsetY)
                )

                // Eye sparkle
                drawCircle(
                    color = Color.White,
                    radius = eyeRadius * 0.18f,
                    center = Offset(eyeX - eyeRadius * 0.15f + pupilOffsetX * 0.3f, eyeY - eyeRadius * 0.2f)
                )

                // Secondary sparkle
                drawCircle(
                    color = Color.White.copy(alpha = 0.6f),
                    radius = eyeRadius * 0.08f,
                    center = Offset(eyeX + eyeRadius * 0.2f + pupilOffsetX * 0.3f, eyeY + eyeRadius * 0.1f)
                )
            }
        }

        // === WING ===
        val wingWave = sin(bob * PI.toFloat() * 2) * 3f
        val wingPath = Path().apply {
            val wingX = centerX - duckSize * 0.12f
            val wingY = bodyY + duckSize * 0.02f + wingWave
            val wingWidth = duckSize * 0.18f
            val wingHeight = duckSize * 0.14f

            moveTo(wingX + wingWidth * 0.3f, wingY - wingHeight * 0.2f)
            quadraticBezierTo(
                wingX - wingWidth * 0.3f, wingY + wingHeight * 0.5f,
                wingX + wingWidth * 0.2f, wingY + wingHeight * 0.8f
            )
            quadraticBezierTo(
                wingX + wingWidth * 0.8f, wingY + wingHeight * 0.4f,
                wingX + wingWidth * 0.3f, wingY - wingHeight * 0.2f
            )
            close()
        }

        drawPath(
            path = wingPath,
            brush = Brush.linearGradient(
                colors = listOf(
                    DuqColors.primaryDim,
                    DuqColors.primaryDark
                ),
                start = Offset(centerX - duckSize * 0.1f, bodyY),
                end = Offset(centerX - duckSize * 0.15f, bodyY + duckSize * 0.15f)
            )
        )

        // === TAIL FEATHERS ===
        val tailPath = Path().apply {
            val tailX = centerX - duckSize * 0.35f
            val tailY = bodyY + duckSize * 0.05f

            moveTo(tailX, tailY)
            quadraticBezierTo(
                tailX - duckSize * 0.12f, tailY - duckSize * 0.08f,
                tailX - duckSize * 0.08f, tailY - duckSize * 0.12f
            )
            quadraticBezierTo(
                tailX - duckSize * 0.05f, tailY - duckSize * 0.05f,
                tailX, tailY
            )
        }

        drawPath(
            path = tailPath,
            color = DuqColors.primaryDim,
            style = Fill
        )

        // === PROCESSING ORBITAL RING ===
        if (state == DuqState.PROCESSING) {
            val orbitRadius = duckSize * 0.5f

            // Ring glow
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        DuqColors.accent.copy(alpha = 0.6f),
                        DuqColors.accent.copy(alpha = 0.1f),
                        Color.Transparent,
                        DuqColors.accent.copy(alpha = 0.1f),
                        DuqColors.accent.copy(alpha = 0.6f)
                    )
                ),
                radius = orbitRadius,
                center = Offset(centerX, centerY),
                style = Stroke(width = 3f)
            )

            // Orbiting dot
            val dotAngle = particleRotation * (PI / 180f).toFloat()
            val dotX = centerX + cos(dotAngle) * orbitRadius
            val dotY = centerY + sin(dotAngle) * orbitRadius

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White,
                        DuqColors.accent,
                        DuqColors.accent.copy(alpha = 0f)
                    )
                ),
                radius = duckSize * 0.05f,
                center = Offset(dotX, dotY)
            )
        }

        // === SOUND WAVES (Playing state) ===
        if (state == DuqState.PLAYING) {
            for (i in 0..2) {
                val wavePhase = (glowPulse + i * 0.3f) % 1f
                val waveRadius = duckSize * 0.4f + wavePhase * duckSize * 0.3f

                drawArc(
                    color = DuqColors.success.copy(alpha = (1f - wavePhase) * 0.4f),
                    startAngle = -30f,
                    sweepAngle = 60f,
                    useCenter = false,
                    topLeft = Offset(centerX + duckSize * 0.2f - waveRadius, centerY - duckSize * 0.3f - waveRadius),
                    size = Size(waveRadius * 2, waveRadius * 2),
                    style = Stroke(width = 3f - wavePhase * 2f)
                )
            }
        }
    }
}
