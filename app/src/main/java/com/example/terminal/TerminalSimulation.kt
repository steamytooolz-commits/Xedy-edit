package com.example.terminal

import android.content.Context
import com.example.ai.GeminiClient
import com.example.workspace.WorkspaceManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TerminalSimulation(
    private val context: Context,
    private val geminiClient: GeminiClient
) {
    private var currentPath = "" // Relative to workspace root
    private val installedPackages = mutableSetOf("git", "bash", "curl")

    fun getPrompt(): String {
        return "alpine-editor:${if (currentPath.isEmpty()) "~" else currentPath}$ "
    }

    suspend fun executeCommand(commandLine: String): String {
        val trimmed = commandLine.trim()
        if (trimmed.isEmpty()) return ""

        val parts = trimmed.split(Regex("\\s+"))
        val cmd = parts[0]
        val args = parts.drop(1)

        return when (cmd) {
            "help" -> {
                """
                |Alpine Linux v3.21 - Shell commands:
                |  help               Display this help guide.
                |  ls                 List directory contents.
                |  cd <dir>           Change workspace directory.
                |  pwd                Print current working directory.
                |  cat <file>         Concatenate and display file content.
                |  echo <text>        Display text line.
                |  clear              Clear terminal history.
                |  apk info           List installed packages.
                |  apk add <package>  Install alpine packages (e.g., python3, nodejs, clang, ollama).
                |  python3 <file>     Run Python script (requires apk add python3).
                |  node <file>        Run JavaScript script (requires apk add nodejs).
                |  g++ <file>         Compile and run C++ code (requires apk add clang).
                |  ollama run <model> Run LLM inside Alpine (requires apk add ollama).
                |  git <ops>          Simulated git operations (git status, log, commit).
                |
                |Installed utilities: ${installedPackages.joinToString(", ")}
                """.trimMargin()
            }
            "ls" -> {
                val dir = File(WorkspaceManager.getWorkspaceRoot(context), currentPath)
                val files = dir.listFiles()
                if (files.isNullOrEmpty()) {
                    "No files found."
                } else {
                    files.joinToString("\n") { file ->
                        val prefix = if (file.isDirectory) "d " else "- "
                        val size = if (file.isDirectory) "" else " (${file.length()} B)"
                        "$prefix${file.name}$size"
                    }
                }
            }
            "cd" -> {
                if (args.isEmpty()) {
                    currentPath = ""
                    ""
                } else {
                    val target = args[0]
                    if (target == "..") {
                        if (currentPath.isNotEmpty()) {
                            val idx = currentPath.lastIndexOf('/')
                            currentPath = if (idx == -1) "" else currentPath.substring(0, idx)
                        }
                        ""
                    } else {
                        val newDir = File(File(WorkspaceManager.getWorkspaceRoot(context), currentPath), target)
                        if (newDir.exists() && newDir.isDirectory) {
                            currentPath = if (currentPath.isEmpty()) target else "$currentPath/$target"
                            ""
                        } else {
                            "cd: no such file or directory: $target"
                        }
                    }
                }
            }
            "pwd" -> {
                "/home/alpine/workspace${if (currentPath.isEmpty()) "" else "/$currentPath"}"
            }
            "cat" -> {
                if (args.isEmpty()) return "cat: file argument required"
                val file = File(File(WorkspaceManager.getWorkspaceRoot(context), currentPath), args[0])
                if (file.exists() && file.isFile) {
                    file.readText()
                } else {
                    "cat: ${args[0]}: No such file"
                }
            }
            "echo" -> {
                args.joinToString(" ")
            }
            "apk" -> {
                if (args.isEmpty()) return "apk: options required (info, add)"
                when (args[0]) {
                    "info" -> "Installed Packages:\n" + installedPackages.joinToString("\n") { "  - $it" }
                    "add" -> {
                        if (args.size < 2) return "apk add: package name required"
                        val pkg = args[1].lowercase()
                        if (installedPackages.contains(pkg)) {
                            "package $pkg is already installed."
                        } else {
                            installedPackages.add(pkg)
                            """
                            |fetch https://dl-cdn.alpinelinux.org/alpine/v3.21/main/aarch64/APKINDEX.tar.gz
                            |fetch https://dl-cdn.alpinelinux.org/alpine/v3.21/community/aarch64/APKINDEX.tar.gz
                            |(1/3) Downloading $pkg package files...
                            |(2/3) Extracting $pkg and its libraries to rootfs...
                            |(3/3) Configuring local environment paths...
                            |Successfully installed $pkg package.
                            """.trimMargin()
                        }
                    }
                    else -> "apk: unknown command ${args[0]}"
                }
            }
            "python3", "python" -> {
                if (!installedPackages.contains("python3")) {
                    return "bash: python3: command not found (hint: run 'apk add python3')"
                }
                if (args.isEmpty()) return "python3: script file required"
                runCodeFile(args[0], "python")
            }
            "node" -> {
                if (!installedPackages.contains("nodejs")) {
                    return "bash: node: command not found (hint: run 'apk add nodejs')"
                }
                if (args.isEmpty()) return "node: script file required"
                runCodeFile(args[0], "javascript")
            }
            "g++", "gcc" -> {
                if (!installedPackages.contains("clang")) {
                    return "bash: g++: command not found (hint: run 'apk add clang')"
                }
                if (args.isEmpty()) return "g++: source file required"
                runCodeFile(args[0], "cpp")
            }
            "ollama" -> {
                if (!installedPackages.contains("ollama")) {
                    return "bash: ollama: command not found (hint: run 'apk add ollama')"
                }
                if (args.isEmpty()) return "ollama: options required (run, pull)"
                when (args[0]) {
                    "pull" -> {
                        if (args.size < 2) return "ollama pull: model name required"
                        val model = args[1]
                        """
                        |pulling manifest 
                        |pulling 51113c49e25d... 100% 4.7 GB/4.7 GB          
                        |pulling f2b489da6749... 100% 1.4 KB/1.4 KB          
                        |pulling 7643f8e57628... 100% 1.4 KB/1.4 KB          
                        |verifying sha256 digest 
                        |writing manifest 
                        |removing any unused layers 
                        |success: pulled model '$model'
                        """.trimMargin()
                    }
                    "run" -> {
                        if (args.size < 2) return "ollama run: model name required"
                        val model = args[1]
                        val prompt = args.drop(2).joinToString(" ")
                        if (prompt.isEmpty()) {
                            ">>> Interactive local model session for '$model' opened. (Type /exit to close)"
                        } else {
                            runOllamaPrompt(model, prompt)
                        }
                    }
                    else -> "ollama: unknown option ${args[0]}"
                }
            }
            "git" -> {
                if (args.isEmpty()) return "git: option required (status, log, commit, add, init)"
                when (args[0]) {
                    "init" -> "Initialized empty Git repository in /home/alpine/workspace/.git/"
                    "status" -> {
                        """
                        |On branch main
                        |Your branch is up to date with 'origin/main'.
                        |
                        |Changes not staged for commit:
                        |  (use "git add <file>..." to update what will be committed)
                        |	modified:   README.md
                        |
                        |no changes added to commit (use "git add" and/or "git commit -a")
                        """.trimMargin()
                    }
                    "add" -> "Staged changes for commit."
                    "commit" -> {
                        val msg = args.drop(1).joinToString(" ").removeSurrounding("\"")
                        if (msg.isEmpty()) return "git commit: message required"
                        """
                        |[main 3e51aef] $msg
                        | 1 file changed, 1 insertion(+), 1 deletion(-)
                        """.trimMargin()
                    }
                    "log" -> {
                        val sdf = SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy Z", Locale.US)
                        """
                        |commit 3e51aefb2a95dfce78a1523910cde1058bb38f12 (HEAD -> main)
                        |Author: Alpine Developer <developer@alpine.org>
                        |Date:   ${sdf.format(Date())}
                        |
                        |    Refactored file structures
                        |
                        |commit ad714ba3612718e24ef130f1d07283cde91f074a
                        |Author: Alpine Developer <developer@alpine.org>
                        |Date:   Wed Jun 10 14:20:11 2026 -0700
                        |
                        |    Initial commit
                        """.trimMargin()
                    }
                    else -> "git: unknown operation ${args[0]}"
                }
            }
            else -> {
                "bash: $cmd: command not found"
            }
        }
    }

    private suspend fun runCodeFile(fileName: String, language: String): String {
        val file = File(File(WorkspaceManager.getWorkspaceRoot(context), currentPath), fileName)
        if (!file.exists() || !file.isFile) {
            return "bash: $fileName: No such file"
        }

        val code = file.readText()

        // Real Gemini-based code execution simulation!
        val systemInstruction = """
            You are a Linux compilation and execution terminal simulation. 
            The user is running a $language program. 
            Simulate compiling and running this code in Alpine Linux inside the `/home/alpine/workspace` directory.
            Output ONLY the standard output (stdout) and standard error (stderr) generated by executing this program.
            If there are any logical or syntax errors, output the precise compiler/runtime errors that would occur.
            Do not include any greeting, explanation, or markdown formatting -- output only the raw terminal stream.
        """.trimIndent()

        val prompt = "Execute this $language program:\n\n```$language\n$code\n```"

        val response = geminiClient.generateContent(systemInstruction, prompt)

        return if (response.startsWith("Error:") || response.startsWith("API Error")) {
            // Fallback for offline usage
            fallbackLocalRun(code, language)
        } else {
            response.trim()
        }
    }

    private fun fallbackLocalRun(code: String, language: String): String {
        // Fallback: parse basic print/console.log commands in Python or Javascript
        val output = mutableListOf<String>()
        val lines = code.lines()
        for (line in lines) {
            val trimmedLine = line.trim()
            if (language == "python" && trimmedLine.startsWith("print(")) {
                val content = trimmedLine.removePrefix("print(").removeSuffix(")").trim().removeSurrounding("\"").removeSurrounding("'")
                output.add(content)
            } else if (language == "javascript" && trimmedLine.startsWith("console.log(")) {
                val content = trimmedLine.removePrefix("console.log(").removeSuffix(")").trim().removeSurrounding("\"").removeSurrounding("'")
                output.add(content)
            }
        }
        return if (output.isEmpty()) {
            "Compilation complete. Executed program successfully with no output (offline fallback)."
        } else {
            output.joinToString("\n")
        }
    }

    private suspend fun runOllamaPrompt(model: String, prompt: String): String {
        val systemInstruction = """
            You are an on-device local LLM model named '$model' running inside an Alpine terminal session.
            Answer the user's prompt succinctly and technically, as fits a CLI model. Keep the answer brief (max 2-3 short paragraphs).
        """.trimIndent()

        return geminiClient.generateContent(systemInstruction, prompt)
    }
}
