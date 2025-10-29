package com.example.aichat.ui.chat

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.aichat.domain.model.ChatMessage
import com.example.aichat.domain.model.MessageStatus
import com.example.aichat.domain.model.Role
import com.example.aichat.ui.components.MarkdownText
import kotlinx.coroutines.launch

@Composable
fun ChatList(
    messages: List<ChatMessage>,
    modifier: Modifier = Modifier,
    onRetry: (String) -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var lastMessageCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(messages.size) {
        if (messages.size > lastMessageCount) {
            scope.launch {
                listState.animateScrollToItem(messages.lastIndex.coerceAtLeast(0))
            }
        }
        lastMessageCount = messages.size
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(messages, key = { it.id }) { message ->
            MessageBubble(message = message, onRetry = onRetry)
        }
        item { Spacer(Modifier.padding(bottom = 72.dp)) }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    onRetry: (String) -> Unit
) {
    val isUser = message.role == Role.USER
    val gradient = Brush.linearGradient(
        colors = if (isUser) {
            listOf(Color(0xCC5B6CFF), Color(0xCCFF4FD8))
        } else {
            listOf(Color(0x661FFFFFFF), Color(0x33222244))
        }
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .clip(MaterialTheme.shapes.medium)
                .background(gradient)
                .padding(18.dp)
        ) {
            Column(modifier = Modifier.align(Alignment.Center)) {
                if (!isUser) {
                    Text(
                        text = message.role.name.lowercase().replaceFirstChar { it.titlecase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                if (message.role == Role.AGENT || message.role == Role.SYSTEM) {
                    Box(
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.medium)
                            .background(Color(0x66181824))
                            .padding(4.dp)
                    ) {
                        MarkdownText(text = message.text)
                    }
                } else {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                AnimatedVisibility(visible = message.status == MessageStatus.FAILED, enter = fadeIn(), exit = fadeOut()) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        Text(
                            text = "Не удалось отправить",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        ElevatedButton(
                            onClick = { onRetry(message.id) },
                            colors = ButtonDefaults.elevatedButtonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text("Повторить", color = MaterialTheme.colorScheme.onSecondary)
                        }
                    }
                }
                if (message.isGhost) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }
    }
}
