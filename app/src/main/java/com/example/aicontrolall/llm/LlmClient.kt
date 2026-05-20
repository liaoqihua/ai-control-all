package com.example.aicontrolall.llm

import com.example.aicontrolall.util.ConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class LlmClient(private val config: ConfigManager) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    suspend fun chat(messages: JSONArray, tools: JSONArray = JSONArray()): LlmResponse =
        withContext(Dispatchers.IO) {
            val requestBody = JSONObject().apply {
                put("model", config.model)
                put("messages", messages)
                put("temperature", 0.7)
                put("max_tokens", 2048)
                if (tools.length() > 0) {
                    put("tools", tools)
                    put("tool_choice", "auto")
                }
            }

            val request = Request.Builder()
                .url("${config.baseUrl}/v1/chat/completions")
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody(JSON_MEDIA))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw IOException("Empty response")

            if (!response.isSuccessful) {
                throw IOException("API error ${response.code}: $body")
            }

            parseResponse(JSONObject(body))
        }

    private fun parseResponse(json: JSONObject): LlmResponse {
        val choices = json.getJSONArray("choices")
        if (choices.length() == 0) return LlmResponse("")

        val choice = choices.getJSONObject(0)
        val message = choice.getJSONObject("message")

        val content = message.optString("content", "")
        val rawToolCalls = message.optJSONArray("tool_calls")
        val reasoningContent = message.optString("reasoning_content", null)

        // Build simplified format for McpGateway (name + arguments)
        val simplifiedTools = if (rawToolCalls != null && rawToolCalls.length() > 0) {
            JSONArray().apply {
                for (i in 0 until rawToolCalls.length()) {
                    val tc = rawToolCalls.getJSONObject(i)
                    val func = tc.getJSONObject("function")
                    put(JSONObject().apply {
                        put("name", func.getString("name"))
                        put("arguments", JSONObject(func.getString("arguments")))
                    })
                }
            }
        } else null

        return LlmResponse(
            content = content,
            toolCalls = simplifiedTools,
            rawToolCalls = rawToolCalls,
            reasoningContent = reasoningContent
        )
    }
}
