package com.example.aichat.domain.model

import android.os.Build
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class Role { USER, AGENT, SYSTEM }

enum class MessageStatus { PENDING, SENT, RECEIVED, FAILED }

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val role: Role,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.PENDING,
    val isSpeaking: Boolean = false,
    val isGhost: Boolean = false
)

@Serializable
data class WebhookPayload(
    val message: PayloadMessage,
    val meta: PayloadMeta
) {
    @Serializable
    data class PayloadMessage(
        val id: String,
        val text: String,
        val role: String,
        val ts: Long
    )

    @Serializable
    data class PayloadMeta(
        val client: String,
        val sessionId: String,
        val device: String,
        val lang: String
    )

    companion object {
        fun fromChatMessage(message: ChatMessage, sessionId: String, lang: String = "ru"): WebhookPayload {
            return WebhookPayload(
                message = PayloadMessage(
                    id = message.id,
                    text = message.text,
                    role = message.role.name.lowercase(),
                    ts = 0L
                ),
                meta = PayloadMeta(
                    client = "android",
                    sessionId = sessionId,
                    device = Build.MODEL.ifEmpty { "android" },
                    lang = lang
                )
            )
        }
    }
}

@Serializable
data class WebhookResponse(
    val ok: Boolean? = null,
    val status: String? = null,
    val message: String? = null,
    val httpCode: Int = 0
)
