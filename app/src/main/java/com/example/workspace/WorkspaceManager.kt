package com.example.workspace

import android.content.Context
import java.io.File

object WorkspaceManager {

    fun getWorkspaceRoot(context: Context): File {
        val root = File(context.filesDir, "workspace")
        if (!root.exists()) {
            root.mkdirs()
        }
        return root
    }

    fun initWorkspace(context: Context) {
        val root = getWorkspaceRoot(context)
        
        // 1. Create README.md
        val readme = File(root, "README.md")
        if (!readme.exists()) {
            readme.writeText("""# Welcome to Alpine Editor! 🏔️

Alpine Editor is an upgraded, mobile-first code development environment featuring:
- **Pristine Dark Theme**: A beautifully spaced Material 3 layout.
- **Syntactic Highlights**: Visual coloring for Python, JS, Kotlin, and HTML.
- **Interactive Terminal**: An Alpine Linux shell simulator with package manager (`apk`).
- **Autonomous AI Agent**: Run `/agent <task>` in the chat to let the agent work!

---

### Getting Started:
1. Open files from the File Explorer in the sidebar.
2. Type terminal commands like `ls`, `cat README.md`, or `apk add python3`.
3. Put your Gemini API Key in Settings to enable the AI Agent!
""")
        }

        // 2. Create main.py
        val mainPy = File(root, "main.py")
        if (!mainPy.exists()) {
            mainPy.writeText("""# Simple Python Script
def greet(name):
    print(f"Hello, {name} from Alpine Linux!")

def calculate_factorial(n):
    if n <= 1:
        return 1
    return n * calculate_factorial(n - 1)

if __name__ == "__main__":
    greet("Developer")
    num = 5
    print(f"Factorial of {num} is: {calculate_factorial(num)}")
""")
        }

        // 3. Create Fibonacci.kt
        val fibKt = File(root, "Fibonacci.kt")
        if (!fibKt.exists()) {
            fibKt.writeText("""package com.example.workspace

fun main() {
    val limit = 10
    println("First ${'$'}{limit} Fibonacci numbers:")
    var t1 = 0
    var t2 = 1
    for (i in 1..limit) {
        print("${'$'}{t1} ")
        val sum = t1 + t2
        t1 = t2
        t2 = sum
    }
    println()
}
""")
        }

        // 4. Create server.js
        val serverJs = File(root, "server.js")
        if (!serverJs.exists()) {
            serverJs.writeText("""// Simple Node.js Server Simulation
const http = require('http');

const server = http.createServer((req, res) => {
    res.writeHead(200, {'Content-Type': 'text/plain'});
    res.end('Hello from Node.js running inside Alpine!\n');
});

const PORT = 3000;
console.log(`Server running on port ${'$'}{PORT}...`);
""")
        }
    }

    fun getRelativePath(context: Context, file: File): String {
        val rootPath = getWorkspaceRoot(context).absolutePath
        val filePath = file.absolutePath
        return if (filePath.startsWith(rootPath)) {
            filePath.substring(rootPath.length).trimStart('/')
        } else {
            filePath
        }
    }

    fun getFileFromRelative(context: Context, relPath: String): File {
        return File(getWorkspaceRoot(context), relPath.trimStart('/'))
    }

    fun createFile(context: Context, relPath: String, content: String = ""): File? {
        val file = getFileFromRelative(context, relPath)
        file.parentFile?.mkdirs()
        return try {
            file.writeText(content)
            file
        } catch (e: Exception) {
            null
        }
    }

    fun createDirectory(context: Context, relPath: String): File? {
        val dir = getFileFromRelative(context, relPath)
        return try {
            dir.mkdirs()
            dir
        } catch (e: Exception) {
            null
        }
    }

    fun deleteFile(context: Context, relPath: String): Boolean {
        val file = getFileFromRelative(context, relPath)
        return file.deleteRecursively()
    }

    fun readFile(context: Context, relPath: String): String {
        val file = getFileFromRelative(context, relPath)
        return if (file.exists() && file.isFile) {
            file.readText()
        } else {
            ""
        }
    }

    fun writeFile(context: Context, relPath: String, content: String) {
        val file = getFileFromRelative(context, relPath)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }

    fun getLanguageFromExtension(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "py" -> "python"
            "kt", "kts" -> "kotlin"
            "js", "jsx" -> "javascript"
            "ts", "tsx" -> "typescript"
            "java" -> "java"
            "html" -> "html"
            "css" -> "css"
            "md" -> "markdown"
            "json" -> "json"
            "c", "cpp", "h" -> "cpp"
            "xml" -> "xml"
            "sh" -> "bash"
            else -> "text"
        }
    }
}
