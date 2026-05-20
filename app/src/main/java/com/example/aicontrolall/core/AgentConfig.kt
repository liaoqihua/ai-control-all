package com.example.aicontrolall.core

import com.example.aicontrolall.util.ConfigManager

data class AgentConfig(
    val apiKey: String = "",
    val model: String = "deepseek-chat",
    val baseUrl: String = "https://api.deepseek.com",
    val maxHistoryLength: Int = 20,
    val evolutionEnabled: Boolean = true
) {
    companion object {
        fun fromConfigManager(cm: ConfigManager): AgentConfig {
            return AgentConfig(
                apiKey = cm.apiKey,
                model = cm.model,
                baseUrl = cm.baseUrl,
                evolutionEnabled = cm.evolutionEnabled
            )
        }
    }
}
