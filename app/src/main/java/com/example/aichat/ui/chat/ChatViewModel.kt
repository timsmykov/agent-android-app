package com.example.aichat.ui.chat

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.annotation.StringRes
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aichat.R
import com.example.aichat.core.Result
import com.example.aichat.core.audio.VoiceActivityDetector
import com.example.aichat.domain.model.ChatMessage
import com.example.aichat.domain.model.MessageStatus
import com.example.aichat.domain.model.Role
import com.example.aichat.domain.model.PlanItem
import com.example.aichat.domain.model.SourceLink
import com.example.aichat.domain.model.WebhookResponse
import com.example.aichat.domain.usecase.SendMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val sendMessageUseCase: SendMessageUseCase,
    private val vad: VoiceActivityDetector,
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

    private var speechRecognizer: SpeechRecognizer? = null

    private var voiceBuffer = StringBuilder()
    private var voiceSubmitted = false
    private var lastVoiceFrame = VoiceFrame()

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
        speechRecognizer?.cancel()
        voiceBuffer.clear()
        voiceSubmitted = false
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
            role = prepared.role,
            metadata = prepared.metadata,
            tags = prepared.tags
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

            val metadata = mutableMapOf(
                "command" to "notion.add",
                "notion.title" to resolvedTitle
            )
            if (note.isNotEmpty()) {
                metadata["notion.note"] = note
            }
            if (body.isNotEmpty()) {
                metadata["notion.raw"] = body
            }

            return PreparedMessage(
                text = if (body.isNotEmpty()) body else resolvedTitle,
                metadata = metadata,
                tags = listOf("notion", "task"),
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
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            emitToast(R.string.voice_not_supported)
            return
        }
        if (!_hasMicrophonePermission.value) {
            askForMicrophonePermission()
            return
        }

        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        voiceBuffer.clear()
                        vad.reset()
                        lastVoiceFrame = VoiceFrame()
                        _voiceFrame.value = lastVoiceFrame
                        setVoiceState(VoiceState.Listening)
                    }

                    override fun onBeginningOfSpeech() {
                        setVoiceState(VoiceState.Listening)
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        val amplitude = ((rmsdB + 2f) / 10f).coerceIn(0f, 1f)
                        val frame = VoiceFrame(
                            amplitude = (lastVoiceFrame.amplitude * 0.6f + amplitude * 0.4f).coerceIn(0f, 1f),
                            centroid = (lastVoiceFrame.centroid * 0.7f + amplitude * 0.3f).coerceIn(0f, 1f)
                        )
                        lastVoiceFrame = frame
                        _voiceFrame.value = frame

                        val detection = vad.evaluate(amplitude, SystemClock.elapsedRealtime())
                        if (detection.started) {
                            setVoiceState(VoiceState.Listening)
                        }
                        if (detection.ended) {
                            setVoiceState(VoiceState.Thinking)
                            submitVoiceMessage()
                        }
                    }

                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() = Unit

                    override fun onError(error: Int) {
                        Timber.w("Speech error $error")
                        emitToast(R.string.voice_error)
                        stopVoiceInput()
                    }

                    override fun onResults(results: Bundle?) {
                        val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                        if (!text.isNullOrEmpty()) {
                            voiceBuffer.clear()
                            voiceBuffer.append(text)
                            submitVoiceMessage()
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                        voiceBuffer.clear()
                        voiceBuffer.append(text)
                        updateState { copy(ghostText = text) }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
            }
        }

        val targetLocale = Locale("ru", "RU")
        val localeTag = targetLocale.toLanguageTag()
        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeTag)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        voiceBuffer.clear()
        voiceSubmitted = false
        vad.reset()
        lastVoiceFrame = VoiceFrame()
        _voiceFrame.value = lastVoiceFrame
        speechRecognizer?.startListening(recognizerIntent)
        setVoiceState(VoiceState.Listening)
    }

    fun stopVoiceInput() {
        cancelVoiceInput()
    }

    fun cancelVoiceInput() {
        speechRecognizer?.stopListening()
        speechRecognizer?.cancel()
        vad.reset()
        voiceBuffer.clear()
        voiceSubmitted = true
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

    fun finishVoiceInput() {
        if (voiceSubmitted && voiceBuffer.isEmpty()) {
            updateState { copy(ghostText = null, voiceState = VoiceState.Idle) }
            return
        }
        submitVoiceMessage(force = true)
    }

    fun onVoiceDraftChange(value: String) {
        updateState { copy(voiceDraft = value) }
    }

    fun onVoiceDraftDismiss() {
        voiceBuffer.clear()
        voiceSubmitted = true
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

    fun onVoiceDraftConfirm() {
        val draft = _uiState.value.voiceDraft?.trim().orEmpty()
        if (draft.isEmpty()) {
            onVoiceDraftDismiss()
            return
        }
        voiceSubmitted = true
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

    private fun submitVoiceMessage(force: Boolean = false) {
        val text = voiceBuffer.toString().trim()
        if (text.isEmpty()) {
            if (force) {
                speechRecognizer?.stopListening()
                vad.reset()
                lastVoiceFrame = VoiceFrame()
                _voiceFrame.value = lastVoiceFrame
            }
            voiceBuffer.clear()
            updateState {
                copy(
                    ghostText = null,
                    voiceState = VoiceState.Idle,
                    voiceDraft = null,
                    isVoicePreviewVisible = false
                )
            }
            return
        }
        if (voiceSubmitted) return
        voiceSubmitted = true
        speechRecognizer?.stopListening()
        voiceBuffer.clear()
        lastVoiceFrame = VoiceFrame()
        _voiceFrame.value = lastVoiceFrame
        updateState {
            copy(
                ghostText = null,
                voiceState = VoiceState.Idle,
                voiceDraft = text,
                isVoicePreviewVisible = true
            )
        }
    }

    private fun setVoiceState(state: VoiceState) {
        updateState { copy(voiceState = state) }
    }

    private fun emitToast(@StringRes res: Int) {
        viewModelScope.launch {
            _events.emit(UiEvent.Toast(context.getString(res)))
        }
    }

    override fun onCleared() {
        super.onCleared()
        speechRecognizer?.destroy()
        speechRecognizer = null
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
        val metadata: Map<String, String> = emptyMap(),
        val tags: List<String> = emptyList(),
        val role: Role = Role.USER
    )

    private companion object {
        private const val NOTION_COMMAND_PREFIX = "/notion"
        private const val DEFAULT_NOTION_TITLE = "Новая задача"
    }
}
