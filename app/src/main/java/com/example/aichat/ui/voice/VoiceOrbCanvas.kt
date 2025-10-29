package com.example.aichat.ui.voice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.aichat.ui.chat.ChatViewModel
import kotlin.math.min

@Composable
fun VoiceOrbCanvas(
    state: ChatViewModel.VoiceState,
    amplitude: Float,
    centroid: Float,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val diameter = kotlin.math.min(size.width, size.height)
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val modeColor = when (state) {
                ChatViewModel.VoiceState.Idle -> Color(0xFF2A2C36)
                ChatViewModel.VoiceState.Listening -> Color(0xFF5B6CFF)
                ChatViewModel.VoiceState.Thinking -> Color(0xFFFF4FD8)
                ChatViewModel.VoiceState.Speaking -> Color(0xFFFF8AD8)
            }
            val radius = diameter / 2f * (0.6f + amplitude.coerceIn(0f, 1f) * 0.35f)
            val brush = Brush.radialGradient(
                colors = listOf(modeColor.copy(alpha = 0.9f), Color.Transparent),
                center = center,
                radius = radius
            )
            drawCircle(brush = brush, radius = radius)
            drawCircle(color = Color.White.copy(alpha = 0.12f), radius = radius * (0.65f + centroid.coerceIn(0f, 1f) * 0.2f))
        }
    }
}
