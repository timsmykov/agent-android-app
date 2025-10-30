package com.example.aichat.ui.chat

import android.view.MotionEvent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aichat.R
import com.example.aichat.core.audio.AudioAnalyzer
import com.example.aichat.ui.chat.ChatViewModel.UiEvent
import com.example.aichat.ui.voice.VoiceOrb
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
                AssistantHeader(
                    mode = uiState.mode,
                    onModeChange = viewModel::onModeSelected,
                    modifier = Modifier.fillMaxWidth()
                )

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

            val isVoiceMode = uiState.mode == ChatViewModel.InteractionMode.Voice

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isVoiceMode) Color(0x66111A2C) else Color(0x33131828)
                ),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isVoiceMode) 24.dp else 18.dp)
                ) {
                    if (isVoiceMode) {
                        VoiceModePanel(
                            state = uiState.voiceState,
                            frame = voiceFrame,
                            transcript = uiState.ghostText,
                            onHoldStart = viewModel::onVoiceHoldStart,
                            onHoldEnd = viewModel::onVoiceHoldEnd
                        )
                    } else {
                        ComposerBar(
                            input = uiState.input,
                            ghostText = uiState.ghostText,
                            isSending = uiState.isSending,
                            voiceState = uiState.voiceState,
                            onTextChange = viewModel::onMessageChanged,
                            onSend = viewModel::sendMessage,
                            onStartVoice = viewModel::startVoiceInput,
                            onStopVoice = viewModel::stopVoiceInput
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = uiState.mode == ChatViewModel.InteractionMode.Chat &&
                uiState.voiceState != ChatViewModel.VoiceState.Idle,
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
private fun AssistantHeader(
    mode: ChatViewModel.InteractionMode,
    onModeChange: (ChatViewModel.InteractionMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(id = R.string.app_name),
            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
        )

        ModeToggle(mode = mode, onModeChange = onModeChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeToggle(
    mode: ChatViewModel.InteractionMode,
    onModeChange: (ChatViewModel.InteractionMode) -> Unit
) {
    val items = listOf(
        ChatViewModel.InteractionMode.Chat to stringResource(id = R.string.mode_chat),
        ChatViewModel.InteractionMode.Voice to stringResource(id = R.string.mode_voice)
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0x33131828)),
        shape = RoundedCornerShape(22.dp)
    ) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            items.forEachIndexed { index, item ->
                val (value, label) = item
                SegmentedButton(
                    selected = mode == value,
                    onClick = { onModeChange(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, items.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primary,
                        activeContentColor = MaterialTheme.colorScheme.onPrimary,
                        inactiveContainerColor = Color.Transparent,
                        inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceModePanel(
    state: ChatViewModel.VoiceState,
    frame: AudioAnalyzer.AudioFrame,
    transcript: String?,
    onHoldStart: () -> Unit,
    onHoldEnd: (Boolean) -> Unit
) {
    val statusText = when (state) {
        ChatViewModel.VoiceState.Idle -> stringResource(id = R.string.voice_hold_hint)
        ChatViewModel.VoiceState.Listening -> stringResource(id = R.string.voice_release_hint)
        ChatViewModel.VoiceState.Thinking -> stringResource(id = R.string.thinking)
        ChatViewModel.VoiceState.Speaking -> stringResource(id = R.string.speaking)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(36.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF101223),
                        Color(0xFF1A213A),
                        Color(0xFF111525)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(720f, 860f)
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.12f),
                        Color.White.copy(alpha = 0.04f)
                    )
                ),
                shape = RoundedCornerShape(36.dp)
            )
            .padding(horizontal = 24.dp, vertical = 28.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = stringResource(id = R.string.voice_panel_title),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )

            VoiceOrb(
                state = state,
                frame = frame,
                modifier = Modifier.size(220.dp)
            )

            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            AnimatedVisibility(visible = !transcript.isNullOrBlank(), enter = fadeIn(), exit = fadeOut()) {
                Text(
                    text = transcript.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }

            VoiceHoldButton(
                state = state,
                frame = frame,
                onPressStart = onHoldStart,
                onPressEnd = { onHoldEnd(false) },
                onPressCancel = { onHoldEnd(true) },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun VoiceHoldButton(
    state: ChatViewModel.VoiceState,
    frame: AudioAnalyzer.AudioFrame,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    onPressCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }

    val haloTarget = (if (isPressed) 0.55f else 0.28f) + frame.amplitude * 0.35f
    val haloAlpha by animateFloatAsState(targetValue = haloTarget.coerceIn(0.25f, 0.9f), label = "voiceHoldHalo")

    val scaleTarget = when {
        isPressed -> 1.08f
        state == ChatViewModel.VoiceState.Listening || state == ChatViewModel.VoiceState.Thinking -> 1.04f
        else -> 1f
    }
    val scale by animateFloatAsState(targetValue = scaleTarget, label = "voiceHoldScale")

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(172.dp)
                .graphicsLayer { alpha = haloAlpha }
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x805E6FFF), Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )

        Box(
            modifier = Modifier
                .size(120.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            Color.White.copy(alpha = 0.22f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    ),
                    shape = CircleShape
                )
                .background(Color.Transparent)
                .pointerInteropFilter { event: MotionEvent ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            if (state == ChatViewModel.VoiceState.Thinking || state == ChatViewModel.VoiceState.Speaking) {
                                return@pointerInteropFilter true
                            }
                            isPressed = true
                            onPressStart()
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (isPressed) {
                                isPressed = false
                                onPressEnd()
                            }
                            true
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            if (isPressed) {
                                isPressed = false
                                onPressCancel()
                            }
                            true
                        }
                        else -> false
                    }
                }
        ) {
            VoiceOrb(
                state = state,
                frame = frame,
                modifier = Modifier.fillMaxSize()
            )

            Icon(
                imageVector = when (state) {
                    ChatViewModel.VoiceState.Idle -> Icons.Rounded.Mic
                    ChatViewModel.VoiceState.Listening -> Icons.Rounded.Stop
                    ChatViewModel.VoiceState.Thinking -> Icons.Rounded.Stop
                    ChatViewModel.VoiceState.Speaking -> Icons.Filled.GraphicEq
                },
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center)
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

        // Noise overlay intentionally removed; Compose can't render layer-list drawables directly.
    }
}
