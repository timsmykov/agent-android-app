package com.example.aichat.ui.voice

import android.opengl.GLSurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.aichat.core.audio.AudioAnalyzer
import com.example.aichat.ui.chat.ChatViewModel

@Composable
fun VoiceOverlay(
    state: ChatViewModel.VoiceState,
    frame: AudioAnalyzer.AudioFrame,
    ghostText: String?
) {
    val context = LocalContext.current
    var glesAvailable by remember { mutableStateOf(true) }

    val orbState = remember { VoiceOrbState() }
    LaunchedEffect(frame) {
        orbState.update(frame.amplitude, frame.centroid, state)
    }

    Surface(
        tonalElevation = 12.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        color = Color(0xAA11131C),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .background(Color.Transparent)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (glesAvailable) {
                AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
                    AndroidView(
                        factory = {
                            VoiceOrbGLView(context).apply {
                                val renderer = VoiceOrbRenderer(context, orbState)
                                attachRenderer(renderer)
                                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                            }
                        },
                        modifier = Modifier.size(180.dp),
                        update = { view ->
                            view.updateState(orbState)
                            view.setOnRendererError { glesAvailable = false }
                        }
                    )
                }
            } else {
                VoiceOrbCanvas(
                    state = state,
                    amplitude = frame.amplitude,
                    centroid = frame.centroid,
                    modifier = Modifier.size(180.dp)
                )
            }

            Text(
                text = when (state) {
                    ChatViewModel.VoiceState.Listening -> "Слушаю…"
                    ChatViewModel.VoiceState.Thinking -> "Обрабатываю…"
                    ChatViewModel.VoiceState.Speaking -> "Озвучиваю ответ…"
                    ChatViewModel.VoiceState.Idle -> ghostText ?: ""
                },
                style = MaterialTheme.typography.bodyLarge,
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
