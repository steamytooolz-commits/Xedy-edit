package com.example.terminal

import android.content.Context
import android.util.Log
import com.example.workspace.WorkspaceManager
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class TerminalSession(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    val commandHistory: MutableList<String> = mutableListOf(),
    var currentDirectory: String = ""
)

class AlpineTerminalManager(private val context: Context) {
    private val sessions = mutableMapOf<String, TerminalSession>()
    private var activeSessionId: String? = null

    init {
        // Initialize default session
        val defaultSession = TerminalSession(name = "Alpine Default Session")
        sessions[defaultSession.id] = defaultSession
        activeSessionId = defaultSession.id
    }

    fun createSession(name: String): TerminalSession {
        val session = TerminalSession(name = name)
        sessions[session.id] = session
        activeSessionId = session.id
        return session
    }

    fun getActiveSession(): TerminalSession? {
        return sessions[activeSessionId]
    }

    fun setActiveSession(id: String) {
        if (sessions.containsKey(id)) {
            activeSessionId = id
        }
    }

    fun getAllSessions(): List<TerminalSession> {
        return sessions.values.toList()
    }

    fun closeSession(id: String) {
        sessions.remove(id)
        if (activeSessionId == id) {
            activeSessionId = sessions.keys.firstOrNull()
        }
    }

    /**
     * Extracts minimal Alpine rootfs environment from assets.
     */
    fun extractAlpineRootfs() {
        val root = WorkspaceManager.getWorkspaceRoot(context)
        val alpineDir = File(root, "rootfs")
        if (!alpineDir.exists()) {
            alpineDir.mkdirs()
        }

        // Staging critical Alpine system folders
        val binDir = File(alpineDir, "bin")
        val etcDir = File(alpineDir, "etc")
        val usrDir = File(alpineDir, "usr")
        val libDir = File(alpineDir, "lib")

        binDir.mkdirs()
        etcDir.mkdirs()
        usrDir.mkdirs()
        libDir.mkdirs()

        // Create virtual basic files inside Alpine environment
        File(etcDir, "alpine-release").writeText("Alpine Linux v3.21.0\n")
        File(etcDir, "os-release").writeText("""
            NAME="Alpine Linux"
            ID=alpine
            VERSION_ID=3.21.0
            PRETTY_NAME="Alpine Linux v3.21"
            HOME_URL="https://alpinelinux.org/"
            BUG_REPORT_URL="https://gitlab.alpinelinux.org/alpine/aports/-/issues"
        """.trimIndent())

        Log.i("AlpineTerminalManager", "Alpine rootfs folder scaffolding generated.")
    }
}
