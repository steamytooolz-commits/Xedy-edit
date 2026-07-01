package com.example.ai

import android.content.Context
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.ChatMessageEntity
import com.example.workspace.WorkspaceManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONObject
import java.io.File

sealed class AgentEvent {
    data class Thinking(val thought: String) : AgentEvent()
    data class ToolExecuting(val tool: String, val args: String) : AgentEvent()
    data class ToolResult(val tool: String, val result: String) : AgentEvent()
    data class Complete(val explanation: String) : AgentEvent()
    data class Error(val message: String) : AgentEvent()
}

class AgentEngine(
    private val context: Context,
    private val geminiClient: GeminiClient,
    private val executeTerminalCommand: suspend (String) -> String
) {
    private val db = AppDatabase.getDatabase(context)

    suspend fun runTask(taskDescription: String): Flow<AgentEvent> = flow {
        emit(AgentEvent.Thinking("Understanding the task and planning operations..."))

        val agentHistory = mutableListOf<ChatMessageEntity>()
        agentHistory.add(ChatMessageEntity(role = "user", content = taskDescription))

        val systemPrompt = """
            You are an autonomous AI Coding Agent inside the Alpine Editor mobile app.
            Your task is to help the user complete: "$taskDescription"
            You have access to a live workspace filesystem and terminal.

            ## Available Tools:
            To execute a tool, you MUST output a line in exactly this format (do not put any other text on that line):
            TOOL_CALL: toolName | { "argName": "value" }

            The tools you can call are:
            1. list_files | {}
               Lists all files in the current workspace recursively with paths and sizes.
            2. read_file | { "path": "relative_file_path" }
               Reads the entire content of a file in the workspace.
            3. write_file | { "path": "relative_file_path", "content": "file_contents" }
               Creates or overwrites a file in the workspace.
            4. terminal_exec | { "command": "shell_command" }
               Runs a shell command in the simulated Alpine Terminal (e.g. `ls`, `python3 script.py`, `apk add nodejs`).

            ## Rules:
            1. Always read a file before modifying it to understand its existing code.
            2. To write/edit, call `write_file` with the COMPLETE and final content of the file.
            3. After making changes, run `terminal_exec` to test/verify that the code compiles or executes correctly! E.g. `python3 script.py` or run a syntax check.
            4. If a test or command fails, read the error and try to fix it using `write_file`.
            5. Proceed step-by-step. Do only ONE tool call at a time.
            6. When you are fully finished and verified, explain your changes and conclude.
        """.trimIndent()

        var iterations = 0
        val maxIterations = 8
        var finished = false

        while (iterations < maxIterations && !finished) {
            iterations++
            Log.d("AgentEngine", "Iteration $iterations")

            // Send to Gemini
            val response = geminiClient.generateContent(
                systemInstruction = systemPrompt,
                prompt = "Current iteration: $iterations. What is your next step? If you are finished, output your final explanation.",
                history = agentHistory
            )

            // Save agent reasoning
            agentHistory.add(ChatMessageEntity(role = "assistant", content = response))

            // Parse response for thoughts and tool calls
            val toolCallRegex = Regex("TOOL_CALL:\\s*(\\w+)\\s*\\|\\s*(\\{.*\\})")
            val match = toolCallRegex.find(response)

            if (match != null) {
                val toolName = match.groupValues[1]
                val argsStr = match.groupValues[2]

                emit(AgentEvent.ToolExecuting(toolName, argsStr))

                // Parse arguments
                val argsJson = try {
                    JSONObject(argsStr)
                } catch (e: Exception) {
                    JSONObject()
                }

                val result = try {
                    executeTool(toolName, argsJson)
                } catch (e: Exception) {
                    "Error executing tool: ${e.localizedMessage ?: e.message}"
                }

                emit(AgentEvent.ToolResult(toolName, result))

                // Feed back tool output to the agent
                agentHistory.add(ChatMessageEntity(role = "user", content = "Tool result from $toolName:\n$result"))
            } else {
                // No tool call means the agent finished or explained
                finished = true
                emit(AgentEvent.Complete(response))
            }
        }

        if (iterations >= maxIterations && !finished) {
            emit(AgentEvent.Error("Reached maximum agent autonomous iterations ($maxIterations) without completing."))
        }
    }

    private suspend fun executeTool(name: String, args: JSONObject): String {
        return when (name) {
            "list_files" -> {
                val root = WorkspaceManager.getWorkspaceRoot(context)
                val files = root.walkTopDown().filter { it.isFile }.toList()
                if (files.isEmpty()) {
                    "Workspace is empty."
                } else {
                    files.joinToString("\n") { file ->
                        val relPath = WorkspaceManager.getRelativePath(context, file)
                        "- $relPath (${file.length()} bytes)"
                    }
                }
            }
            "read_file" -> {
                val path = args.optString("path", "")
                if (path.isEmpty()) return "Error: path argument is required"
                val content = WorkspaceManager.readFile(context, path)
                if (content.isEmpty() && !File(WorkspaceManager.getWorkspaceRoot(context), path).exists()) {
                    "Error: File not found at path: $path"
                } else {
                    content
                }
            }
            "write_file" -> {
                val path = args.optString("path", "")
                val content = args.optString("content", "")
                if (path.isEmpty()) return "Error: path argument is required"
                WorkspaceManager.writeFile(context, path, content)
                "Successfully wrote to $path (${content.length} characters)"
            }
            "terminal_exec" -> {
                val command = args.optString("command", "")
                if (command.isEmpty()) return "Error: command argument is required"
                executeTerminalCommand(command)
            }
            else -> {
                "Error: Unknown tool '$name'"
            }
        }
    }
}
