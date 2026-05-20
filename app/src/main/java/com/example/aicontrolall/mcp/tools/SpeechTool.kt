package com.example.aicontrolall.mcp.tools

import android.content.Context
import android.speech.tts.TextToSpeech
import com.example.aicontrolall.mcp.McpTool
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.util.Locale
import kotlin.coroutines.resume

class SpeechTool(private val context: Context) : McpTool {
    override val name = "speak_text"
    override val description = "用手机扬声器/耳机朗读一段文字。用于 AI 对用户'说话'。"
    override val parameters = """{"type":"object","properties":{"text":{"type":"string","description":"要朗读的文字"}},"required":["text"]}"""

    private var tts: TextToSpeech? = null

    override suspend fun execute(args: JSONObject): String {
        val text = args.optString("text", "")
        if (text.isBlank()) return "Error: text is required"

        return suspendCancellableCoroutine { continuation ->
            tts = TextToSpeech(context) { status ->
                if (status != TextToSpeech.SUCCESS) {
                    continuation.resume("Error: TTS initialization failed")
                    return@TextToSpeech
                }
                tts?.language = Locale.CHINESE
                tts?.setOnUtteranceCompletedListener {
                    val escaped = text.replace("\"", "\\\"")
                    continuation.resume("""{"status":"spoken","text":"$escaped"}""")
                }
                val utteranceId = System.currentTimeMillis().toString()
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            }
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
