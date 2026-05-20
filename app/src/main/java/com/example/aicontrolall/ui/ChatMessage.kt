package com.example.aicontrolall.ui

data class ChatMessage(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val isUser: Boolean,
    val toolResults: String = ""
)
