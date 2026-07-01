package com.example.plugins

import android.content.Context

interface EditorPlugin {
    val id: String
    val name: String
    val version: String
    
    fun onInitialize(context: Context)
    fun onBeforeSave(filePath: String, currentContent: String): String = currentContent
    fun onAfterSave(filePath: String) {}
    fun getCustomMenuActions(): List<PluginAction> = emptyList()
}

data class PluginAction(
    val id: String,
    val title: String,
    val description: String,
    val run: (context: Context, currentFileContent: String) -> String
)
