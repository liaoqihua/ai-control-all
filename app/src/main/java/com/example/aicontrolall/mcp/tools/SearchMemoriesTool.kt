package com.example.aicontrolall.mcp.tools

import com.example.aicontrolall.mcp.McpTool
import com.example.aicontrolall.memory.MemoryStore
import com.example.aicontrolall.memory.SessionStore
import org.json.JSONArray
import org.json.JSONObject

class SearchMemoriesTool(
    private val memoryStore: MemoryStore,
    private val sessionStore: SessionStore
) : McpTool {
    override val name = "search_memories"
    override val description = "搜索持久记忆和会话历史，返回相关的事实和历史对话。用于 AI 回忆之前和用户的互动。"
    override val parameters = """{"type":"object","properties":{"query":{"type":"string","description":"搜索关键词"}},"required":["query"]}"""

    override suspend fun execute(args: JSONObject): String {
        val query = args.optString("query", "")
        if (query.isBlank()) return "Error: query is required"

        val memories = memoryStore.search(query, limit = 5)
        val messages = sessionStore.searchMessages(query, limit = 5)

        val result = JSONObject()
        result.put("memories", JSONArray().apply {
            memories.forEach { put(it.content) }
        })
        result.put("messages", JSONArray().apply {
            messages.forEach { put("[${it.role}] ${it.content}") }
        })

        return result.toString()
    }
}
