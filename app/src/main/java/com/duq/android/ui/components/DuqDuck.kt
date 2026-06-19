package com.duq.android.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.duq.android.DuqState
import com.duq.android.R

/**
 * DuqDuck — каноническая резиновая утка (классическая ванная игрушка).
 *
 * Сам силуэт — векторный asset [R.drawable.ic_rubber_duck] (НЕ рисуем от руки Canvas:
 * пропорции выверены, цвета каноничные). Здесь только живые Compose-анимации поверх
 * вектора: покачивание (bob), лёгкий наклон головы и дыхание-пульс. Скорость/амплитуда
 * зависят от [state] — смена состояния (запись/обработка/проигрывание) заметна визуально.
 */
@Composable
fun DuqDuck(
    state: DuqState?,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "duck")

    // Покачивание на воде — быстрее при активности.
    val bobMs = when (state) {
        DuqState.LISTENING, DuqState.RECORDING -> 480
        DuqState.PROCESSING -> 300
        DuqState.PLAYING -> 600
        else -> 1500
    }
    val bob by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(bobMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bob"
    )

    // Лёгкий наклон — сильнее и быстрее, когда слушает/пишет.
    val tiltDeg = when (state) {
        DuqState.LISTENING, DuqState.RECORDING -> 7f
        DuqState.PROCESSING -> 5f
        else -> 3f
    }
    val tiltMs = if (state == DuqState.LISTENING || state == DuqState.RECORDING) 420 else 2000
    val tilt by transition.animateFloat(
        initialValue = -tiltDeg,
        targetValue = tiltDeg,
        animationSpec = infiniteRepeatable(
            animation = tween(tiltMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tilt"
    )

    // Дыхание/пульс — заметнее при обработке и проигрывании.
    val pulseAmp = when (state) {
        DuqState.PROCESSING, DuqState.PLAYING -> 0.06f
        DuqState.LISTENING, DuqState.RECORDING -> 0.04f
        else -> 0.02f
    }
    val pulseMs = when (state) {
        DuqState.PROCESSING -> 380
        DuqState.PLAYING -> 520
        DuqState.LISTENING, DuqState.RECORDING -> 440
        else -> 2200
    }
    val pulse by transition.animateFloat(
        initialValue = 1f - pulseAmp,
        targetValue = 1f + pulseAmp,
        animationSpec = infiniteRepeatable(
            animation = tween(pulseMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.ic_rubber_duck),
            contentDescription = "DUQ",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .scale(pulse)
                .graphicsLayer {
                    translationY = bob * size.height * 0.035f
                    rotationZ = tilt
                    // Точка вращения — у головы (правее/выше центра), чтобы наклон
                    // читался как кивок, а не вращение всей утки.
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.62f, 0.38f)
                }
        )
    }
}
