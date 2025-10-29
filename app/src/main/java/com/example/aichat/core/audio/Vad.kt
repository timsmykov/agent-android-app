package com.example.aichat.core.audio

import kotlin.math.max

class VoiceActivityDetector {
    private var state: State = State.Silence
    private var lastSpeechTimestamp: Long = 0L

    data class Detection(
        val isSpeech: Boolean,
        val started: Boolean,
        val ended: Boolean
    )

    enum class State { Silence, Speech }

    fun reset() {
        state = State.Silence
        lastSpeechTimestamp = 0L
    }

    fun evaluate(amplitude: Float, timestamp: Long, startThreshold: Float = 0.02f, stopThreshold: Float = 0.01f, silenceTimeoutMs: Long = 700L): Detection {
        val clamped = max(0f, amplitude)
        val nowSpeech = when (state) {
            State.Silence -> clamped >= startThreshold
            State.Speech -> clamped >= stopThreshold
        }

        var started = false
        var ended = false

        if (state == State.Silence && nowSpeech) {
            state = State.Speech
            lastSpeechTimestamp = timestamp
            started = true
        } else if (state == State.Speech) {
            if (nowSpeech) {
                lastSpeechTimestamp = timestamp
            } else if (timestamp - lastSpeechTimestamp >= silenceTimeoutMs) {
                state = State.Silence
                ended = true
            }
        }

        return Detection(
            isSpeech = state == State.Speech,
            started = started,
            ended = ended
        )
    }
}
