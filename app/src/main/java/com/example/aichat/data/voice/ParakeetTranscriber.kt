package com.example.aichat.data.voice

import com.example.aichat.BuildConfig
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ParakeetTranscriber @Inject constructor(
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
        return Session(scope, ioDispatcher, onPartial, onFinal, onError)
    }

    inner class Session internal constructor(
        private val scope: CoroutineScope,
        private val dispatcher: CoroutineDispatcher,
        private val onPartial: (String) -> Unit,
        private val onFinal: (String) -> Unit,
        private val onError: (Throwable) -> Unit
    ) {
        private val bufferLock = Any()
        private val audioBuffer = ByteArrayOutputStream()
        private val closed = AtomicBoolean(false)
        private val errorNotified = AtomicBoolean(false)

        fun offer(chunk: ByteArray) {
            if (closed.get() || chunk.isEmpty()) return
            synchronized(bufferLock) {
                audioBuffer.write(chunk)
            }
        }

        suspend fun finish() {
            if (!closed.compareAndSet(false, true)) return
            val payload = synchronized(bufferLock) {
                val bytes = audioBuffer.toByteArray()
                audioBuffer.reset()
                bytes
            }
            if (payload.isEmpty()) {
                scope.launch { onFinal("") }
                return
            }

            runCatching {
                val text = send(payload)
                scope.launch {
                    if (text.isNotBlank()) {
                        onPartial(text)
                    }
                    onFinal(text)
                }
            }.onFailure { throwable ->
                if (notifyError()) {
                    scope.launch { onError(throwable) }
                }
            }
        }

        fun cancel() {
            if (closed.compareAndSet(false, true)) {
                synchronized(bufferLock) { audioBuffer.reset() }
            }
        }

        private suspend fun send(payload: ByteArray): String = withContext(dispatcher) {
            val request = Request.Builder()
                .url(BuildConfig.ASR_URL)
                .post(payload.toRequestBody(PCM_MEDIA_TYPE))
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("ASR HTTP ${response.code}")
                }
                val body = response.body?.string() ?: throw IOException("Empty ASR response")
                val parsed = json.parseToJsonElement(body).jsonObject
                parsed["text"]?.jsonPrimitive?.content.orEmpty().trim()
            }
        }

        private fun notifyError(): Boolean = errorNotified.compareAndSet(false, true)
    }

    companion object {
        private val PCM_MEDIA_TYPE = "application/octet-stream".toMediaType()
    }
}
