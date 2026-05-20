package com.example.aicontrolall.ui

data class DeviceDriver(
    val id: String,
    val name: String,
    val type: String,
    val category: String,       // "内置" | "外设"
    val capabilities: List<String>,
    val status: String,
    val dataFields: List<String>,
    val mcpTool: String?,
    val mcpToolStatus: String   // "registered" | "planned"
)
