package com.example.aichat.domain.usecase

import com.example.aichat.core.Result
import com.example.aichat.domain.model.ChatMessage
import com.example.aichat.domain.model.WebhookResponse
import com.example.aichat.domain.repo.WebhookRepository
import javax.inject.Inject

class SendMessageUseCase @Inject constructor(
    private val repository: WebhookRepository
) {
    suspend operator fun invoke(message: ChatMessage, sessionId: String): Result<WebhookResponse> {
        return repository.send(message, sessionId)
    }
}
