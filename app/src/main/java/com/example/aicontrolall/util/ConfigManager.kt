package com.example.aicontrolall.util

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * 基于本地 JSON 文件的配置管理器。
 * 配置文件路径: {app内部存储}/config/agent_config.json
 */
class ConfigManager(context: Context) {
    private val configDir = File(context.filesDir, "config")
    private val configFile = File(configDir, "agent_config.json")

    init {
        if (!configDir.exists()) configDir.mkdirs()
        if (!configFile.exists()) {
            configFile.writeText("""{"api_key":"","model":"deepseek-chat","base_url":"https://api.deepseek.com","evolution_enabled":true}""")
        }
    }

    private fun readJson(): JSONObject {
        return try {
            JSONObject(configFile.readText())
        } catch (e: Exception) {
            JSONObject()
        }
    }

    private fun writeJson(json: JSONObject) {
        configFile.writeText(json.toString(2))
    }

    var apiKey: String
        get() = readJson().optString("api_key", "")
        set(value) {
            val json = readJson()
            json.put("api_key", value)
            writeJson(json)
        }

    var model: String
        get() = readJson().optString("model", "deepseek-chat")
        set(value) {
            val json = readJson()
            json.put("model", value)
            writeJson(json)
        }

    var baseUrl: String
        get() = readJson().optString("base_url", "https://api.deepseek.com")
        set(value) {
            val json = readJson()
            json.put("base_url", value)
            writeJson(json)
        }

    var evolutionEnabled: Boolean
        get() = readJson().optBoolean("evolution_enabled", true)
        set(value) {
            val json = readJson()
            json.put("evolution_enabled", value)
            writeJson(json)
        }

    /** 获取所有配置的 JSON 副本，供 Debug / 导出使用 */
    fun getAllConfig(): JSONObject = readJson()

    /** 获取配置文件路径，供 Debug 使用 */
    fun getConfigFilePath(): String = configFile.absolutePath
}
