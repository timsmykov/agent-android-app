package com.example.aichat.domain.repo

import com.example.aichat.core.Result
import com.example.aichat.domain.model.ChatMessage
import com.example.aichat.domain.model.WebhookResponse

interface WebhookRepository {
    suspend fun send(message: ChatMessage, sessionId: String): Result<WebhookResponse>
}
