package com.duq.android.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.duq.android.data.local.dao.ConversationDao
import com.duq.android.data.local.dao.MessageDao
import com.duq.android.data.local.entities.ConversationEntity
import com.duq.android.data.local.entities.MessageEntity

@Database(
    entities = [ConversationEntity::class, MessageEntity::class],
    version = 1,
    exportSchema = true
)
abstract class DuqDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
}
