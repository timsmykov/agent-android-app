package com.example.aichat.core.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.VisibleForTesting
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

@Singleton
class AudioAnalyzer(
    private val contextDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    data class AudioFrame(
        val amplitude: Float,
        val centroid: Float
    )

    fun frames(): Flow<AudioFrame> = callbackFlow {
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
            close(IllegalStateException("AudioRecord buffer size error"))
            return@callbackFlow
        }

        val bufferSize = max(minBufferSize, SAMPLE_RATE)
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            close(IllegalStateException("AudioRecord not initialized"))
            return@callbackFlow
        }

        audioRecord.startRecording()

        val pcmBuffer = ShortArray(bufferSize)
        val real = DoubleArray(nextPowerOfTwo(bufferSize))
        val imag = DoubleArray(real.size)

        val job = launch(contextDispatcher) {
            while (true) {
                val read = audioRecord.read(pcmBuffer, 0, pcmBuffer.size)
                if (read <= 0) continue

                val amplitude = calculateRms(pcmBuffer, read)

                for (i in real.indices) {
                    real[i] = 0.0
                    imag[i] = 0.0
                }
                val copySize = min(real.size, read)
                for (i in 0 until copySize) {
                    real[i] = pcmBuffer[i] / Short.MAX_VALUE.toDouble()
                }
                Fft.forward(real, imag)
                val centroid = calculateSpectralCentroid(real, imag, SAMPLE_RATE.toDouble())

                trySend(AudioFrame(amplitude, centroid))
            }
        }

        awaitClose {
            job.cancel()
            audioRecord.stop()
            audioRecord.release()
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
        return sqrt(mean).toFloat()
    }

    @VisibleForTesting
    internal fun calculateSpectralCentroid(real: DoubleArray, imag: DoubleArray, sampleRate: Double): Float {
        var energySum = 0.0
        var weightedSum = 0.0
        val n = real.size
        for (i in 0 until n / 2) {
            val magnitude = sqrt(real[i] * real[i] + imag[i] * imag[i])
            val frequency = i * sampleRate / n
            energySum += magnitude
            weightedSum += frequency * magnitude
        }
        if (energySum == 0.0) return 0f
        val centroid = weightedSum / energySum
        return centroid.toFloat()
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
}
