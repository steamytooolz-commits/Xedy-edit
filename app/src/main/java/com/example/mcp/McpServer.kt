package com.example.mcp

import android.content.Context
import com.example.workspace.WorkspaceManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object McpServer {

    /**
     * Handles JSON-RPC 2.0 requests matching Model Context Protocol (MCP).
     */
    fun handleRequest(context: Context, jsonRequestStr: String, executeTerminal: (String) -> String): String {
        return try {
            val request = JSONObject(jsonRequestStr)
            val id = request.opt("id")
            val method = request.optString("method", "")
            val params = request.optJSONObject("params") ?: JSONObject()

            val response = JSONObject()
            response.put("jsonrpc", "2.0")
            response.put("id", id)

            when (method) {
                "tools/list" -> {
                    val result = JSONObject()
                    val tools = JSONArray()

                    // Tool 1: list_files
                    tools.put(JSONObject().apply {
                        put("name", "list_files")
                        put("description", "Recursively lists all files inside the workspace.")
                        put("inputSchema", JSONObject().apply {
                            put("type", "object")
                            put("properties", JSONObject())
                        })
                    })

                    // Tool 2: read_file
                    tools.put(JSONObject().apply {
                        put("name", "read_file")
                        put("description", "Reads the text contents of a file in the workspace.")
                        put("inputSchema", JSONObject().apply {
                            put("type", "object")
                            put("required", JSONArray().put("path"))
                            put("properties", JSONObject().apply {
                                put("path", JSONObject().apply {
                                    put("type", "string")
                                    put("description", "Relative file path inside workspace.")
                                })
                            })
                        })
                    })

                    // Tool 3: write_file
                    tools.put(JSONObject().apply {
                        put("name", "write_file")
                        put("description", "Overwrites or creates a file with specified content.")
                        put("inputSchema", JSONObject().apply {
                            put("type", "object")
                            put("required", JSONArray().put("path").put("content"))
                            put("properties", JSONObject().apply {
                                put("path", JSONObject().apply { put("type", "string") })
                                put("content", JSONObject().apply { put("type", "string") })
                            })
                        })
                    })

                    // Tool 4: terminal_exec
                    tools.put(JSONObject().apply {
                        put("name", "terminal_exec")
                        put("description", "Runs a shell command in the simulated Alpine Linux terminal.")
                        put("inputSchema", JSONObject().apply {
                            put("type", "object")
                            put("required", JSONArray().put("command"))
                            put("properties", JSONObject().apply {
                                put("command", JSONObject().apply { put("type", "string") })
                            })
                        })
                    })

                    result.put("tools", tools)
                    response.put("result", result)
                }

                "tools/call" -> {
                    val name = params.optString("name", "")
                    val arguments = params.optJSONObject("arguments") ?: JSONObject()
                    val result = JSONObject()

                    val textOutput = when (name) {
                        "list_files" -> {
                            val root = WorkspaceManager.getWorkspaceRoot(context)
                            val files = root.walkTopDown().filter { it.isFile }.toList()
                            if (files.isEmpty()) "Empty" else files.joinToString("\n") { WorkspaceManager.getRelativePath(context, it) }
                        }
                        "read_file" -> {
                            val path = arguments.optString("path", "")
                            WorkspaceManager.readFile(context, path)
                        }
                        "write_file" -> {
                            val path = arguments.optString("path", "")
                            val content = arguments.optString("content", "")
                            WorkspaceManager.writeFile(context, path, content)
                            "Wrote successfully to $path"
                        }
                        "terminal_exec" -> {
                            val cmd = arguments.optString("command", "")
                            executeTerminal(cmd)
                        }
                        else -> "Error: Unknown tool $name"
                    }

                    val contentArr = JSONArray().put(JSONObject().apply {
                        put("type", "text")
                        put("text", textOutput)
                    })
                    result.put("content", contentArr)
                    response.put("result", result)
                }

                "resources/list" -> {
                    val result = JSONObject()
                    val resources = JSONArray()
                    val root = WorkspaceManager.getWorkspaceRoot(context)
                    root.walkTopDown().filter { it.isFile }.forEach { file ->
                        val rel = WorkspaceManager.getRelativePath(context, file)
                        resources.put(JSONObject().apply {
                            put("uri", "workspace://$rel")
                            put("name", file.name)
                            put("mimeType", "text/plain")
                        })
                    }
                    result.put("resources", resources)
                    response.put("result", result)
                }

                "resources/read" -> {
                    val uri = params.optString("uri", "")
                    val rel = uri.replace("workspace://", "")
                    val content = WorkspaceManager.readFile(context, rel)
                    
                    val result = JSONObject()
                    val contentsArr = JSONArray().put(JSONObject().apply {
                        put("uri", uri)
                        put("text", content)
                    })
                    result.put("contents", contentsArr)
                    response.put("result", result)
                }

                else -> {
                    val err = JSONObject()
                    err.put("code", -32601)
                    err.put("message", "Method not found: $method")
                    response.put("error", err)
                }
            }

            response.toString()
        } catch (e: Exception) {
            val response = JSONObject()
            response.put("jsonrpc", "2.0")
            val err = JSONObject()
            err.put("code", -32603)
            err.put("message", "Internal error: ${e.message}")
            response.put("error", err)
            response.toString()
        }
    }
}
