package com.example.plugins

import android.content.Context
import android.util.Log

object PluginLoader {
    private const val TAG = "PluginLoader"
    private val loadedPlugins = mutableListOf<EditorPlugin>()

    init {
        // Register default high-fidelity built-in extensions as part of the SDK bootstrap
        registerPlugin(object : EditorPlugin {
            override val id: String = "com.alpine.format.py"
            override val name: String = "Python Auto-Formatter"
            override val version: String = "1.0.0"

            override fun onInitialize(context: Context) {
                Log.i(TAG, "Initializing $name Extension")
            }

            override fun onBeforeSave(filePath: String, currentContent: String): String {
                if (filePath.endsWith(".py")) {
                    // Simple formatting rules: clean up trailing whitespaces & guarantee double trailing newlines
                    return currentContent.lines()
                        .map { it.trimEnd() }
                        .joinToString("\n")
                        .trimEnd() + "\n\n"
                }
                return currentContent
            }
        })

        registerPlugin(object : EditorPlugin {
            override val id: String = "com.alpine.banner.insert"
            override val name: String = "Developer Banner Tool"
            override val version: String = "1.1.0"

            override fun onInitialize(context: Context) {
                Log.i(TAG, "Initializing $name Extension")
            }

            override fun getCustomMenuActions(): List<PluginAction> {
                return listOf(
                    PluginAction(
                        id = "insert_header",
                        title = "Insert Alpine Header",
                        description = "Prepends a cool Alpine developer signature header to the active document.",
                        run = { _, content ->
                            "# Created with Alpine Editor 🏔️\n# Developed at: 2026-07-01\n\n$content"
                        }
                    )
                )
            }
        })
    }

    fun registerPlugin(plugin: EditorPlugin) {
        loadedPlugins.add(plugin)
    }

    fun getPlugins(): List<EditorPlugin> {
        return loadedPlugins
    }

    fun runBeforeSaveHooks(filePath: String, content: String): String {
        var current = content
        for (plugin in loadedPlugins) {
            current = plugin.onBeforeSave(filePath, current)
        }
        return current
    }

    fun runAfterSaveHooks(filePath: String) {
        for (plugin in loadedPlugins) {
            plugin.onAfterSave(filePath)
        }
    }
}
