package com.example.aichat.ui.chat

import android.view.MotionEvent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.aichat.R
import com.example.aichat.ui.chat.ChatViewModel.UiEvent
import com.example.aichat.ui.chat.ChatViewModel.VoiceFrame
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

        val isVoiceMode = uiState.mode == ChatViewModel.InteractionMode.Voice

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            AssistantHeader(
                mode = uiState.mode,
                onModeChange = viewModel::onModeSelected,
                onNewChat = { viewModel.resetChat() },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (isVoiceMode) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true),
                    contentAlignment = Alignment.Center
                ) {
                    VoiceModePanel(
                        state = uiState.voiceState,
                        frame = voiceFrame,
                        onHoldStart = viewModel::onVoiceHoldStart,
                        onHoldEnd = viewModel::onVoiceHoldEnd
                    )
                }
            } else {
                ChatList(
                    messages = uiState.messages,
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 12.dp),
                    onRetry = viewModel::retry
                )

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

        val voiceDraft = uiState.voiceDraft
        if (uiState.isVoicePreviewVisible && voiceDraft != null) {
            VoiceTranscriptionDialog(
                value = voiceDraft,
                onValueChange = viewModel::onVoiceDraftChange,
                onDismiss = viewModel::onVoiceDraftDismiss,
                onConfirm = viewModel::onVoiceDraftConfirm
            )
        }
    }
}
@Composable
private fun AssistantHeader(
    mode: ChatViewModel.InteractionMode,
    onModeChange: (ChatViewModel.InteractionMode) -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
            )

            if (mode == ChatViewModel.InteractionMode.Chat) {
                TextButton(onClick = onNewChat) {
                    Text(text = stringResource(id = R.string.new_chat))
                }
            }
        }

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
private fun VoiceTranscriptionDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(id = R.string.voice_preview_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(id = R.string.voice_preview_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = false,
                    keyboardOptions = KeyboardOptions.Default.copy(
                        capitalization = KeyboardCapitalization.Sentences
                    )
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(text = stringResource(id = R.string.send))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.cancel))
            }
        }
    )
}

@Composable
private fun VoiceModePanel(
    state: ChatViewModel.VoiceState,
    frame: VoiceFrame,
    onHoldStart: () -> Unit,
    onHoldEnd: (Boolean) -> Unit
) {
    var isPressing by remember { mutableStateOf(false) }
    val hintAlpha by animateFloatAsState(
        targetValue = if (isPressing) 0f else 0.6f,
        animationSpec = tween(
            durationMillis = 220,
            easing = FastOutSlowInEasing
        ),
        label = "voiceHintAlpha"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        VoiceHoldButton(
            state = state,
            frame = frame,
            onPressStart = onHoldStart,
            onPressEnd = { onHoldEnd(false) },
            onPressCancel = { onHoldEnd(true) },
            onPressStateChange = { isPressing = it },
            modifier = Modifier.size(240.dp)
        )

        val hint = when (state) {
            ChatViewModel.VoiceState.Idle -> stringResource(id = R.string.voice_hold_hint)
            ChatViewModel.VoiceState.Listening -> stringResource(id = R.string.voice_release_hint)
            ChatViewModel.VoiceState.Thinking -> stringResource(id = R.string.thinking)
        }

        if (hintAlpha > 0.01f) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(hintAlpha)
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun VoiceHoldButton(
    state: ChatViewModel.VoiceState,
    frame: VoiceFrame,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
    onPressCancel: () -> Unit,
    onPressStateChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }

    val scaleTarget = when {
        isPressed -> 1.08f
        state == ChatViewModel.VoiceState.Listening || state == ChatViewModel.VoiceState.Thinking -> 1.04f
        else -> 1f
    }
    val scale by animateFloatAsState(targetValue = scaleTarget, label = "voiceHoldScale")

    val displayState = when {
        isPressed -> ChatViewModel.VoiceState.Listening
        else -> state
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(220.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .border(width = 1.dp, color = Color.White.copy(alpha = 0.16f), shape = CircleShape)
                .background(Color.Transparent)
                .pointerInteropFilter { event: MotionEvent ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            if (state == ChatViewModel.VoiceState.Thinking) {
                                return@pointerInteropFilter true
                            }
                            isPressed = true
                            onPressStateChange(true)
                            onPressStart()
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (isPressed) {
                                isPressed = false
                                onPressStateChange(false)
                                onPressEnd()
                            }
                            true
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            if (isPressed) {
                                isPressed = false
                                onPressStateChange(false)
                                onPressCancel()
                            }
                            true
                        }
                        else -> false
                    }
                }
        ) {
            VoiceOrb(
                state = displayState,
                frame = frame,
                modifier = Modifier.fillMaxSize()
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
