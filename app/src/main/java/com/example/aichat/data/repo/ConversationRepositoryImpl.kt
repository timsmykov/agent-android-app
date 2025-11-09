package com.example.aichat.data.repo

import com.example.aichat.data.local.dao.ConversationDao
import com.example.aichat.data.local.dao.MessageDao
import com.example.aichat.data.local.entity.ConversationEntity
import com.example.aichat.data.local.entity.MessageEntity
import com.example.aichat.domain.model.ChatMessage
import com.example.aichat.domain.model.ConversationSummary
import com.example.aichat.domain.model.Role
import com.example.aichat.domain.repo.ConversationRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ConversationRepositoryImpl @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val json: Json
) : ConversationRepository {

    override fun observeConversations(): Flow<List<ConversationSummary>> =
        conversationDao.observeConversations()
            .map { items -> items.map { it.toSummary() } }

    override suspend fun loadConversation(conversationId: String): List<ChatMessage> {
        return messageDao.getMessages(conversationId).map { entity ->
            json.decodeFromString(ChatMessage.serializer(), entity.body)
        }
    }

    override suspend fun appendMessage(conversationId: String, message: ChatMessage) {
        val existing = conversationDao.findById(conversationId)
        val updatedCount = (existing?.messageCount ?: 0) + 1
        val resolvedTitle = when {
            !existing?.title.isNullOrBlank() -> existing?.title
            message.role == Role.USER -> message.text.takeIf { it.isNotBlank() }?.trim()?.take(MAX_TITLE)
            else -> null
        }
        val conversation = ConversationEntity(
            id = conversationId,
            title = resolvedTitle ?: existing?.title ?: DEFAULT_TITLE,
            lastMessagePreview = message.text.take(MAX_PREVIEW),
            createdAt = existing?.createdAt ?: message.timestamp,
            updatedAt = message.timestamp,
            messageCount = updatedCount
        )
        conversationDao.upsert(conversation)
        messageDao.upsert(message.toEntity(conversationId))
    }

    override suspend fun updateMessage(conversationId: String, message: ChatMessage) {
        messageDao.upsert(message.toEntity(conversationId))
    }

    override suspend fun deleteConversation(conversationId: String) {
        messageDao.deleteByConversation(conversationId)
        conversationDao.deleteById(conversationId)
    }

    private fun ConversationEntity.toSummary(): ConversationSummary =
        ConversationSummary(
            id = id,
            title = title.orEmpty().ifBlank { DEFAULT_TITLE },
            lastMessagePreview = lastMessagePreview,
            updatedAt = updatedAt,
            messageCount = messageCount
        )

    private fun ChatMessage.toEntity(conversationId: String): MessageEntity =
        MessageEntity(
            id = id,
            conversationId = conversationId,
            role = role.name,
            text = text,
            status = status.name,
            timestamp = timestamp,
            body = json.encodeToString(ChatMessage.serializer(), this)
        )

    companion object {
        private const val DEFAULT_TITLE = "Диалог"
        private const val MAX_TITLE = 80
        private const val MAX_PREVIEW = 180
    }
}
