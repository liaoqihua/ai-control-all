package com.example.aicontrolall.core

import com.example.aicontrolall.mcp.McpGateway
import com.example.aicontrolall.memory.MemoryStore
import com.example.aicontrolall.memory.SessionStore
import com.example.aicontrolall.memory.SkillStore
import org.json.JSONArray
import org.json.JSONObject

class PromptBuilder(
    private val memoryStore: MemoryStore,
    private val skillStore: SkillStore,
    private val sessionStore: SessionStore,
    private val mcpGateway: McpGateway
) {
    companion object {
        val SYSTEM_PROMPT = """
你是一个运行在用户手机上的 AI 助手。你可以通过工具控制手机的硬件设备（摄像头、扬声器、麦克风等）以及其他连接的智能设备。
你的记忆系统记录了关于用户的事实和信息，你会在每次对话开始时自动获得相关记忆。
你有能力从对话中学习——当你发现新的事实时会将它们存入记忆，当你发现某种工作模式重复出现时会将其提炼为技能。

重要规则：
1. 你是用户的伙伴，不是冷冰冰的工具。用自然亲切的中文回复。
2. 读取已知事实部分，如果其中有和用户当前请求相关的信息，直接使用它，不需要重复询问用户。
3. 如果用户的请求涉及设备控制，调用对应的 tool。不要凭空编造结果。
4. 如果对话中出现用户的新偏好、新信息、或环境变化，记录下来（但不要在回复里说你正在记录）。
5. 如果你不确定某个设备是否可用，先调用 search_memories 查一下。
""".trimIndent()
    }

    fun build(
        userInput: String,
        sessionId: String,
        memoriesQuery: String = userInput
    ): JSONArray {
        val messages = JSONArray()

        // 1. System message
        val systemContent = buildSystemContent(memoriesQuery)
        messages.put(JSONObject().apply {
            put("role", "system")
            put("content", systemContent)
        })

        // 2. Recent conversation history (last 10 messages)
        val recentMessages = sessionStore.getRecentMessages(sessionId, limit = 10)
        for (msg in recentMessages) {
            messages.put(JSONObject().apply {
                put("role", msg.role)
                put("content", msg.content)
            })
        }

        // 3. Current user input
        messages.put(JSONObject().apply {
            put("role", "user")
            put("content", userInput)
        })

        return messages
    }

    fun buildTools(): JSONArray {
        val tools = JSONArray()
        for (tool in mcpGateway.listTools()) {
            tools.put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", tool.name)
                    put("description", tool.description)
                    put("parameters", JSONObject(tool.parameters))
                })
            })
        }
        return tools
    }

    private fun buildSystemContent(memoriesQuery: String): String {
        val sb = StringBuilder()
        sb.appendLine(SYSTEM_PROMPT)
        sb.appendLine()

        // 注入相关记忆
        val memories = memoryStore.search(memoriesQuery, limit = 10)
            .ifEmpty { memoryStore.getRecent(limit = 5) }
        if (memories.isNotEmpty()) {
            sb.appendLine("## 已知事实")
            for (mem in memories) {
                sb.appendLine("- ${mem.content}")
            }
            sb.appendLine()
        }

        // 注入可用技能
        val skills = skillStore.getAll(limit = 10)
        if (skills.isNotEmpty()) {
            sb.appendLine("## 可用技能")
            for (skill in skills) {
                sb.appendLine("### ${skill.title} (置信度: ${"%.0f".format(skill.confidence * 100)}%)")
                sb.appendLine(skill.steps)
                if (skill.pitfalls.isNotBlank()) {
                    sb.appendLine("⚠ 注意: ${skill.pitfalls}")
                }
                sb.appendLine()
            }
        }

        // 注入可用 MCP 工具
        val toolsPrompt = mcpGateway.buildToolsPrompt()
        if (toolsPrompt.isNotBlank()) {
            sb.appendLine("## 可用设备工具")
            sb.appendLine(toolsPrompt)
        }

        return sb.toString()
    }
}
