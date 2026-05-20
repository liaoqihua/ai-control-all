package com.example.aicontrolall.evolution

import com.example.aicontrolall.memory.MemoryStore
import com.example.aicontrolall.memory.SessionStore
import com.example.aicontrolall.memory.SkillStore

class EvolutionCycle(
    private val memoryStore: MemoryStore,
    private val skillStore: SkillStore,
    private val sessionStore: SessionStore,
    private val factExtractor: FactExtractor = FactExtractor(memoryStore),
    private val patternRecognizer: PatternRecognizer = PatternRecognizer(sessionStore, skillStore)
) {
    private val activeSkills = mutableSetOf<String>()

    /**
     * 每个对话回合后调用，执行完整的进化循环
     */
    fun evolve(
        userInput: String,
        assistantResponse: String,
        @Suppress("UNUSED_PARAMETER") toolResults: List<String>,
        sessionId: String
    ) {
        // 1. 事实提取 → 存入记忆
        val facts = factExtractor.extract(userInput, assistantResponse)
        for (fact in facts) {
            memoryStore.add(
                target = "memory",
                content = fact,
                tags = "auto-extracted"
            )
        }

        // 2. 检测对话中出现了哪些已注册技能
        val recentMessages = sessionStore.getRecentMessages(sessionId, limit = 20)
        detectActiveSkills(assistantResponse)

        // 3. 对活跃技能更新置信度
        for (skillName in activeSkills) {
            if (patternRecognizer.detectSkillSuccess(skillName, recentMessages)) {
                skillStore.incrementConfidence(skillName)
            } else {
                skillStore.decrementConfidence(skillName)
            }
        }

        // 4. 模式识别 → 可能生成新技能
        val newSkill = patternRecognizer.analyze(userInput, recentMessages)
        if (newSkill != null) {
            skillStore.add(newSkill)
        }
    }

    private fun detectActiveSkills(assistantResponse: String) {
        activeSkills.clear()
        val allSkills = skillStore.getAll()
        for (skill in allSkills) {
            if (assistantResponse.contains(skill.name) ||
                assistantResponse.contains(skill.title)) {
                activeSkills.add(skill.name)
            }
        }
    }
}
