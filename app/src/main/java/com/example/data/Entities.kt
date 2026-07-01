package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "open_tabs")
data class OpenTab(
    @PrimaryKey val filePath: String,
    val title: String,
    val openedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String, // "user", "assistant", "system", "agent_thinking", "agent_action", "agent_error"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,
    val value: String
)
