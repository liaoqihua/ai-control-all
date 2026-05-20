package com.example.aicontrolall.memory.models

data class Session(
    val id: String,
    val title: String = "",
    val summary: String = "",
    val messageCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
