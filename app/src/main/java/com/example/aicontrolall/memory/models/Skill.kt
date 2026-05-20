package com.example.aicontrolall.memory.models

data class Skill(
    val id: Long = 0,
    val name: String,           // 唯一标识符 e.g. "adjust-smart-light"
    val title: String,          // 人类可读标题
    val description: String,    // 触发条件描述
    val steps: String,          // 步骤列表（JSON 数组字符串）
    val pitfalls: String = "",  // 常见陷阱（JSON 数组字符串）
    val confidence: Float = 0.5f, // 0.0 ~ 1.0
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val usageCount: Int = 0
)
