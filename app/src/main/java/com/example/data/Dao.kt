package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface OpenTabDao {
    @Query("SELECT * FROM open_tabs ORDER BY openedAt ASC")
    fun getAllOpenTabs(): Flow<List<OpenTab>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOpenTab(tab: OpenTab)

    @Query("DELETE FROM open_tabs WHERE filePath = :filePath")
    suspend fun deleteOpenTab(filePath: String)

    @Query("DELETE FROM open_tabs")
    suspend fun clearAll()
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(msg: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()
}

@Dao
interface SettingDao {
    @Query("SELECT * FROM settings WHERE `key` = :key LIMIT 1")
    suspend fun getSetting(key: String): SettingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: SettingEntity)

    @Query("SELECT * FROM settings")
    fun getAllSettingsFlow(): Flow<List<SettingEntity>>
}
