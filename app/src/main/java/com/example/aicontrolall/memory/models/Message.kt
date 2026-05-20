package com.example.aicontrolall.memory.models

data class Message(
    val id: Long = 0,
    val sessionId: String,      // UUID
    val role: String,           // "user" | "assistant" | "tool"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
