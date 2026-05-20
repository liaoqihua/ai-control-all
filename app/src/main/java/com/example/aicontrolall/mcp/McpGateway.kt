package com.example.aicontrolall.mcp

import org.json.JSONArray
import org.json.JSONObject

class McpGateway {

    private val tools = mutableMapOf<String, McpTool>()

    fun register(tool: McpTool) {
        tools[tool.name] = tool
    }

    fun unregister(name: String) {
        tools.remove(name)
    }

    fun listTools(): List<McpTool> = tools.values.toList()

    fun getTool(name: String): McpTool? = tools[name]

    /**
     * 生成所有工具签名摘要，注入到 LLM system prompt
     */
    fun buildToolsPrompt(): String {
        if (tools.isEmpty()) return "No tools available."
        return tools.values.joinToString("\n\n") { it.toPrompt() }
    }

    /**
     * 批量执行 LLM 返回的工具调用
     * @param toolCalls JSONArray，每项格式: {"name":"...","arguments":{...}}
     * @return 每个工具调用的结果列表
     */
    suspend fun executeBatch(toolCalls: JSONArray): List<ToolResult> {
        val results = mutableListOf<ToolResult>()
        for (i in 0 until toolCalls.length()) {
            val call = toolCalls.getJSONObject(i)
            val name = call.optString("name")
            val args = call.optJSONObject("arguments") ?: JSONObject()
            val tool = tools[name]
            if (tool == null) {
                results.add(ToolResult(name, false, "Tool not found: $name"))
                continue
            }
            try {
                val output = tool.execute(args)
                results.add(ToolResult(name, true, output))
            } catch (e: Exception) {
                results.add(ToolResult(name, false, "Error: ${e.message}"))
            }
        }
        return results
    }

    data class ToolResult(
        val toolName: String,
        val success: Boolean,
        val output: String
    )
}
