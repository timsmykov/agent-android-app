package com.example.aichat.ui.chat

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AuroraBackdrop(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Column(modifier = Modifier.weight(1f, fill = true)) {
                AssistantHeader(modifier = Modifier.fillMaxWidth())

                Spacer(modifier = Modifier.height(8.dp))

                ChatList(
                    messages = uiState.messages,
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 12.dp),
                    onRetry = viewModel::retry
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0x33131828)
                ),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
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

                    VoiceActionRow(
                        state = uiState.voiceState,
                        onStart = viewModel::startVoiceInput,
                        onStop = viewModel::stopVoiceInput
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = uiState.voiceState != ChatViewModel.VoiceState.Idle,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 96.dp)
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
private fun VoiceActionRow(
    state: ChatViewModel.VoiceState,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    val isActive = state != ChatViewModel.VoiceState.Idle
    val gradient = if (isActive) {
        Brush.radialGradient(
            colors = listOf(MaterialTheme.colorScheme.secondary, Color(0x00FF6FDB)),
            radius = 220f
        )
    } else {
        Brush.linearGradient(
            colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)
        )
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        val action = when (state) {
            ChatViewModel.VoiceState.Listening, ChatViewModel.VoiceState.Thinking -> onStop
            ChatViewModel.VoiceState.Speaking -> onStop
            ChatViewModel.VoiceState.Idle -> onStart
        }

        Box(
            modifier = Modifier
                .size(96.dp)
                .shadow(elevation = if (isActive) 20.dp else 12.dp, shape = CircleShape, clip = false)
                .clip(CircleShape)
                .background(gradient)
                .padding(4.dp)
                .clip(CircleShape)
                .background(Color(0xFF0C0E1A))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { action() }
        ) {
            val icon = when (state) {
                ChatViewModel.VoiceState.Listening, ChatViewModel.VoiceState.Thinking -> Icons.Rounded.Stop
                ChatViewModel.VoiceState.Speaking -> Icons.Filled.GraphicEq
                ChatViewModel.VoiceState.Idle -> Icons.Rounded.Mic
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
                    .padding(6.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = when (state) {
                ChatViewModel.VoiceState.Listening -> stringResource(id = R.string.listening)
                ChatViewModel.VoiceState.Thinking -> stringResource(id = R.string.thinking)
                ChatViewModel.VoiceState.Speaking -> stringResource(id = R.string.speaking)
                ChatViewModel.VoiceState.Idle -> stringResource(id = R.string.voice_mode_idle)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AssistantHeader(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(id = R.string.app_name),
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0x3319243E)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(
                    text = stringResource(id = R.string.voice_mode_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(id = R.string.voice_mode_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
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
            .background(Color(0xAA05060B))
            .padding(32.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF15192B))
                .padding(24.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x33243958))
                ) {
                    Text(text = stringResource(id = R.string.dismiss))
                }
                Button(
                    onClick = onGrant,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(id = R.string.grant))
                }
            }
        }
    }
}

@Composable
private fun AuroraBackdrop(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF05060B),
                            Color(0xFF0A0D1F),
                            Color(0xFF05060B)
                        )
                    )
                )
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x66405DFF), Color.Transparent)
                ),
                radius = width * 0.6f,
                center = Offset(width * 0.2f, height * 0.15f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x44FF6FDB), Color.Transparent)
                ),
                radius = width * 0.7f,
                center = Offset(width * 0.8f, height * 0.25f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x3342E4FF), Color.Transparent)
                ),
                radius = height * 0.55f,
                center = Offset(width * 0.5f, height * 0.85f)
            )
        }

        Image(
            painter = painterResource(id = R.drawable.noise_background),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize(),
            alpha = 0.08f
        )
    }
}
