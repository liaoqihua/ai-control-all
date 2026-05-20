package com.example.aicontrolall.mcp

import org.json.JSONObject

/**
 * MCP 工具统一接口。
 * 每个设备/功能实现这个接口，McpGateway 自动发现和调度。
 */
interface McpTool {
    /** 工具名称，唯一标识，如 "capture_photo" */
    val name: String

    /** 人类可读描述，会注入到 LLM prompt 中 */
    val description: String

    /** 参数定义 JSON Schema，如 {"type":"object","properties":{"resolution":{"type":"string"}}} */
    val parameters: String

    /**
     * 执行工具调用
     * @param args 参数 JSON 对象
     * @return 执行结果字符串（成功返回结果，失败返回错误信息）
     */
    suspend fun execute(args: JSONObject): String

    /**
     * 生成给 LLM 看的工具签名
     */
    fun toPrompt(): String {
        return """
Tool: $name
Description: $description
Parameters: $parameters
""".trimIndent()
    }
}
