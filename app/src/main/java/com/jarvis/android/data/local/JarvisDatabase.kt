package com.jarvis.android.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.jarvis.android.data.local.dao.ConversationDao
import com.jarvis.android.data.local.dao.MessageDao
import com.jarvis.android.data.local.entities.ConversationEntity
import com.jarvis.android.data.local.entities.MessageEntity

@Database(
    entities = [ConversationEntity::class, MessageEntity::class],
    version = 1,
    exportSchema = true
)
abstract class JarvisDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
}
