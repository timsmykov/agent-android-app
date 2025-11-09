package com.example.aichat.ui.chat

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aichat.R
import com.example.aichat.core.Result
import com.example.aichat.core.audio.VoiceRecorder
import com.example.aichat.data.voice.ParakeetTranscriber
import com.example.aichat.domain.model.ChatMessage
import com.example.aichat.domain.model.MessageStatus
import com.example.aichat.domain.model.Role
import com.example.aichat.domain.model.PlanItem
import com.example.aichat.domain.model.SourceLink
import com.example.aichat.domain.model.WebhookResponse
import com.example.aichat.domain.usecase.SendMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val sendMessageUseCase: SendMessageUseCase,
    private val voiceRecorder: VoiceRecorder,
    private val parakeetTranscriber: ParakeetTranscriber,
    @ApplicationContext private val context: Context,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private fun newSessionId(): String = UUID.randomUUID().toString()
    private var sessionId: String = newSessionId()

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _messages = mutableStateListOf<ChatMessage>()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private val _hasMicrophonePermission = MutableStateFlow(false)
    val hasMicrophonePermission: StateFlow<Boolean> = _hasMicrophonePermission.asStateFlow()

    private val _microphonePermissionRequested = MutableSharedFlow<Boolean>(replay = 0)
    val microphonePermissionRequested: SharedFlow<Boolean> = _microphonePermissionRequested.asSharedFlow()

    private val _voiceFrame = MutableStateFlow(VoiceFrame())
    val voiceFrame: StateFlow<VoiceFrame> = _voiceFrame.asStateFlow()

    private var recorderSession: VoiceRecorder.Session? = null
    private var transcriptionSession: ParakeetTranscriber.Session? = null
    private var transcriptionCompletion: CompletableDeferred<Unit>? = null
    private var lastVoiceFrame = VoiceFrame()
    private val transcriptBuffer = StringBuilder()

    fun askForMicrophonePermission() {
        viewModelScope.launch {
            _microphonePermissionRequested.emit(true)
        }
    }

    fun onMicrophonePermissionResult(granted: Boolean) {
        _hasMicrophonePermission.value = granted
        if (!granted) {
            stopVoiceInput()
            updateState { copy(showPermissionRationale = true) }
        } else {
            updateState { copy(showPermissionRationale = false) }
        }
    }

    fun onMessageChanged(value: String) {
        updateState { copy(input = value) }
    }

    fun resetChat(preserveMode: Boolean = true) {
        cancelVoiceSessions()
        _messages.clear()
        sessionId = newSessionId()
        lastVoiceFrame = VoiceFrame()
        _voiceFrame.value = lastVoiceFrame
        updateState {
            copy(
                messages = emptyList(),
                input = "",
                isSending = false,
                ghostText = null,
                voiceState = VoiceState.Idle,
                voiceDraft = null,
                isVoicePreviewVisible = false,
                mode = if (preserveMode) mode else InteractionMode.Chat
            )
        }
    }

    fun sendMessage() {
        val rawInput = _uiState.value.input
        val prepared = prepareOutboundMessage(rawInput) ?: return

        val message = ChatMessage(
            text = prepared.text,
            role = prepared.role
        )
        appendMessage(message)
        updateState { copy(input = "", isSending = true) }

        viewModelScope.launch {
            val result = withContext(ioDispatcher) { sendMessageUseCase(message, sessionId) }
            when (result) {
                is Result.Success -> handleSuccess(message.id, result.data)
                is Result.Error -> handleError(message.id, result.throwable)
                Result.Loading -> Unit
            }
        }
    }

    private fun prepareOutboundMessage(raw: String): PreparedMessage? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        val commandToken = trimmed.substringBefore(' ')
        if (commandToken.equals(NOTION_COMMAND_PREFIX, ignoreCase = true)) {
            val body = trimmed.drop(commandToken.length).trim()
            val lines = body.lines()
            val title = lines.firstOrNull()?.takeIf { it.isNotBlank() }?.trim().orEmpty()
            val note = lines.drop(1).joinToString("\n").trim()
            val resolvedTitle = title.ifBlank { DEFAULT_NOTION_TITLE }

            val text = buildString {
                append(resolvedTitle)
                if (note.isNotEmpty()) {
                    append("\n")
                    append(note)
                }
            }.trim()

            return PreparedMessage(
                text = if (text.isNotEmpty()) text else resolvedTitle,
                role = Role.USER
            )
        }

        return PreparedMessage(text = trimmed)
    }

    fun retry(messageId: String) {
        val message = _messages.firstOrNull { it.id == messageId } ?: return
        updateMessage(messageId) { copy(status = MessageStatus.PENDING) }
        viewModelScope.launch {
            updateState { copy(isSending = true) }
            val result = withContext(ioDispatcher) { sendMessageUseCase(message.copy(status = MessageStatus.PENDING), sessionId) }
            when (result) {
                is Result.Success -> handleSuccess(messageId, result.data)
                is Result.Error -> handleError(messageId, result.throwable)
                Result.Loading -> Unit
            }
        }
    }

    fun startVoiceInput() {
        if (!_hasMicrophonePermission.value) {
            askForMicrophonePermission()
            return
        }
        if (recorderSession != null) return

        updateState {
            copy(
                ghostText = null,
                voiceDraft = null,
                isVoicePreviewVisible = false,
                voiceState = VoiceState.Listening
            )
        }
        lastVoiceFrame = VoiceFrame()
        _voiceFrame.value = lastVoiceFrame
        transcriptBuffer.clear()
        transcriptionCompletion = CompletableDeferred()

        val transcriber = parakeetTranscriber.start(
            scope = viewModelScope,
            onPartial = { partial ->
                val prefix = transcriptBuffer.toString().trim()
                val addition = partial.trim()
                val display = listOf(prefix, addition)
                    .filter { it.isNotBlank() }
                    .joinToString(separator = " ")
                    .trim()
                updateState { copy(ghostText = display.takeUnless { it.isBlank() }) }
            },
            onFinal = { final ->
                handleFinalTranscript(final)
            },
            onError = { throwable ->
                handleVoiceTranscriptionError(throwable)
            }
        )
        transcriptionSession = transcriber

        val session = voiceRecorder.start(
            scope = viewModelScope,
            onChunk = { chunk ->
                transcriber.offer(chunk.data)
                updateVoiceFrame(chunk.amplitude, chunk.centroid)
            },
            onError = { throwable ->
                handleVoiceTranscriptionError(throwable)
            }
        )
        recorderSession = session
        if (session === VoiceRecorder.Session.EMPTY) {
            recorderSession = null
            transcriptionSession?.cancel()
            transcriptionSession = null
            cancelVoiceSessions()
            emitToast(R.string.voice_error)
        }
    }

    fun stopVoiceInput() {
        cancelVoiceInput()
    }

    fun cancelVoiceInput() {
        cancelVoiceSessions()
    }

    fun finishVoiceInput() {
        if (recorderSession == null && transcriptionSession == null) {
            return
        }
        setVoiceState(VoiceState.Thinking)
        lastVoiceFrame = VoiceFrame()
        _voiceFrame.value = lastVoiceFrame
        viewModelScope.launch(ioDispatcher) {
            try {
                recorderSession?.stop()
            } finally {
                recorderSession = null
            }

            val activeTranscriber = transcriptionSession
            if (activeTranscriber != null) {
                runCatching { activeTranscriber.finish() }
                transcriptionSession = null
            }

            val completion = transcriptionCompletion
            completion?.let {
                withTimeoutOrNull(2_500L) {
                    runCatching { it.await() }.getOrNull()
                }
            }
            transcriptionCompletion = null

            withContext(Dispatchers.Main) {
                if (!_uiState.value.isVoicePreviewVisible) {
                    showTranscriptionResult()
                }
            }
        }
    }

    fun onVoiceDraftChange(value: String) {
        updateState { copy(voiceDraft = value) }
    }

    fun onVoiceDraftDismiss() {
        transcriptBuffer.clear()
        updateState {
            copy(
                ghostText = null,
                voiceState = VoiceState.Idle,
                voiceDraft = null,
                isVoicePreviewVisible = false
            )
        }
        lastVoiceFrame = VoiceFrame()
        _voiceFrame.value = lastVoiceFrame
    }

    fun onVoiceDraftConfirm() {
        val draft = _uiState.value.voiceDraft?.trim().orEmpty()
        if (draft.isEmpty()) {
            onVoiceDraftDismiss()
            return
        }
        updateState {
            copy(
                input = draft,
                ghostText = null,
                voiceState = VoiceState.Thinking,
                voiceDraft = null,
                isVoicePreviewVisible = false
            )
        }
        sendMessage()
    }

    fun onVoiceHoldStart() {
        if (_uiState.value.mode == InteractionMode.Voice) {
            resetChat()
        }
        startVoiceInput()
    }

    fun onVoiceHoldEnd(cancelled: Boolean) {
        if (cancelled) {
            cancelVoiceInput()
        } else {
            finishVoiceInput()
        }
    }

    fun onModeSelected(mode: InteractionMode) {
        if (_uiState.value.mode == mode) return
        updateState { copy(mode = mode) }
        when (mode) {
            InteractionMode.Chat -> cancelVoiceInput()
            InteractionMode.Voice -> resetChat()
        }
    }

    private fun handleSuccess(messageId: String, response: WebhookResponse) {
        updateMessage(messageId) { copy(status = MessageStatus.SENT) }
        val resolvedText = response.resolveText() ?: "HTTP ${response.httpCode}"
        val workflow = response.primaryResult
        val plan = workflow?.plan.orEmpty()
            .mapNotNull { item ->
                val title = item.title.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                PlanItem(
                    title = title,
                    isDone = item.done == true
                )
            }
        val sources = workflow?.sources.orEmpty()
            .mapNotNull { source ->
                val url = source.url.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val title = source.title.ifBlank { url }
                SourceLink(
                    title = title,
                    url = url
                )
            }

        val agentMessage = ChatMessage(
            role = Role.AGENT,
            text = resolvedText,
            status = MessageStatus.RECEIVED,
            summary = workflow?.summary,
            plan = plan,
            sources = sources
        )
        appendMessage(agentMessage)
        updateState { copy(isSending = false, voiceState = VoiceState.Idle) }
    }

    private fun handleError(messageId: String, throwable: Throwable) {
        Timber.e(throwable, "Message send failed")
        android.util.Log.e("Webhook", "Message send failed", throwable)
        updateMessage(messageId) { copy(status = MessageStatus.FAILED) }
        updateState { copy(isSending = false, voiceState = VoiceState.Idle) }
        val readable = throwable.message ?: "unknown error"
        emitToast(R.string.send_failed)
        viewModelScope.launch {
            _events.emit(UiEvent.Toast("Webhook error: $readable"))
        }
    }

    private fun appendMessage(message: ChatMessage) {
        _messages.add(message)
        updateState { copy(messages = _messages.toList()) }
    }

    private fun updateMessage(id: String, transform: ChatMessage.() -> ChatMessage) {
        val index = _messages.indexOfFirst { it.id == id }
        if (index == -1) return
        _messages[index] = _messages[index].transform()
        updateState { copy(messages = _messages.toList()) }
    }

    private fun updateState(reducer: ChatUiState.() -> ChatUiState) {
        _uiState.value = _uiState.value.reducer()
    }

    private fun cancelVoiceSessions() {
        recorderSession?.cancel()
        recorderSession = null
        transcriptionSession?.cancel()
        transcriptionSession = null
        transcriptionCompletion?.cancel()
        transcriptionCompletion = null
        transcriptBuffer.clear()
        lastVoiceFrame = VoiceFrame()
        _voiceFrame.value = lastVoiceFrame
        updateState {
            copy(
                ghostText = null,
                voiceState = VoiceState.Idle,
                voiceDraft = null,
                isVoicePreviewVisible = false
            )
        }
    }

    private fun handleVoiceTranscriptionError(throwable: Throwable) {
        Timber.e(throwable, "Voice transcription failed")
        cancelVoiceSessions()
        emitToast(R.string.voice_error)
    }

    private fun handleFinalTranscript(text: String?) {
        val cleaned = text?.trim().orEmpty()
        if (cleaned.isNotBlank()) {
            val current = transcriptBuffer.toString()
            when {
                current.isBlank() -> {
                    transcriptBuffer.clear()
                    transcriptBuffer.append(cleaned)
                }
                cleaned.startsWith(current) -> {
                    transcriptBuffer.clear()
                    transcriptBuffer.append(cleaned)
                }
                current.startsWith(cleaned) -> {
                    // ignore shorter duplicate chunk
                }
                else -> {
                    if (!current.endsWith(cleaned)) {
                        if (transcriptBuffer.isNotEmpty()) transcriptBuffer.append(' ')
                        transcriptBuffer.append(cleaned)
                    }
                }
            }
        }
        transcriptionCompletion?.let { if (!it.isCompleted) it.complete(Unit) }
        val combined = transcriptBuffer.toString().trim()
        if (recorderSession != null) {
            updateState { copy(ghostText = combined.takeUnless { it.isBlank() }) }
        } else {
            showTranscriptionResult()
        }
    }

    private fun updateVoiceFrame(amplitude: Float, centroid: Float) {
        val smoothedAmplitude = (lastVoiceFrame.amplitude * 0.6f + amplitude * 0.4f).coerceIn(0f, 1f)
        val smoothedCentroid = (lastVoiceFrame.centroid * 0.7f + centroid * 0.3f).coerceIn(0f, 1f)
        lastVoiceFrame = VoiceFrame(smoothedAmplitude, smoothedCentroid)
        _voiceFrame.value = lastVoiceFrame
    }

    private fun setVoiceState(state: VoiceState) {
        updateState { copy(voiceState = state) }
    }

    private fun showTranscriptionResult() {
        val combined = transcriptBuffer.toString().trim()
        lastVoiceFrame = VoiceFrame()
        _voiceFrame.value = lastVoiceFrame
        if (combined.isNotBlank()) {
            updateState {
                copy(
                    ghostText = null,
                    voiceState = VoiceState.Idle,
                    voiceDraft = combined,
                    isVoicePreviewVisible = true
                )
            }
        } else {
            updateState { copy(ghostText = null, voiceState = VoiceState.Idle) }
        }
    }

    private fun emitToast(@StringRes res: Int) {
        viewModelScope.launch {
            _events.emit(UiEvent.Toast(context.getString(res)))
        }
    }

    override fun onCleared() {
        super.onCleared()
        cancelVoiceSessions()
    }

    data class ChatUiState(
        val messages: List<ChatMessage> = emptyList(),
        val input: String = "",
        val isSending: Boolean = false,
        val ghostText: String? = null,
        val showPermissionRationale: Boolean = false,
        val voiceState: VoiceState = VoiceState.Idle,
        val voiceDraft: String? = null,
        val isVoicePreviewVisible: Boolean = false,
        val mode: InteractionMode = InteractionMode.Chat
    )

    sealed class UiEvent {
        data class Toast(val message: String) : UiEvent()
    }

    enum class VoiceState { Idle, Listening, Thinking }
    enum class InteractionMode { Chat, Voice }

    data class VoiceFrame(
        val amplitude: Float = 0f,
        val centroid: Float = 0f
    )

    private data class PreparedMessage(
        val text: String,
        val role: Role = Role.USER
    )

    private companion object {
        private const val NOTION_COMMAND_PREFIX = "/notion"
        private const val DEFAULT_NOTION_TITLE = "Новая задача"
    }
}
