package com.example.aichat.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.aichat.data.local.dao.ConversationDao
import com.example.aichat.data.local.dao.MessageDao
import com.example.aichat.data.local.entity.ConversationEntity
import com.example.aichat.data.local.entity.MessageEntity

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
}
