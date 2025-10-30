package com.example.aichat.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.aichat.domain.model.ChatMessage
import com.example.aichat.domain.model.MessageStatus
import com.example.aichat.domain.model.Role
import com.example.aichat.domain.model.SourceLink
import com.example.aichat.ui.components.MarkdownText
import kotlinx.coroutines.launch
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

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
    val gradient = if (isUser) {
        Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.85f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            )
        )
    }
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
                    AgentReply(
                        message = message,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .padding(4.dp)
                    )
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

@Composable
private fun AgentReply(
    message: ChatMessage,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MarkdownText(text = message.text)

        message.summary?.takeIf { it.isNotBlank() }?.let { summary ->
            Surface(
                tonalElevation = 6.dp,
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Сводка",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    MarkdownText(text = summary)
                }
            }
        }

        if (message.plan.isNotEmpty()) {
            Surface(
                tonalElevation = 6.dp,
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "План",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    message.plan.forEach { item ->
                        PlanRow(title = item.title, done = item.isDone)
                    }
                }
            }
        }

        if (message.sources.isNotEmpty()) {
            Surface(
                tonalElevation = 4.dp,
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Источники",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    message.sources.forEach { source ->
                        SourceRow(source)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanRow(title: String, done: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val icon = if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked
        val tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.padding(end = 2.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SourceRow(source: SourceLink) {
    val uriHandler = LocalUriHandler.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val annotated = remember(source, primaryColor) {
        buildAnnotatedString {
            pushStyle(SpanStyle(color = primaryColor, fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline))
            append(source.title.ifBlank { source.url })
            pop()
        }
    }

    val bodySmall = MaterialTheme.typography.bodySmall

    Text(
        text = annotated,
        style = bodySmall,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable { uriHandler.openUri(source.url) }
            .padding(vertical = 4.dp),
        color = primaryColor
    )
}
