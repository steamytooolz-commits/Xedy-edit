package com.example.ai

import android.content.Context
import android.util.Log
import com.example.workspace.WorkspaceManager
import java.io.File

object CodebaseIndexer {
    private const val TAG = "CodebaseIndexer"

    /**
     * Traverses the current workspace files, parses symbols (functions, classes, definitions),
     * and populates the VectorStore index for fast agent searches and code RAG navigation.
     */
    fun startIndexing(context: Context) {
        try {
            val root = WorkspaceManager.getWorkspaceRoot(context)
            VectorStore.clear()

            Log.i(TAG, "Starting AST-based codebase indexing...")
            indexDirectory(context, root)
            Log.i(TAG, "AST Indexing complete. Loaded ${VectorStore.getAllSnippets().size} indexed AST definitions.")
        } catch (e: Exception) {
            Log.e(TAG, "Error indexing workspace symbols", e)
        }
    }

    private fun indexDirectory(context: Context, dir: File) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                if (file.name != ".git" && file.name != ".gradle" && file.name != "rootfs") {
                    indexDirectory(context, file)
                }
            } else if (file.isFile) {
                indexFile(context, file)
            }
        }
    }

    private fun indexFile(context: Context, file: File) {
        val ext = file.extension.lowercase()
        if (ext !in listOf("py", "kt", "js", "ts", "java", "cpp", "h", "md")) return

        val content = file.readText()
        val relPath = WorkspaceManager.getRelativePath(context, file)
        val lines = content.lines()

        // Index entire file as module
        VectorStore.addSnippet(
            DocumentSnippet(
                filePath = relPath,
                content = content,
                lineStart = 1,
                type = "module"
            )
        )

        // Parse individual definitions (simulating Tree-sitter abstract syntax tree parsing)
        var currentBlock = StringBuilder()
        var blockStartLine = 1
        var inBlock = false

        for (i in lines.indices) {
            val line = lines[i]
            val trimmed = line.trim()

            val isDefinition = when (ext) {
                "py" -> trimmed.startsWith("def ") || trimmed.startsWith("class ")
                "kt", "java" -> trimmed.startsWith("fun ") || trimmed.startsWith("class ") || trimmed.startsWith("interface ")
                "js", "ts" -> trimmed.startsWith("function ") || trimmed.startsWith("class ") || trimmed.startsWith("const ") && trimmed.contains("=>")
                "cpp", "h" -> (trimmed.contains("(") && trimmed.endsWith(")")) || trimmed.startsWith("class ")
                else -> false
            }

            if (isDefinition) {
                if (inBlock) {
                    // Save previous block
                    saveBlockSnippet(relPath, currentBlock.toString(), blockStartLine)
                    currentBlock = StringBuilder()
                }
                inBlock = true
                blockStartLine = i + 1
            }

            if (inBlock) {
                currentBlock.appendLine(line)
            }
        }

        if (inBlock && currentBlock.isNotEmpty()) {
            saveBlockSnippet(relPath, currentBlock.toString(), blockStartLine)
        }
    }

    private fun saveBlockSnippet(filePath: String, blockText: String, lineStart: Int) {
        val type = if (blockText.trim().startsWith("class")) "class" else "function"
        VectorStore.addSnippet(
            DocumentSnippet(
                filePath = filePath,
                content = blockText.trim(),
                lineStart = lineStart,
                type = type
            )
        )
    }
}
