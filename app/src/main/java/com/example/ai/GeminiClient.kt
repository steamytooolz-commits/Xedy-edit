package com.example.ai

import android.content.Context
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.ChatMessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class GeminiClient(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun getActiveProvider(): String {
        return db.settingDao().getSetting("ai_provider")?.value ?: "gemini"
    }

    private suspend fun getProviderApiKey(provider: String): String {
        val dbKey = db.settingDao().getSetting("${provider}_api_key")?.value
        if (!dbKey.isNullOrBlank()) {
            return dbKey
        }
        if (provider == "gemini") {
            return try {
                val buildConfigClass = Class.forName("com.example.BuildConfig")
                val field = buildConfigClass.getField("GEMINI_API_KEY")
                field.get(null) as String
            } catch (e: Exception) {
                ""
            }
        }
        return ""
    }

    private suspend fun getProviderModel(provider: String): String {
        val model = db.settingDao().getSetting("${provider}_model")?.value
        if (!model.isNullOrBlank()) {
            return model
        }
        return when (provider) {
            "gemini" -> "gemini-3.5-flash"
            "nvidia" -> "nvidia/llama-3.1-nemotron-70b-instruct"
            "openai" -> "gpt-4o-mini"
            "anthropic" -> "claude-3-5-sonnet-20240620"
            "custom" -> "custom-model"
            else -> "gemini-3.5-flash"
        }
    }

    private suspend fun getProviderBaseUrl(provider: String): String {
        val url = db.settingDao().getSetting("${provider}_base_url")?.value
        if (!url.isNullOrBlank()) {
            return url
        }
        return when (provider) {
            "nvidia" -> "https://integrate.api.nvidia.com/v1"
            "custom" -> "http://10.0.2.2:11434/v1"
            else -> ""
        }
    }

    suspend fun getSelectedModel(): String {
        val provider = getActiveProvider()
        return getProviderModel(provider)
    }

    /**
     * Unified generate content call routing to the active provider.
     */
    suspend fun generateContent(
        systemInstruction: String,
        prompt: String,
        history: List<ChatMessageEntity> = emptyList()
    ): String {
        val provider = getActiveProvider()
        val apiKey = getProviderApiKey(provider)
        val model = getProviderModel(provider)

        if (apiKey.isBlank() && provider != "custom") {
            return "Error: API Key for $provider is missing. Please configure it in the Settings screen."
        }

        return when (provider) {
            "gemini" -> generateGeminiContent(apiKey, model, systemInstruction, prompt, history)
            "nvidia", "openai", "custom" -> generateOpenAIStyleContent(provider, apiKey, model, systemInstruction, prompt, history)
            "anthropic" -> generateAnthropicContent(apiKey, model, systemInstruction, prompt, history)
            else -> "Error: Unknown AI Provider $provider"
        }
    }

    /**
     * Unified generate content stream call routing to the active provider.
     */
    fun generateContentStream(
        systemInstruction: String,
        prompt: String,
        history: List<ChatMessageEntity> = emptyList()
    ): Flow<String> = flow {
        val provider = getActiveProvider()
        val apiKey = getProviderApiKey(provider)
        val model = getProviderModel(provider)

        if (apiKey.isBlank() && provider != "custom") {
            emit("Error: API Key for $provider is missing. Please configure it in the Settings screen.")
            return@flow
        }

        val flowToCollect = when (provider) {
            "gemini" -> generateGeminiContentStream(apiKey, model, systemInstruction, prompt, history)
            "nvidia", "openai", "custom" -> generateOpenAIStyleStream(provider, apiKey, model, systemInstruction, prompt, history)
            "anthropic" -> generateAnthropicStream(apiKey, model, systemInstruction, prompt, history)
            else -> flow { emit("Error: Unknown AI Provider $provider") }
        }

        flowToCollect.collect { chunk ->
            emit(chunk)
        }
    }.flowOn(Dispatchers.IO)

    // --- Google Gemini Implementation ---

    private suspend fun generateGeminiContent(
        apiKey: String,
        model: String,
        systemInstruction: String,
        prompt: String,
        history: List<ChatMessageEntity>
    ): String = withContext(Dispatchers.IO) {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        try {
            val requestJson = JSONObject()

            if (systemInstruction.isNotEmpty()) {
                requestJson.put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().apply {
                        put("text", systemInstruction)
                    }))
                })
            }

            val contentsArray = JSONArray()
            history.forEach { msg ->
                if (msg.role == "user" || msg.role == "assistant" || msg.role == "model") {
                    contentsArray.put(JSONObject().apply {
                        put("role", if (msg.role == "assistant") "model" else msg.role)
                        put("parts", JSONArray().put(JSONObject().apply {
                            put("text", msg.content)
                        }))
                    })
                }
            }

            contentsArray.put(JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().apply {
                    put("text", prompt)
                }))
            })

            requestJson.put("contents", contentsArray)

            requestJson.put("generationConfig", JSONObject().apply {
                put("temperature", 0.2)
                put("maxOutputTokens", 8192)
            })

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext "Gemini Error (Code ${response.code}): $bodyString"
                }

                val jsonResponse = JSONObject(bodyString)
                val text = jsonResponse.optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")

                return@withContext text ?: "No response from model"
            }
        } catch (e: Exception) {
            Log.e("GeminiClient", "Gemini Generation failed", e)
            return@withContext "Error: ${e.localizedMessage ?: e.message}"
        }
    }

    private fun generateGeminiContentStream(
        apiKey: String,
        model: String,
        systemInstruction: String,
        prompt: String,
        history: List<ChatMessageEntity>
    ): Flow<String> = flow {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?key=$apiKey"
        val requestJson = JSONObject()

        if (systemInstruction.isNotEmpty()) {
            requestJson.put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply {
                    put("text", systemInstruction)
                }))
            })
        }

        val contentsArray = JSONArray()
        history.forEach { msg ->
            if (msg.role == "user" || msg.role == "assistant" || msg.role == "model") {
                contentsArray.put(JSONObject().apply {
                    put("role", if (msg.role == "assistant") "model" else msg.role)
                    put("parts", JSONArray().put(JSONObject().apply {
                        put("text", msg.content)
                    }))
                })
            }
        }

        contentsArray.put(JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(JSONObject().apply {
                put("text", prompt)
            }))
        })
        requestJson.put("contents", contentsArray)

        requestJson.put("generationConfig", JSONObject().apply {
            put("temperature", 0.3)
        })

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestJson.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emit("Error (Code ${response.code}): ${response.body?.string()}")
                    return@flow
                }

                val reader = BufferedReader(InputStreamReader(response.body?.byteStream()))
                var line: String?
                val buffer = StringBuilder()

                while (reader.readLine().also { line = it } != null) {
                    val trimmedLine = line?.trim() ?: ""
                    if (trimmedLine.startsWith("[")) {
                        continue
                    }
                    if (trimmedLine.startsWith(",")) {
                        buffer.append(trimmedLine.substring(1))
                    } else {
                        buffer.append(trimmedLine)
                    }

                    try {
                        val jsonStr = buffer.toString().trim()
                        if (jsonStr.endsWith("]") || jsonStr.endsWith("}")) {
                            val cleanJson = if (jsonStr.startsWith(",")) jsonStr.substring(1).trim() else jsonStr
                            val chunk = JSONObject(cleanJson)
                            val text = chunk.optJSONArray("candidates")
                                ?.optJSONObject(0)
                                ?.optJSONObject("content")
                                ?.optJSONArray("parts")
                                ?.optJSONObject(0)
                                ?.optString("text")
                            if (text != null) {
                                emit(text)
                            }
                            buffer.clear()
                        }
                    } catch (e: Exception) {
                        // Incomplete JSON buffer line
                    }
                }
            }
        } catch (e: Exception) {
            emit("Error: ${e.localizedMessage ?: e.message}")
        }
    }

    // --- NVIDIA, OpenAI and Custom OpenAI-Compatible Implementation ---

    private suspend fun generateOpenAIStyleContent(
        provider: String,
        apiKey: String,
        model: String,
        systemInstruction: String,
        prompt: String,
        history: List<ChatMessageEntity>
    ): String = withContext(Dispatchers.IO) {
        val baseUrl = getProviderBaseUrl(provider).ifEmpty {
            if (provider == "openai") "https://api.openai.com/v1" else ""
        }
        val url = "$baseUrl/chat/completions"

        try {
            val requestJson = JSONObject()
            requestJson.put("model", model)

            val messagesArray = JSONArray()

            // System instructions
            if (systemInstruction.isNotEmpty()) {
                messagesArray.put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemInstruction)
                })
            }

            // History
            history.forEach { msg ->
                messagesArray.put(JSONObject().apply {
                    put("role", if (msg.role == "user") "user" else "assistant")
                    put("content", msg.content)
                })
            }

            // Current prompt
            messagesArray.put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            })

            requestJson.put("messages", messagesArray)
            requestJson.put("temperature", 0.2)

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)

            val requestBuilder = Request.Builder()
                .url(url)
                .post(requestBody)

            if (apiKey.isNotEmpty()) {
                requestBuilder.header("Authorization", "Bearer $apiKey")
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                val bodyString = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext "$provider Error (Code ${response.code}): $bodyString"
                }

                val jsonResponse = JSONObject(bodyString)
                val text = jsonResponse.optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")

                return@withContext text ?: "No response from model"
            }
        } catch (e: Exception) {
            Log.e("GeminiClient", "OpenAI-Style Generation failed", e)
            return@withContext "Error: ${e.localizedMessage ?: e.message}"
        }
    }

    private fun generateOpenAIStyleStream(
        provider: String,
        apiKey: String,
        model: String,
        systemInstruction: String,
        prompt: String,
        history: List<ChatMessageEntity>
    ): Flow<String> = flow {
        val baseUrl = getProviderBaseUrl(provider).ifEmpty {
            if (provider == "openai") "https://api.openai.com/v1" else ""
        }
        val url = "$baseUrl/chat/completions"

        val requestJson = JSONObject()
        requestJson.put("model", model)
        requestJson.put("stream", true)

        val messagesArray = JSONArray()
        if (systemInstruction.isNotEmpty()) {
            messagesArray.put(JSONObject().apply {
                put("role", "system")
                put("content", systemInstruction)
            })
        }

        history.forEach { msg ->
            messagesArray.put(JSONObject().apply {
                put("role", if (msg.role == "user") "user" else "assistant")
                put("content", msg.content)
            })
        }

        messagesArray.put(JSONObject().apply {
            put("role", "user")
            put("content", prompt)
        })

        requestJson.put("messages", messagesArray)
        requestJson.put("temperature", 0.3)

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestJson.toString().toRequestBody(mediaType)

        val requestBuilder = Request.Builder()
            .url(url)
            .post(requestBody)

        if (apiKey.isNotEmpty()) {
            requestBuilder.header("Authorization", "Bearer $apiKey")
        }

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    emit("Error (Code ${response.code}): ${response.body?.string()}")
                    return@flow
                }

                val reader = BufferedReader(InputStreamReader(response.body?.byteStream()))
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    val rawLine = line?.trim() ?: ""
                    if (rawLine.startsWith("data:")) {
                        val cleanLine = rawLine.substring(5).trim()
                        if (cleanLine == "[DONE]") {
                            break
                        }
                        try {
                            val chunkJson = JSONObject(cleanLine)
                            val textChunk = chunkJson.optJSONArray("choices")
                                ?.optJSONObject(0)
                                ?.optJSONObject("delta")
                                ?.optString("content")
                            if (!textChunk.isNullOrEmpty()) {
                                emit(textChunk)
                            }
                        } catch (e: Exception) {
                            // Non-json chunk or noise
                        }
                    }
                }
            }
        } catch (e: Exception) {
            emit("Error: ${e.localizedMessage ?: e.message}")
        }
    }

    // --- Anthropic Claude Implementation ---

    private suspend fun generateAnthropicContent(
        apiKey: String,
        model: String,
        systemInstruction: String,
        prompt: String,
        history: List<ChatMessageEntity>
    ): String = withContext(Dispatchers.IO) {
        val url = "https://api.anthropic.com/v1/messages"

        try {
            val requestJson = JSONObject()
            requestJson.put("model", model)
            requestJson.put("max_tokens", 4096)
            if (systemInstruction.isNotEmpty()) {
                requestJson.put("system", systemInstruction)
            }

            val messagesArray = JSONArray()
            history.forEach { msg ->
                messagesArray.put(JSONObject().apply {
                    put("role", if (msg.role == "user") "user" else "assistant")
                    put("content", msg.content)
                })
            }

            messagesArray.put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            })

            requestJson.put("messages", messagesArray)
            requestJson.put("temperature", 0.2)

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    return@withContext "Anthropic Error (Code ${response.code}): $bodyString"
                }

                val jsonResponse = JSONObject(bodyString)
                val text = jsonResponse.optJSONArray("content")
                    ?.optJSONObject(0)
                    ?.optString("text")

                return@withContext text ?: "No response from model"
            }
        } catch (e: Exception) {
            Log.e("GeminiClient", "Anthropic Generation failed", e)
            return@withContext "Error: ${e.localizedMessage ?: e.message}"
        }
    }

    private fun generateAnthropicStream(
        apiKey: String,
        model: String,
        systemInstruction: String,
        prompt: String,
        history: List<ChatMessageEntity>
    ): Flow<String> = flow {
        val url = "https://api.anthropic.com/v1/messages"

        val requestJson = JSONObject()
        requestJson.put("model", model)
        requestJson.put("max_tokens", 4096)
        requestJson.put("stream", true)
        if (systemInstruction.isNotEmpty()) {
            requestJson.put("system", systemInstruction)
        }

        val messagesArray = JSONArray()
        history.forEach { msg ->
            messagesArray.put(JSONObject().apply {
                put("role", if (msg.role == "user") "user" else "assistant")
                put("content", msg.content)
            })
        }

        messagesArray.put(JSONObject().apply {
            put("role", "user")
            put("content", prompt)
        })

        requestJson.put("messages", messagesArray)
        requestJson.put("temperature", 0.3)

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestJson.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    emit("Error (Code ${response.code}): ${response.body?.string()}")
                    return@flow
                }

                val reader = BufferedReader(InputStreamReader(response.body?.byteStream()))
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    val rawLine = line?.trim() ?: ""
                    if (rawLine.startsWith("data:")) {
                        val cleanLine = rawLine.substring(5).trim()
                        try {
                            val dataJson = JSONObject(cleanLine)
                            if (dataJson.optString("type") == "content_block_delta") {
                                val textChunk = dataJson.optJSONObject("delta")?.optString("text")
                                if (!textChunk.isNullOrEmpty()) {
                                    emit(textChunk)
                                }
                            }
                        } catch (e: Exception) {
                            // Non-json chunk or noise
                        }
                    }
                }
            }
        } catch (e: Exception) {
            emit("Error: ${e.localizedMessage ?: e.message}")
        }
    }
}
