package com.example.aichat.ui.chat

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.aichat.R
import com.example.aichat.ui.chat.ChatViewModel.UiEvent
import com.example.aichat.ui.voice.VoiceOverlay

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val voiceFrame by viewModel.voiceFrame.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            if (event is UiEvent.Toast) {
                Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val showRationale = uiState.showPermissionRationale

    val gradient = Brush.verticalGradient(
        listOf(
            Color(0xFF0E0F12),
            Color(0xFF131525),
            Color(0xFF0E0F12)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp, bottom = 16.dp),
                color = Color.Transparent
            ) {
                Text(
                    text = "AI Chat Voice",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            ChatList(
                messages = uiState.messages,
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = 12.dp),
                onRetry = viewModel::retry
            )

            ComposerBar(
                input = uiState.input,
                ghostText = uiState.ghostText,
                isSending = uiState.isSending,
                voiceState = uiState.voiceState,
                onTextChange = viewModel::onMessageChanged,
                onSend = viewModel::sendMessage,
                onStartVoice = viewModel::startVoiceInput,
                onStopVoice = viewModel::stopVoiceInput,
                onCommandInsert = viewModel::insertCommand
            )
        }

        AnimatedVisibility(
            visible = uiState.voiceState != ChatViewModel.VoiceState.Idle,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            VoiceOverlay(
                state = uiState.voiceState,
                frame = voiceFrame,
                ghostText = uiState.ghostText
            )
        }

        if (showRationale) {
            PermissionRationale(
                message = context.getString(R.string.voice_permission_rationale),
                onGrant = onRequestPermission,
                onDismiss = {
                    viewModel.onMicrophonePermissionResult(false)
                }
            )
        }
    }
}

@Composable
private fun PermissionRationale(
    message: String,
    onGrant: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .background(Color(0xFF1C1F2A), RoundedCornerShape(20.dp))
                .padding(24.dp)
        ) {
            Text(text = message, color = Color.White, modifier = Modifier.padding(bottom = 16.dp))
            RowButtons(onGrant = onGrant, onDismiss = onDismiss)
        }
    }
}

@Composable
private fun RowButtons(onGrant: () -> Unit, onDismiss: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onDismiss, modifier = Modifier.weight(1f)) {
            Text(text = stringResource(id = R.string.dismiss))
        }
        Button(onClick = onGrant, modifier = Modifier.weight(1f)) {
            Text(text = stringResource(id = R.string.grant))
        }
    }
}
