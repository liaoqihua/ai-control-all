package com.example.aicontrolall.llm

import org.json.JSONArray

data class LlmResponse(
    val content: String,
    val toolCalls: JSONArray? = null,
    val rawToolCalls: JSONArray? = null,
    val reasoningContent: String? = null  // DeepSeek thinking mode 需要回传
)
