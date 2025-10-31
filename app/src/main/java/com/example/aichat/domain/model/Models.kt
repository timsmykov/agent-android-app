package com.example.aichat.domain.model

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
    val summary: String? = null,
    val plan: List<PlanItem> = emptyList(),
    val sources: List<SourceLink> = emptyList()
)

data class PlanItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val isDone: Boolean = false
)

data class SourceLink(
    val title: String,
    val url: String
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
        val userAgent: String,
        val lang: String
    )
}

@Serializable
data class WebhookResponse(
    val ok: Boolean? = null,
    val status: String? = null,
    val message: String? = null,
    val result: WorkflowResult? = null,
    val data: WorkflowResult? = null,
    val choices: List<WorkflowResult>? = null,
    val httpCode: Int = 0
) {
    val primaryResult: WorkflowResult?
        get() = result ?: data ?: choices?.firstOrNull()

    fun resolveText(): String? {
        val candidate = listOfNotNull(
            message,
            primaryResult?.reply,
            primaryResult?.text,
            status
        ).firstOrNull { !it.isNullOrBlank() }
        return candidate?.ifBlank { null }
    }
}

@Serializable
data class WorkflowResult(
    val reply: String? = null,
    val text: String? = null,
    val summary: String? = null,
    val plan: List<WorkflowPlanItem>? = null,
    val sources: List<WorkflowSource>? = null
)

@Serializable
data class WorkflowPlanItem(
    val title: String,
    @SerialName("isDone")
    val done: Boolean? = null
)

@Serializable
data class WorkflowSource(
    val title: String,
    val url: String
)
