package com.example.aicontrolall.mcp.tools

import android.content.Context
import com.example.aicontrolall.mcp.McpTool
import org.json.JSONObject

class CameraTool(private val context: Context) : McpTool {
    override val name = "capture_photo"
    override val description = "用手机摄像头拍一张照片，返回照片的文件路径"
    override val parameters = """{"type":"object","properties":{},"required":[]}"""

    override suspend fun execute(args: JSONObject): String {
        // Phase 1: 用 Camera Intent 拍照（后续可改为 CameraX 直接控制）
        return """{"status":"not_implemented","message":"Camera capture will be implemented with CameraX"}"""
    }
}
