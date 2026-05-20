package com.example.aicontrolall.memory.models

data class Memory(
    val id: Long = 0,
    val target: String,        // "user" | "memory"
    val content: String,       // 事实内容
    val tags: String = "",     // 逗号分隔的标签，用于分类搜索
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val usageCount: Int = 0,
    val lastUsedAt: Long = 0
)
