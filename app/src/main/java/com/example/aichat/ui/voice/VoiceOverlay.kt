package com.example.aichat.ui.voice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.aichat.R
import com.example.aichat.ui.chat.ChatViewModel
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun VoiceOverlay(
    state: ChatViewModel.VoiceState,
    frame: ChatViewModel.VoiceFrame,
    ghostText: String?
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        tonalElevation = 18.dp,
        shape = RoundedCornerShape(32.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0x3315192B),
                            Color(0x33292954),
                            Color(0x33111526)
                        )
                    )
                )
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            VoiceOrb(
                state = state,
                frame = frame,
                modifier = Modifier.size(180.dp)
            )

            Text(
                text = when (state) {
                    ChatViewModel.VoiceState.Listening -> stringResource(id = R.string.listening)
                    ChatViewModel.VoiceState.Thinking -> stringResource(id = R.string.thinking)
                    ChatViewModel.VoiceState.Idle -> ghostText ?: ""
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            AnimatedVisibility(visible = !ghostText.isNullOrBlank(), enter = fadeIn(), exit = fadeOut()) {
                Text(
                    text = ghostText.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun VoiceOrb(
    state: ChatViewModel.VoiceState,
    frame: ChatViewModel.VoiceFrame,
    modifier: Modifier = Modifier
) {
    val amplitudeTarget = frame.amplitude.coerceIn(0f, 1f)
    val centroidTarget = frame.centroid.coerceIn(0f, 1f)
    val amplitude by animateFloatAsState(
        targetValue = amplitudeTarget,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "voiceOrbAmplitude"
    )
    val centroid by animateFloatAsState(
        targetValue = centroidTarget,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "voiceOrbCentroid"
    )
    val infinite = rememberInfiniteTransition(label = "voiceOrb")
    val ripple by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "voiceOrbRipple"
    )
    val breath by infinite.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "voiceOrbBreath"
    )
    val wavePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "voiceOrbWave"
    )

    val baseScale = when (state) {
        ChatViewModel.VoiceState.Idle -> 0.9f
        ChatViewModel.VoiceState.Listening -> 1.04f
        ChatViewModel.VoiceState.Thinking -> 1.0f
    }
    val scale = baseScale * (1f + amplitude * 0.18f) * breath

    val basePalette = when (state) {
        ChatViewModel.VoiceState.Idle -> listOf(
            Color(0xFF2F3253),
            Color(0xFF1F2141),
            Color(0xFF151728)
        )
        ChatViewModel.VoiceState.Listening -> listOf(
            Color(0xFF485BFF),
            Color(0xFF3138FF),
            Color(0xFF181C36)
        )
        ChatViewModel.VoiceState.Thinking -> listOf(
            Color(0xFF8A4BFF),
            Color(0xFF5C2FFF),
            Color(0xFF231A40)
        )
    }
    val accentColor = when (state) {
        ChatViewModel.VoiceState.Idle -> Color(0xFF5B6CFF)
        ChatViewModel.VoiceState.Listening -> Color(0xFF85A1FF)
        ChatViewModel.VoiceState.Thinking -> Color(0xFFFF8FF0)
    }
    val gradientColors = listOf(0.6f, 0.4f, 0.2f).mapIndexed { index, factor ->
        val base = basePalette[index]
        lerp(base, accentColor, (factor * amplitude + 0.1f).coerceIn(0f, 1f))
    }

    Canvas(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    ) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val orbPath = Path().apply {
            addOval(
                Rect(
                    left = center.x - radius,
                    top = center.y - radius,
                    right = center.x + radius,
                    bottom = center.y + radius
                )
            )
        }

        drawCircle(
            brush = Brush.radialGradient(
                colors = gradientColors,
                center = center,
                radius = radius * 1.3f
            ),
            radius = radius,
            center = center
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.45f + amplitude * 0.25f),
                    Color.Transparent
                ),
                center = center.copy(y = center.y - radius * (0.35f + centroid * 0.25f)),
                radius = radius * (1.12f + amplitude * 0.38f)
            )
        )

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.22f + amplitude * 0.32f),
                    Color.Transparent
                ),
                center = center,
                radius = radius * (1.55f + amplitude * 0.45f)
            )
        )

        val supportsWave = state != ChatViewModel.VoiceState.Idle || amplitude > 0.08f
        if (supportsWave) {
            val waveHeight = radius * (0.1f + amplitude * 0.18f)
            val waveBaseline = center.y + radius * (0.18f - amplitude * 0.25f)
            val waveLength = radius * 1.4f
            val step = radius / 16f
            val startX = center.x - radius * 1.4f
            val endX = center.x + radius * 1.4f
            val wavePath = Path().apply {
                moveTo(startX, size.height)
                var x = startX
                while (x <= endX) {
                    val progress = (x - startX) / waveLength
                    val angle = (progress + wavePhase) * 2f * PI.toFloat()
                    val y = waveBaseline + sin(angle) * waveHeight
                    lineTo(x, y)
                    x += step
                }
                lineTo(endX, size.height)
                close()
            }
            clipPath(orbPath, clipOp = ClipOp.Intersect) {
                drawPath(
                    path = wavePath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.55f + amplitude * 0.25f),
                            accentColor.copy(alpha = 0.0f)
                        ),
                        startY = waveBaseline - waveHeight * 2f,
                        endY = waveBaseline + waveHeight * 3f
                    )
                )
            }
        }

        val rippleAlpha = when (state) {
            ChatViewModel.VoiceState.Listening -> (1f - ripple) * (0.25f + amplitude * 0.3f)
            else -> 0f
        }
        if (rippleAlpha > 0.01f) {
            drawCircle(
                color = Color.White.copy(alpha = rippleAlpha),
                radius = radius * (1.25f + ripple * 0.7f + amplitude * 0.25f),
                center = center,
                style = Stroke(width = radius * 0.045f)
            )
        }
    }
}
