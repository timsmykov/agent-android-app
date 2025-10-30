package com.example.aichat.ui.chat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.annotation.StringRes
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.aichat.R
import com.example.aichat.core.Result
import com.example.aichat.core.audio.AudioAnalyzer
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
import kotlinx.coroutines.Job
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
    private val audioAnalyzer: AudioAnalyzer,
    private val vad: VoiceActivityDetector,
    @ApplicationContext private val context: Context,
    private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val sessionId: String = UUID.randomUUID().toString()

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _messages = mutableStateListOf<ChatMessage>()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private val _hasMicrophonePermission = MutableStateFlow(false)
    val hasMicrophonePermission: StateFlow<Boolean> = _hasMicrophonePermission.asStateFlow()

    private val _microphonePermissionRequested = MutableSharedFlow<Boolean>(replay = 0)
    val microphonePermissionRequested: SharedFlow<Boolean> = _microphonePermissionRequested.asSharedFlow()

    private val _voiceFrame = MutableStateFlow(AudioAnalyzer.AudioFrame(0f, 0f))
    val voiceFrame: StateFlow<AudioAnalyzer.AudioFrame> = _voiceFrame.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private var analyzerJob: Job? = null

    private var voiceBuffer = StringBuilder()

    init {
        initTextToSpeech()
    }

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

    fun insertCommand(command: String) {
        val newValue = when (command) {
            "/plan" -> "\n1. [ ] "
            "/summarize" -> "Пожалуйста, сделай сводку пунктами."
            else -> command
        }
        updateState { copy(input = newValue) }
    }

    fun sendMessage() {
        val text = _uiState.value.input.trim()
        if (text.isEmpty()) return

        val message = ChatMessage(text = text, role = Role.USER)
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
                        setVoiceState(VoiceState.Listening)
                    }

                    override fun onBeginningOfSpeech() {
                        setVoiceState(VoiceState.Listening)
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        val normalized = (rmsdB + 2) / 10f
                        feedVad(normalized)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() {
                        setVoiceState(VoiceState.Thinking)
                        submitVoiceMessage()
                    }

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

        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        speechRecognizer?.startListening(recognizerIntent)
        startAnalyzer()
        setVoiceState(VoiceState.Listening)
    }

    fun stopVoiceInput() {
        speechRecognizer?.stopListening()
        speechRecognizer?.cancel()
        stopAnalyzer()
        voiceBuffer.clear()
        updateState { copy(ghostText = null, voiceState = VoiceState.Idle) }
    }

    fun onComposerGhostConsumed() {
        updateState { copy(ghostText = null) }
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
        updateState { copy(isSending = false, voiceState = VoiceState.Speaking) }
        speak(agentMessage.text)
    }

    private fun handleError(messageId: String, throwable: Throwable) {
        Timber.e(throwable, "Message send failed")
        updateMessage(messageId) { copy(status = MessageStatus.FAILED) }
        updateState { copy(isSending = false) }
        emitToast(R.string.send_failed)
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

    private fun initTextToSpeech() {
        textToSpeech = TextToSpeech(context) { status ->
            if (status != TextToSpeech.SUCCESS) {
                Timber.e("TTS init failed: $status")
            } else {
                textToSpeech?.language = Locale("ru")
                textToSpeech?.setSpeechRate(1.02f)
            }
        }
    }

    private fun speak(text: String) {
        val tts = textToSpeech ?: return
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, UUID.randomUUID().toString())
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, params.getString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID))
        tts.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                updateState { copy(voiceState = VoiceState.Speaking) }
            }

            override fun onDone(utteranceId: String?) {
                updateState { copy(voiceState = VoiceState.Idle) }
            }

            override fun onError(utteranceId: String?) {
                updateState { copy(voiceState = VoiceState.Idle) }
            }
        })
    }

    private fun startAnalyzer() {
        if (analyzerJob?.isActive == true) return
        vad.reset()
        analyzerJob = viewModelScope.launch {
            audioAnalyzer.frames().collect { frame ->
                _voiceFrame.value = frame
                val detection = vad.evaluate(frame.amplitude, System.currentTimeMillis())
                if (detection.started) {
                    setVoiceState(VoiceState.Listening)
                }
                if (detection.ended) {
                    setVoiceState(VoiceState.Thinking)
                    submitVoiceMessage()
                }
            }
        }
    }

    private fun stopAnalyzer() {
        analyzerJob?.cancel()
        analyzerJob = null
        vad.reset()
        _voiceFrame.value = AudioAnalyzer.AudioFrame(0f, 0f)
    }

    private fun submitVoiceMessage() {
        val text = voiceBuffer.toString().trim()
        if (text.isEmpty()) {
            updateState { copy(ghostText = null, voiceState = VoiceState.Idle) }
            return
        }
        speechRecognizer?.stopListening()
        updateState { copy(input = text, ghostText = null, voiceState = VoiceState.Thinking) }
        voiceBuffer.clear()
        stopAnalyzer()
        sendMessage()
    }

    private fun setVoiceState(state: VoiceState) {
        updateState { copy(voiceState = state) }
    }

    private fun feedVad(amplitude: Float) {
        val detection = vad.evaluate(amplitude, System.currentTimeMillis())
        if (detection.started) {
            setVoiceState(VoiceState.Listening)
        }
        if (detection.ended) {
            submitVoiceMessage()
        }
    }

    private fun emitToast(@StringRes res: Int) {
        viewModelScope.launch {
            _events.emit(UiEvent.Toast(context.getString(res)))
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopAnalyzer()
        speechRecognizer?.destroy()
        textToSpeech?.shutdown()
    }

    data class ChatUiState(
        val messages: List<ChatMessage> = emptyList(),
        val input: String = "",
        val isSending: Boolean = false,
        val ghostText: String? = null,
        val showPermissionRationale: Boolean = false,
        val voiceState: VoiceState = VoiceState.Idle
    )

    sealed class UiEvent {
        data class Toast(val message: String) : UiEvent()
    }

    enum class VoiceState { Idle, Listening, Thinking, Speaking }
}

val ChatViewModel.hasMicrophonePermissionState: StateFlow<Boolean>
    get() = hasMicrophonePermission
