package com.example.aicontrolall.ui

data class MenuItem(
    val id: String,
    val icon: String,
    val label: String,
    val badge: Int = 0,
    val isDivider: Boolean = false
)
