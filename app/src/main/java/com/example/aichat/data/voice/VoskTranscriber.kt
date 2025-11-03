package com.example.aichat.data.voice

import com.example.aichat.BuildConfig
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString

class VoskTranscriber @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val ioDispatcher: CoroutineDispatcher
) {

    fun start(
        scope: CoroutineScope,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit,
        onError: (Throwable) -> Unit
    ): Session {
        val audioChannel = Channel<ByteArray>(Channel.UNLIMITED)
        val handshake = CompletableDeferred<Unit>()
        val session = Session(audioChannel, handshake, scope, onError)
        val request = Request.Builder()
            .url(BuildConfig.VOSK_WS_URL)
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                handshake.complete(Unit)
                webSocket.send(CONFIG_MESSAGE)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                scope.launch {
                    try {
                        handleMessage(text, onPartial, onFinal)
                    } catch (throwable: Throwable) {
                        if (session.notifyError(throwable)) {
                            onError(throwable)
                        }
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!handshake.isCompleted) {
                    handshake.completeExceptionally(t)
                }
                if (session.notifyError(t)) {
                    scope.launch { onError(t) }
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                session.markClosed()
            }
        }

        val webSocket = okHttpClient.newWebSocket(request, listener)
        session.attach(webSocket)
        session.startSender(ioDispatcher, onError)
        return session
    }

    private fun handleMessage(
        payload: String,
        onPartial: (String) -> Unit,
        onFinal: (String) -> Unit
    ) {
        val element = try {
            json.parseToJsonElement(payload)
        } catch (ex: Exception) {
            throw IOException("Invalid transcription payload: $payload", ex)
        }
        val obj = element.jsonObject
        obj["partial"]?.jsonPrimitive
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?.let(onPartial)

        obj["text"]?.jsonPrimitive
            ?.contentOrNull
            ?.let(onFinal)
    }

    class Session internal constructor(
        private val channel: kotlinx.coroutines.channels.Channel<ByteArray>,
        private val handshake: CompletableDeferred<Unit>,
        private val scope: CoroutineScope,
        private val onError: (Throwable) -> Unit
    ) {
        private var webSocket: WebSocket? = null
        private var senderJob: Job? = null
        private val closed = AtomicBoolean(false)
        private val errorNotified = AtomicBoolean(false)

        internal fun attach(socket: WebSocket) {
            this.webSocket = socket
        }

        internal fun startSender(dispatcher: CoroutineDispatcher, errorCallback: (Throwable) -> Unit) {
            senderJob = scope.launch(dispatcher) {
                try {
                    handshake.await()
                } catch (throwable: Throwable) {
                    if (notifyError(throwable)) {
                        errorCallback(throwable)
                    }
                    throw throwable
                }
                try {
                    for (chunk in channel) {
                        val socket = webSocket ?: throw IllegalStateException("WebSocket not available")
                        if (!socket.send(chunk.toByteString())) {
                            throw IOException("Failed to send audio chunk")
                        }
                    }
                    webSocket?.send(EOF_MESSAGE)
                } catch (throwable: Throwable) {
                    if (throwable is kotlinx.coroutines.CancellationException) throw throwable
                    if (notifyError(throwable)) {
                        errorCallback(throwable)
                    }
                    webSocket?.cancel()
                }
            }
        }

        fun offer(chunk: ByteArray) {
            if (channel.isClosedForSend) return
            val result = channel.trySend(chunk)
            if (result.isFailure) {
                val overflow = IOException("Audio buffer overflow")
                if (notifyError(overflow)) {
                    onError(overflow)
                }
            }
        }

        suspend fun finish() {
            if (closed.compareAndSet(false, true)) {
                channel.close()
                senderJob?.join()
            }
        }

        fun cancel() {
            if (closed.compareAndSet(false, true)) {
                channel.cancel()
                senderJob?.cancel()
                webSocket?.cancel()
            }
        }

        internal fun markClosed() {
            closed.set(true)
        }

        internal fun notifyError(throwable: Throwable): Boolean {
            if (errorNotified.compareAndSet(false, true)) {
                channel.cancel()
                senderJob?.cancel()
                webSocket?.cancel()
                return true
            }
            return false
        }

        companion object {
            private const val EOF_MESSAGE = "{\"eof\" : 1}"
            private const val NORMAL_CLOSURE_CODE = 1000
        }
    }

    companion object {
        private const val CONFIG_MESSAGE = """{"config":{"sample_rate":16000,"words":true}}"""
    }
}
