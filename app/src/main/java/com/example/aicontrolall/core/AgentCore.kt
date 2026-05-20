package com.example.aicontrolall.core

import com.example.aicontrolall.evolution.EvolutionCycle
import com.example.aicontrolall.llm.LlmClient
import com.example.aicontrolall.mcp.McpGateway
import com.example.aicontrolall.memory.MemoryStore
import com.example.aicontrolall.memory.SessionStore
import com.example.aicontrolall.memory.SkillStore
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

class AgentCore(
    private val config: AgentConfig,
    private val memoryStore: MemoryStore,
    private val skillStore: SkillStore,
    private val sessionStore: SessionStore,
    private val mcpGateway: McpGateway,
    private val llmClient: LlmClient,
    private val promptBuilder: PromptBuilder,
    private val evolutionCycle: EvolutionCycle
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    data class AgentResult(
        val reply: String,
        val toolResults: List<McpGateway.ToolResult> = emptyList()
    )

    suspend fun processInput(
        userInput: String,
        sessionId: String
    ): AgentResult = withContext(Dispatchers.IO) {
        // 1. 保存用户消息
        sessionStore.addMessage(sessionId, "user", userInput)
        if (sessionStore.getRecentMessages(sessionId, 2).size <= 1) {
            sessionStore.updateSessionTitle(sessionId, userInput.take(30))
        }

        // 2. 组装 prompt
        val messages = promptBuilder.build(userInput, sessionId)
        val tools = promptBuilder.buildTools()

        // 3. 调用 LLM
        val response = llmClient.chat(messages, tools)

        // 4. 如果有工具调用，先执行
        val toolResults = mutableListOf<McpGateway.ToolResult>()
        if (response.toolCalls != null && response.toolCalls.length() > 0) {
            val results = mcpGateway.executeBatch(response.toolCalls)
            toolResults.addAll(results)

            // Send assistant message with tool_calls back to API
            val assistantMsg = JSONObject().apply {
                put("role", "assistant")
                put("content", JSONObject.NULL)
                put("tool_calls", response.rawToolCalls)
                if (response.reasoningContent != null) {
                    put("reasoning_content", response.reasoningContent)
                }
            }
            messages.put(assistantMsg)

            // Send tool result messages, matching id from rawToolCalls
            if (response.rawToolCalls != null) {
                for (i in 0 until minOf(results.size, response.rawToolCalls.length())) {
                    val rawTc = response.rawToolCalls.getJSONObject(i)
                    val callId = rawTc.getString("id")
                    messages.put(JSONObject().apply {
                        put("role", "tool")
                        put("content", results[i].output)
                        put("tool_call_id", callId)
                    })
                }
            }

            val secondResponse = llmClient.chat(messages, JSONArray())
            val finalReply = secondResponse.content

            sessionStore.addMessage(sessionId, "assistant", finalReply)

            if (config.evolutionEnabled) {
                evolutionCycle.evolve(
                    userInput = userInput,
                    assistantResponse = finalReply,
                    toolResults = results.map { it.output },
                    sessionId = sessionId
                )
            }

            AgentResult(reply = finalReply, toolResults = results)
        } else {
            sessionStore.addMessage(sessionId, "assistant", response.content)

            if (config.evolutionEnabled) {
                evolutionCycle.evolve(
                    userInput = userInput,
                    assistantResponse = response.content,
                    toolResults = emptyList(),
                    sessionId = sessionId
                )
            }

            AgentResult(reply = response.content)
        }
    }

    fun getStatus(): String {
        return buildString {
            appendLine("=== AiControlAll Agent Status ===")
            appendLine("Memories: ${memoryStore.count()}")
            appendLine("Skills: ${skillStore.count()}")
            appendLine("Tools: ${mcpGateway.listTools().size}")
            appendLine("Model: ${config.model}")
            appendLine("Evolution: ${if (config.evolutionEnabled) "enabled" else "disabled"}")
        }
    }

    fun shutdown() {
        scope.cancel()
    }
}
