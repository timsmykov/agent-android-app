package com.example.aichat.domain.repo

import com.example.aichat.domain.model.ChatMessage
import com.example.aichat.domain.model.ConversationSummary
import kotlinx.coroutines.flow.Flow

interface ConversationRepository {
    fun observeConversations(): Flow<List<ConversationSummary>>
    suspend fun loadConversation(conversationId: String): List<ChatMessage>
    suspend fun appendMessage(conversationId: String, message: ChatMessage)
    suspend fun updateMessage(conversationId: String, message: ChatMessage)
    suspend fun deleteConversation(conversationId: String)
}
