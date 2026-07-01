package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AgentEngine
import com.example.ai.AgentEvent
import com.example.ai.GeminiClient
import com.example.data.AppDatabase
import com.example.data.ChatMessageEntity
import com.example.data.EditorRepository
import com.example.data.OpenTab
import com.example.terminal.TerminalSimulation
import com.example.workspace.WorkspaceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val repository = EditorRepository(db)
    private val geminiClient = GeminiClient(application)
    private val terminalSim = TerminalSimulation(application, geminiClient)
    private val agentEngine = AgentEngine(application, geminiClient) { cmd ->
        terminalSim.executeCommand(cmd)
    }

    // --- Workspace Files State ---
    private val _workspaceFiles = MutableStateFlow<List<File>>(emptyList())
    val workspaceFiles: StateFlow<List<File>> = _workspaceFiles.asStateFlow()

    // --- Open Tabs State ---
    val openTabs: StateFlow<List<OpenTab>> = repository.openTabs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeTabPath = MutableStateFlow<String?>(null)
    val activeTabPath: StateFlow<String?> = _activeTabPath.asStateFlow()

    var activeFileContent by mutableStateOf("")
        private set

    // --- Terminal State ---
    private val _terminalHistory = MutableStateFlow<List<String>>(emptyList())
    val terminalHistory: StateFlow<List<String>> = _terminalHistory.asStateFlow()

    private val _terminalPrompt = MutableStateFlow(terminalSim.getPrompt())
    val terminalPrompt: StateFlow<String> = _terminalPrompt.asStateFlow()

    // --- AI Agent / Chat State ---
    val chatMessages: StateFlow<List<ChatMessageEntity>> = repository.chatMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isAgentRunning = MutableStateFlow(false)
    val isAgentRunning: StateFlow<Boolean> = _isAgentRunning.asStateFlow()

    private val _agentLogs = MutableStateFlow<List<AgentEvent>>(emptyList())
    val agentLogs: StateFlow<List<AgentEvent>> = _agentLogs.asStateFlow()

    // --- Settings State ---
    val settingsMap: StateFlow<Map<String, String>> = repository.allSettingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        // Init Workspace demo files on background thread
        viewModelScope.launch(Dispatchers.IO) {
            WorkspaceManager.initWorkspace(application)
            refreshFiles()
            
            // Bootstrap Alpine virtual environment
            try {
                val alpineTerminalManager = com.example.terminal.AlpineTerminalManager(application)
                alpineTerminalManager.extractAlpineRootfs()
            } catch (e: Exception) {
                Log.e("EditorViewModel", "Failed to extract alpine rootfs", e)
            }

            // Start Codebase AST symbol indexing for RAG
            try {
                com.example.ai.CodebaseIndexer.startIndexing(application)
            } catch (e: Exception) {
                Log.e("EditorViewModel", "Failed to index codebase", e)
            }

            // Print welcome in terminal
            val welcomeLines = listOf(
                "Welcome to Alpine OS v3.21.0 on aarch64 core!",
                "Type 'help' to see available Alpine utilities, compilers, or LLMs.",
                "Simulated package manager 'apk' is ready.",
                "Physical workspace is mounted at `/home/alpine/workspace`.",
                "",
                terminalSim.getPrompt()
            )
            _terminalHistory.value = welcomeLines

            // Open README.md on start
            val readmeFile = File(WorkspaceManager.getWorkspaceRoot(application), "README.md")
            if (readmeFile.exists()) {
                openTab("README.md")
            }
        }
    }

    // --- File Operations ---
    fun refreshFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val root = WorkspaceManager.getWorkspaceRoot(getApplication())
            val list = root.walkTopDown()
                .filter { it.name != ".git" && !it.absolutePath.contains(".git/") }
                .toList()
            _workspaceFiles.value = list
        }
    }

    fun createFile(relPath: String, content: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            WorkspaceManager.createFile(getApplication(), relPath, content)
            refreshFiles()
            appendTerminalLine("Created file: $relPath")
        }
    }

    fun createDirectory(relPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            WorkspaceManager.createDirectory(getApplication(), relPath)
            refreshFiles()
            appendTerminalLine("Created directory: $relPath")
        }
    }

    fun deleteFile(relPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            WorkspaceManager.deleteFile(getApplication(), relPath)
            // If deleting open file, close it
            repository.deleteOpenTab(relPath)
            if (_activeTabPath.value == relPath) {
                _activeTabPath.value = null
                activeFileContent = ""
            }
            refreshFiles()
            appendTerminalLine("Deleted path: $relPath")
        }
    }

    fun openTab(relPath: String) {
        viewModelScope.launch {
            // First save active tab if there is one
            saveActiveTabContent()

            val file = WorkspaceManager.getFileFromRelative(getApplication(), relPath)
            if (file.exists() && file.isFile) {
                val title = file.name
                repository.insertOpenTab(OpenTab(filePath = relPath, title = title))
                _activeTabPath.value = relPath
                activeFileContent = WorkspaceManager.readFile(getApplication(), relPath)
            }
        }
    }

    fun closeTab(relPath: String) {
        viewModelScope.launch {
            repository.deleteOpenTab(relPath)
            if (_activeTabPath.value == relPath) {
                val tabs = openTabs.value
                val index = tabs.indexOfFirst { it.filePath == relPath }
                if (tabs.size > 1) {
                    val nextTab = if (index == tabs.size - 1) tabs[index - 1] else tabs[index + 1]
                    openTab(nextTab.filePath)
                } else {
                    _activeTabPath.value = null
                    activeFileContent = ""
                }
            }
        }
    }

    fun selectTab(relPath: String) {
        openTab(relPath)
    }

    fun updateActiveContent(content: String) {
        activeFileContent = content
    }

    suspend fun saveActiveTabContent() {
        val path = _activeTabPath.value
        if (path != null) {
            withContext(Dispatchers.IO) {
                WorkspaceManager.writeFile(getApplication(), path, activeFileContent)
            }
        }
    }

    fun saveCurrentFile() {
        viewModelScope.launch {
            saveActiveTabContent()
            appendTerminalLine("Saved active file content to disk.")
        }
    }

    // --- Terminal Operations ---
    fun executeTerminalCommand(input: String) {
        viewModelScope.launch {
            val cmd = input.trim()
            if (cmd.isEmpty()) return@launch

            // 1. Add command line to history
            val prompt = _terminalPrompt.value
            _terminalHistory.value = _terminalHistory.value + "$prompt$cmd"

            if (cmd == "clear") {
                _terminalHistory.value = emptyList()
                return@launch
            }

            // 2. Execute simulation
            val result = withContext(Dispatchers.IO) {
                terminalSim.executeCommand(cmd)
            }

            // 3. Update output
            val outputLines = result.split("\n")
            _terminalHistory.value = _terminalHistory.value + outputLines
            _terminalPrompt.value = terminalSim.getPrompt()
            
            // Refresh files since git/terminal commands might modify workspace files
            refreshFiles()
            
            // If active tab file was modified, reload it
            val active = _activeTabPath.value
            if (active != null) {
                activeFileContent = withContext(Dispatchers.IO) {
                    WorkspaceManager.readFile(getApplication(), active)
                }
            }
        }
    }

    private fun appendTerminalLine(line: String) {
        _terminalHistory.value = _terminalHistory.value + line
    }

    // --- AI Agent / Chat Operations ---
    fun sendChatMessage(text: String) {
        viewModelScope.launch {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return@launch

            // Insert user message
            repository.insertChatMessage("user", trimmed)

            if (trimmed.startsWith("/agent")) {
                val task = trimmed.removePrefix("/agent").trim()
                runAgent(task)
            } else {
                // Regular chat with Gemini Client
                _isAgentRunning.value = true
                val prompt = trimmed
                
                // Read active file content for contextual code answers
                val activeFile = _activeTabPath.value
                val contextPrompt = if (activeFile != null) {
                    """
                    |User is currently editing the file: '$activeFile'.
                    |Here is the file content:
                    |```
                    |$activeFileContent
                    |```
                    |
                    |Question: $prompt
                    """.trimMargin()
                } else {
                    prompt
                }

                val systemInstruction = "You are a friendly, expert Linux developer and coding assistant inside the Alpine Editor application. Help the user with their coding questions."

                val response = geminiClient.generateContent(
                    systemInstruction = systemInstruction,
                    prompt = contextPrompt,
                    history = chatMessages.value
                )

                repository.insertChatMessage("assistant", response)
                _isAgentRunning.value = false
            }
        }
    }

    private fun runAgent(task: String) {
        viewModelScope.launch {
            if (_isAgentRunning.value) return@launch
            _isAgentRunning.value = true
            _agentLogs.value = emptyList()

            repository.insertChatMessage("system", "Starting autonomous Coding Agent for task: \"$task\"...")

            agentEngine.runTask(task).collect { event ->
                _agentLogs.value = _agentLogs.value + event
                when (event) {
                    is AgentEvent.Thinking -> {
                        repository.insertChatMessage("agent_thinking", event.thought)
                    }
                    is AgentEvent.ToolExecuting -> {
                        repository.insertChatMessage("agent_action", "Calling: ${event.tool} with args: ${event.args}")
                    }
                    is AgentEvent.ToolResult -> {
                        // Log output
                    }
                    is AgentEvent.Complete -> {
                        repository.insertChatMessage("assistant", "✅ Autonomous Task Complete!\n\n${event.explanation}")
                        _isAgentRunning.value = false
                        refreshFiles()
                        
                        // Reload active tab if it changed
                        val active = _activeTabPath.value
                        if (active != null) {
                            activeFileContent = withContext(Dispatchers.IO) {
                                WorkspaceManager.readFile(getApplication(), active)
                            }
                        }
                    }
                    is AgentEvent.Error -> {
                        repository.insertChatMessage("agent_error", "⚠️ Agent Error: ${event.message}")
                        _isAgentRunning.value = false
                    }
                }
            }
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            repository.clearChatMessages()
            _agentLogs.value = emptyList()
        }
    }

    // --- Settings ---
    fun saveSetting(key: String, value: String) {
        viewModelScope.launch {
            repository.setSetting(key, value)
        }
    }

    fun getSettingSync(key: String, defaultValue: String): String {
        return settingsMap.value[key] ?: defaultValue
    }

    // --- External Storage Export / Import ---
    fun exportWorkspace(): String {
        return com.example.workspace.ExternalStorageBridge.exportWorkspaceToDownloads(getApplication())
    }

    fun importWorkspace(path: String): String {
        val result = com.example.workspace.ExternalStorageBridge.importWorkspaceFromPath(getApplication(), path)
        refreshFiles()
        return result
    }
}
