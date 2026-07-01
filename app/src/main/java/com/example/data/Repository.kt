package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class EditorRepository(private val db: AppDatabase) {

    private val openTabDao = db.openTabDao()
    private val chatMessageDao = db.chatMessageDao()
    private val settingDao = db.settingDao()

    val openTabs: Flow<List<OpenTab>> = openTabDao.getAllOpenTabs()
    val chatMessages: Flow<List<ChatMessageEntity>> = chatMessageDao.getAllMessages()
    val allSettingsFlow: Flow<Map<String, String>> = settingDao.getAllSettingsFlow()
        .map { list -> list.associate { it.key to it.value } }

    suspend fun getSetting(key: String, defaultValue: String = ""): String {
        return settingDao.getSetting(key)?.value ?: defaultValue
    }

    suspend fun setSetting(key: String, value: String) {
        settingDao.insertSetting(SettingEntity(key, value))
    }

    suspend fun insertOpenTab(tab: OpenTab) {
        openTabDao.insertOpenTab(tab)
    }

    suspend fun deleteOpenTab(filePath: String) {
        openTabDao.deleteOpenTab(filePath)
    }

    suspend fun clearOpenTabs() {
        openTabDao.clearAll()
    }

    suspend fun insertChatMessage(role: String, content: String): ChatMessageEntity {
        val entity = ChatMessageEntity(role = role, content = content)
        val id = chatMessageDao.insertMessage(entity)
        return entity.copy(id = id)
    }

    suspend fun clearChatMessages() {
        chatMessageDao.clearAll()
    }
}
