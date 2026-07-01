package com.example.workspace

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object ExternalStorageBridge {
    private const val TAG = "ExternalStorageBridge"

    /**
     * Checks if external storage is writable.
     */
    fun isExternalStorageWritable(): Boolean {
        val state = Environment.getExternalStorageState()
        return Environment.MEDIA_MOUNTED == state
    }

    /**
     * Checks if external storage is readable.
     */
    fun isExternalStorageReadable(): Boolean {
        val state = Environment.getExternalStorageState()
        return Environment.MEDIA_MOUNTED == state || Environment.MEDIA_MOUNTED_READ_ONLY == state
    }

    /**
     * Returns the canonical path to the external storage (/storage/emulated/0 or /sdcard).
     */
    fun getExternalStoragePath(): String {
        return Environment.getExternalStorageDirectory().absolutePath
    }

    /**
     * Checks if broad external storage manager permission (all files access) is granted on Android 11+.
     */
    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true // Prior to Android 11, standard runtime storage permissions are sufficient
        }
    }

    /**
     * Exports the internal Alpine workspace to the user's shared Download folder.
     * This provides direct access to the files via PC or standard file manager app.
     */
    fun exportWorkspaceToDownloads(context: Context): String {
        if (!isExternalStorageWritable()) {
            return "Error: External storage is not writable/mounted."
        }

        try {
            val workspaceRoot = WorkspaceManager.getWorkspaceRoot(context)
            // Save to /storage/emulated/0/Download/AlpineEditor_Backup or /sdcard/Download/AlpineEditor_Backup
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val backupDir = File(downloadsDir, "AlpineEditor_Backup")
            
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }

            copyDirectory(workspaceRoot, backupDir)
            Log.i(TAG, "Workspace exported successfully to: ${backupDir.absolutePath}")
            return "Successfully exported workspace to:\n${backupDir.absolutePath}"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export workspace", e)
            return "Failed to export: ${e.message}"
        }
    }

    /**
     * Imports files from a specified folder in external storage (/sdcard/ or /storage/emulated/0/...)
     * into the internal app workspace.
     */
    fun importWorkspaceFromPath(context: Context, externalPath: String): String {
        if (!isExternalStorageReadable()) {
            return "Error: External storage is not readable."
        }

        try {
            val sourceDir = File(externalPath)
            if (!sourceDir.exists()) {
                return "Error: Source directory does not exist: $externalPath"
            }
            if (!sourceDir.isDirectory) {
                return "Error: Source is not a directory: $externalPath"
            }

            val workspaceRoot = WorkspaceManager.getWorkspaceRoot(context)
            copyDirectory(sourceDir, workspaceRoot)
            
            Log.i(TAG, "Workspace imported successfully from: ${sourceDir.absolutePath}")
            return "Successfully imported files into workspace from:\n${sourceDir.absolutePath}"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import workspace", e)
            return "Failed to import: ${e.message}"
        }
    }

    /**
     * Helper to recursively copy directories.
     */
    private fun copyDirectory(source: File, destination: File) {
        if (source.isDirectory) {
            if (!destination.exists()) {
                destination.mkdirs()
            }
            val children = source.list() ?: return
            for (child in children) {
                // Skip system/internal structures that don't need to be exposed
                if (child == "rootfs" || child == ".gradle" || child == ".git" || child == "tmp") {
                    continue
                }
                copyDirectory(File(source, child), File(destination, child))
            }
        } else {
            // It's a file, copy content
            FileInputStream(source).use { input ->
                FileOutputStream(destination).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
}
