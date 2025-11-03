package com.example.aichat.core.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VoiceRecorder(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    data class Chunk(
        val data: ByteArray,
        val amplitude: Float,
        val centroid: Float
    )

    fun start(
        scope: CoroutineScope,
        onChunk: (Chunk) -> Unit,
        onError: (Throwable) -> Unit
    ): Session {
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            onError(IllegalStateException("Unable to determine buffer size"))
            return Session.EMPTY
        }
        val bufferSize = max(minBufferSize, SAMPLE_RATE)
        val audioRecord = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
        } catch (throwable: Throwable) {
            onError(throwable)
            return Session.EMPTY
        }

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            onError(IllegalStateException("AudioRecord not initialised"))
            return Session.EMPTY
        }

        val job = scope.launch(dispatcher) {
            val shortBuffer = ShortArray(bufferSize)
            val fftSize = nextPowerOfTwo(bufferSize)
            val real = DoubleArray(fftSize)
            val imag = DoubleArray(fftSize)
            try {
                audioRecord.startRecording()
            } catch (throwable: Throwable) {
                onError(throwable)
                return@launch
            }
            if (audioRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                onError(IllegalStateException("AudioRecord failed to start"))
                return@launch
            }
            try {
                while (isActive) {
                    val read = audioRecord.read(shortBuffer, 0, shortBuffer.size)
                    if (read <= 0) {
                        if (read == AudioRecord.ERROR_DEAD_OBJECT) {
                            onError(IllegalStateException("AudioRecord dead object"))
                            break
                        }
                        continue
                    }
                    val bytes = ByteArray(read * 2)
                    var byteIndex = 0
                    for (i in 0 until read) {
                        val sample = shortBuffer[i]
                        bytes[byteIndex++] = (sample.toInt() and 0xFF).toByte()
                        bytes[byteIndex++] = ((sample.toInt() shr 8) and 0xFF).toByte()
                    }

                    val amplitude = calculateRms(shortBuffer, read)
                    val centroid = calculateCentroid(shortBuffer, read, real, imag)
                    onChunk(Chunk(bytes, amplitude, centroid))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (throwable: Throwable) {
                onError(throwable)
            } finally {
                withContext(NonCancellable) {
                    runCatching {
                        if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                            audioRecord.stop()
                        }
                    }
                    audioRecord.release()
                }
            }
        }

        return Session(job)
    }

    class Session internal constructor(
        private val job: Job?
    ) {
        suspend fun stop() {
            job?.cancelAndJoin()
        }

        fun cancel() {
            job?.cancel()
        }

        companion object {
            val EMPTY = Session(null)
        }
    }

    private fun calculateRms(buffer: ShortArray, size: Int): Float {
        if (size == 0) return 0f
        var sum = 0.0
        for (i in 0 until size) {
            val sample = buffer[i] / Short.MAX_VALUE.toDouble()
            sum += sample * sample
        }
        val mean = sum / size
        return sqrt(mean).toFloat().coerceIn(0f, 1f)
    }

    private fun calculateCentroid(
        buffer: ShortArray,
        size: Int,
        real: DoubleArray,
        imag: DoubleArray
    ): Float {
        java.util.Arrays.fill(real, 0.0)
        java.util.Arrays.fill(imag, 0.0)
        val copySize = min(size, real.size)
        for (i in 0 until copySize) {
            real[i] = buffer[i] / Short.MAX_VALUE.toDouble()
        }
        Fft.forward(real, imag)
        var energySum = 0.0
        var weightedSum = 0.0
        val n = real.size
        for (i in 0 until n / 2) {
            val magnitude = sqrt(real[i] * real[i] + imag[i] * imag[i])
            val frequency = i * SAMPLE_RATE.toDouble() / n
            energySum += magnitude
            weightedSum += frequency * magnitude
        }
        if (energySum == 0.0) return 0f
        val centroid = weightedSum / energySum
        return (centroid / CENTROID_NORMALIZATION).toFloat().coerceIn(0f, 1f)
    }

    private fun nextPowerOfTwo(value: Int): Int {
        var v = max(1, value)
        v--
        v = v or (v shr 1)
        v = v or (v shr 2)
        v = v or (v shr 4)
        v = v or (v shr 8)
        v = v or (v shr 16)
        v++
        return v
    }

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val CENTROID_NORMALIZATION = 8_000f
    }
}
