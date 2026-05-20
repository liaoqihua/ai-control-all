package com.example.aicontrolall.evolution

import com.example.aicontrolall.memory.SessionStore
import com.example.aicontrolall.memory.SkillStore
import com.example.aicontrolall.memory.models.Message
import com.example.aicontrolall.memory.models.Skill

class PatternRecognizer(
    private val sessionStore: SessionStore,
    private val skillStore: SkillStore
) {
    companion object {
        const val PATTERN_THRESHOLD = 3 // 同一模式出现3次就生成技能
    }

    /**
     * 分析最近的消息，检测是否有重复模式
     */
    fun analyze(currentUserInput: String, recentMessages: List<Message>): Skill? {
        val triggerWords = extractTriggerWords(currentUserInput)
        if (triggerWords.isEmpty()) return null

        for (word in triggerWords) {
            val count = recentMessages.count { msg ->
                msg.role == "user" && msg.content.contains(word)
            }
            if (count >= PATTERN_THRESHOLD - 1) { // -1 因为当前消息也算一次
                val existingSkill = skillStore.getByName("auto-$word")
                if (existingSkill == null) {
                    return createAutoSkill(word)
                }
            }
        }
        return null
    }

    private fun extractTriggerWords(input: String): List<String> {
        val patterns = listOf(
            Regex("(打开|关闭|调节|设置|播放|停止|拍照|录像|查询|搜索|发送|拨打)"),
            Regex("(调暗|调亮|升温|降温|切换|静音|取消)")
        )
        return patterns.flatMap { pat ->
            pat.findAll(input).map { it.value }.toList()
        }.distinct()
    }

    private fun createAutoSkill(trigger: String): Skill {
        return Skill(
            name = "auto-$trigger",
            title = "自动生成的${trigger}技能",
            description = "从用户行为中自动学习到的${trigger}操作模式",
            steps = """[{"step":1,"action":"调用相关 MCP 工具执行${trigger}操作"},{"step":2,"action":"确认结果并告知用户"}]""",
            pitfalls = "这是一个自动生成的技能，可能需要手动调整步骤",
            confidence = 0.3f
        )
    }

    /**
     * 从最近消息中提取"这个步骤有效"的证据
     */
    fun detectSkillSuccess(@Suppress("UNUSED_PARAMETER") skillName: String, recentMessages: List<Message>): Boolean {
        val lastAssistantMsgs = recentMessages.takeLast(2).filter { it.role == "assistant" }
        if (lastAssistantMsgs.isEmpty()) return false
        return lastAssistantMsgs.none {
            it.content.contains("Error") ||
            it.content.contains("失败") ||
            it.content.contains("无法")
        }
    }
}
