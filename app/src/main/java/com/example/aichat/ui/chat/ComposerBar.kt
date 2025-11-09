package com.example.aichat.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.aichat.R
import com.example.aichat.ui.chat.ChatViewModel.VoiceState

@Composable
fun ComposerBar(
    input: String,
    ghostText: String?,
    isSending: Boolean,
    voiceState: VoiceState,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStartVoice: () -> Unit,
    onStopVoice: () -> Unit
) {
    val background = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        val canSend = input.isNotBlank() && !isSending
        OutlinedTextField(
            value = input,
            onValueChange = onTextChange,
            placeholder = { Text(text = stringResource(id = R.string.type_message), color = MaterialTheme.colorScheme.onSurfaceVariant) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(
                onSend = { if (canSend) onSend() }
            ),
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        if (voiceState == VoiceState.Listening || voiceState == VoiceState.Thinking) onStopVoice() else onStartVoice()
                    }) {
                        Icon(
                            imageVector = Icons.Default.KeyboardVoice,
                            contentDescription = null,
                            tint = when (voiceState) {
                                VoiceState.Listening -> MaterialTheme.colorScheme.secondary
                                VoiceState.Thinking -> MaterialTheme.colorScheme.primary
                                VoiceState.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    IconButton(onClick = { if (canSend) onSend() }, enabled = canSend) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            tint = if (canSend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        )

        AnimatedVisibility(visible = !ghostText.isNullOrBlank(), enter = fadeIn(), exit = fadeOut()) {
            Text(
                text = ghostText.orEmpty(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}
