package com.example.aicontrolall.evolution

import com.example.aicontrolall.memory.MemoryStore

class FactExtractor(private val memoryStore: MemoryStore) {

    /**
     * 基于启发式规则从用户输入中提取事实。
     * 未来可以改为用小模型做抽取，当前用规则匹配常见模式。
     */
    fun extract(userInput: String, @Suppress("UNUSED_PARAMETER") assistantResponse: String): List<String> {
        val facts = mutableListOf<String>()

        // Pattern 1: "我[是/在/有/住/用/喜欢/讨厌]..." → 事实
        val selfPattern = Regex("我[是在有住用喜欢讨厌].+?[。，,!！?？\\n]")
        selfPattern.findAll(userInput).forEach {
            facts.add(it.value.trimEnd('。', '，', ',', '!', '！', '?', '？'))
        }

        // Pattern 2: "每天/每次/总是/一直/从来不" → 习惯
        val habitPattern = Regex("(每天|每次|总是|一直|从来不|经常|偶尔).+?[。，,!！?？\\n]")
        habitPattern.findAll(userInput).forEach {
            facts.add(it.value.trimEnd('。', '，', ',', '!', '！', '?', '？'))
        }

        // Pattern 3: "我的（某物）[是/在]" → 拥有物
        val possessionPattern = Regex("我的.{1,10}(是|在|叫|IP|地址).+?[。，,!！?？\\n]")
        possessionPattern.findAll(userInput).forEach {
            facts.add(it.value.trimEnd('。', '，', ',', '!', '！', '?', '？'))
        }

        return facts.distinct()
    }
}
