# AiControlAll — Agent Core Foundation 实现计划

> **For Hermes:** 按 Phase 顺序执行，每 Phase 内部遵循 TDD（先写测试 → 看它失败 → 最小实现 → 看它通过）。

**Goal:** 在 Android 上搭建一个自进化 Agent 系统——记忆/技能/会话三层知识 + MCP 设备网关 + DeepSeek 云端推理 + 自动进化闭环。

**Architecture:** Agent Core 跑在 Android 主线程的消息循环里，SQLite 存储三层知识（Memory/Skill/Session），MCP Gateway 管理本地工具注册和调度，LlmClient 负责与 DeepSeek API 通信，EvolutionCycle 在每个对话回合后自动提炼新知识和修正技能置信度。

**Tech Stack:** Kotlin, Android SDK 34, SQLite + FTS5, OkHttp, Gson, CameraX, Android TTS, Coroutines

---

## 项目最终目录结构

```
app/src/main/java/com/example/aicontrolall/
├── core/
│   ├── AgentCore.kt              # 主循环
│   ├── AgentConfig.kt            # 配置 data class
│   └── PromptBuilder.kt          # 拼 system prompt
├── memory/
│   ├── DatabaseHelper.kt         # SQLiteOpenHelper
│   ├── MemoryStore.kt            # 事实记忆 CRUD
│   ├── SkillStore.kt             # 技能 CRUD + 置信度
│   ├── SessionStore.kt           # 会话+消息 CRUD + FTS5
│   └── models/
│       ├── Memory.kt             # data class
│       ├── Skill.kt              # data class
│       └── Message.kt            # data class
├── mcp/
│   ├── McpTool.kt                # Tool 接口
│   ├── McpGateway.kt             # 注册中心 + 调度
│   └── tools/
│       ├── CameraTool.kt
│       ├── SpeechTool.kt
│       └── SearchMemoriesTool.kt
├── llm/
│   ├── LlmClient.kt              # OkHttp → DeepSeek
│   └── LlmResponse.kt            # 解析工具调用
├── evolution/
│   ├── FactExtractor.kt          # 从对话提炼事实
│   ├── PatternRecognizer.kt      # 识别重复模式 → 生成技能
│   └── EvolutionCycle.kt         # 编排进化循环
├── util/
│   └── ConfigManager.kt          # JSON 文件配置读写
├── ui/
│   ├── MainActivity.kt           # 聊天主界面
│   ├── SettingsActivity.kt       # 设置页面（API Key / Model 等）
│   ├── ChatAdapter.kt
│   └── ChatMessage.kt
```

---

## Phase 1: 数据模型 + 数据库基础（记忆/技能/会话三层存储）

### Task 1.1: 创建数据模型类

**Objective:** 定义 Memory、Skill、Message 三个 data class

**Files:**
- Create: `app/src/main/java/com/example/aicontrolall/memory/models/Memory.kt`
- Create: `app/src/main/java/com/example/aicontrolall/memory/models/Skill.kt`
- Create: `app/src/main/java/com/example/aicontrolall/memory/models/Message.kt`

**Step 1: Write Memory data class**

```kotlin
package com.example.aicontrolall.memory.models

data class Memory(
    val id: Long = 0,
    val target: String,        // "user" | "memory"
    val content: String,       // 事实内容
    val tags: String = "",     // 逗号分隔的标签，用于分类搜索
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val usageCount: Int = 0,
    val lastUsedAt: Long = 0
)
```

**Step 2: Write Skill data class**

```kotlin
package com.example.aicontrolall.memory.models

data class Skill(
    val id: Long = 0,
    val name: String,           // 唯一标识符 e.g. "adjust-smart-light"
    val title: String,          // 人类可读标题
    val description: String,    // 触发条件描述
    val steps: String,          // 步骤列表（JSON 数组字符串）
    val pitfalls: String = "",  // 常见陷阱（JSON 数组字符串）
    val confidence: Float = 0.5f, // 0.0 ~ 1.0
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val usageCount: Int = 0
)
```

**Step 3: Write Message data class**

```kotlin
package com.example.aicontrolall.memory.models

data class Message(
    val id: Long = 0,
    val sessionId: String,      // UUID
    val role: String,           // "user" | "assistant" | "tool"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
```

---

### Task 1.2: 创建 DatabaseHelper（SQLite + FTS5）

**Objective:** 建立 SQLite 数据库，包含三张主表 + FTS5 全文索引

**Files:**
- Create: `app/src/main/java/com/example/aicontrolall/memory/DatabaseHelper.kt`

**Step 1: Write DatabaseHelper**

```kotlin
package com.example.aicontrolall.memory

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "aicontrolall.db"
        const val DATABASE_VERSION = 1

        // ── Memories table ──
        const val TABLE_MEMORIES = "memories"
        const val COL_MEM_ID = "id"
        const val COL_MEM_TARGET = "target"
        const val COL_MEM_CONTENT = "content"
        const val COL_MEM_TAGS = "tags"
        const val COL_MEM_CREATED_AT = "created_at"
        const val COL_MEM_UPDATED_AT = "updated_at"
        const val COL_MEM_USAGE_COUNT = "usage_count"
        const val COL_MEM_LAST_USED_AT = "last_used_at"

        // ── Skills table ──
        const val TABLE_SKILLS = "skills"
        const val COL_SKILL_ID = "id"
        const val COL_SKILL_NAME = "name"
        const val COL_SKILL_TITLE = "title"
        const val COL_SKILL_DESC = "description"
        const val COL_SKILL_STEPS = "steps"
        const val COL_SKILL_PITFALLS = "pitfalls"
        const val COL_SKILL_CONFIDENCE = "confidence"
        const val COL_SKILL_VERSION = "version"
        const val COL_SKILL_CREATED_AT = "created_at"
        const val COL_SKILL_UPDATED_AT = "updated_at"
        const val COL_SKILL_USAGE_COUNT = "usage_count"

        // ── Sessions table ──
        const val TABLE_SESSIONS = "sessions"
        const val COL_SESS_ID = "id"
        const val COL_SESS_TITLE = "title"
        const val COL_SESS_SUMMARY = "summary"
        const val COL_SESS_CREATED_AT = "created_at"

        // ── Messages table ──
        const val TABLE_MESSAGES = "messages"
        const val COL_MSG_ID = "id"
        const val COL_MSG_SESSION_ID = "session_id"
        const val COL_MSG_ROLE = "role"
        const val COL_MSG_CONTENT = "content"
        const val COL_MSG_TIMESTAMP = "timestamp"

        // ── FTS virtual tables ──
        const val FTS_MEMORIES = "memories_fts"
        const val FTS_MESSAGES = "messages_fts"
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Memories
        db.execSQL("""
            CREATE TABLE $TABLE_MEMORIES (
                $COL_MEM_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_MEM_TARGET TEXT NOT NULL,
                $COL_MEM_CONTENT TEXT NOT NULL,
                $COL_MEM_TAGS TEXT DEFAULT '',
                $COL_MEM_CREATED_AT INTEGER NOT NULL,
                $COL_MEM_UPDATED_AT INTEGER NOT NULL,
                $COL_MEM_USAGE_COUNT INTEGER DEFAULT 0,
                $COL_MEM_LAST_USED_AT INTEGER DEFAULT 0
            )
        """)
        db.execSQL("CREATE VIRTUAL TABLE $FTS_MEMORIES USING fts5($COL_MEM_CONTENT, content=$TABLE_MEMORIES)")

        // Skills
        db.execSQL("""
            CREATE TABLE $TABLE_SKILLS (
                $COL_SKILL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_SKILL_NAME TEXT UNIQUE NOT NULL,
                $COL_SKILL_TITLE TEXT NOT NULL,
                $COL_SKILL_DESC TEXT NOT NULL,
                $COL_SKILL_STEPS TEXT NOT NULL,
                $COL_SKILL_PITFALLS TEXT DEFAULT '',
                $COL_SKILL_CONFIDENCE REAL DEFAULT 0.5,
                $COL_SKILL_VERSION INTEGER DEFAULT 1,
                $COL_SKILL_CREATED_AT INTEGER NOT NULL,
                $COL_SKILL_UPDATED_AT INTEGER NOT NULL,
                $COL_SKILL_USAGE_COUNT INTEGER DEFAULT 0
            )
        """)

        // Sessions
        db.execSQL("""
            CREATE TABLE $TABLE_SESSIONS (
                $COL_SESS_ID TEXT PRIMARY KEY,
                $COL_SESS_TITLE TEXT DEFAULT '',
                $COL_SESS_SUMMARY TEXT DEFAULT '',
                $COL_SESS_CREATED_AT INTEGER NOT NULL
            )
        """)

        // Messages
        db.execSQL("""
            CREATE TABLE $TABLE_MESSAGES (
                $COL_MSG_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_MSG_SESSION_ID TEXT NOT NULL,
                $COL_MSG_ROLE TEXT NOT NULL,
                $COL_MSG_CONTENT TEXT NOT NULL,
                $COL_MSG_TIMESTAMP INTEGER NOT NULL,
                FOREIGN KEY ($COL_MSG_SESSION_ID) REFERENCES $TABLE_SESSIONS($COL_SESS_ID)
            )
        """)
        db.execSQL("CREATE VIRTUAL TABLE $FTS_MESSAGES USING fts5($COL_MSG_SESSION_ID, $COL_MSG_ROLE, $COL_MSG_CONTENT, content=$TABLE_MESSAGES)")

        // Indexes
        db.execSQL("CREATE INDEX idx_mem_target ON $TABLE_MEMORIES($COL_MEM_TARGET)")
        db.execSQL("CREATE INDEX idx_mem_updated ON $TABLE_MEMORIES($COL_MEM_UPDATED_AT DESC)")
        db.execSQL("CREATE INDEX idx_skill_name ON $TABLE_SKILLS($COL_SKILL_NAME)")
        db.execSQL("CREATE INDEX idx_skill_confidence ON $TABLE_SKILLS($COL_SKILL_CONFIDENCE DESC)")
        db.execSQL("CREATE INDEX idx_msg_session ON $TABLE_MESSAGES($COL_MSG_SESSION_ID)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $FTS_MEMORIES")
        db.execSQL("DROP TABLE IF EXISTS $FTS_MESSAGES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_MEMORIES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SKILLS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_MESSAGES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SESSIONS")
        onCreate(db)
    }
}
```

---

### Task 1.3: 实现 MemoryStore CRUD

**Objective:** 事实记忆的增删改查 + FTS5 搜索

**Files:**
- Create: `app/src/main/java/com/example/aicontrolall/memory/MemoryStore.kt`

**Complete code:**

```kotlin
package com.example.aicontrolall.memory

import android.content.ContentValues
import com.example.aicontrolall.memory.models.Memory

class MemoryStore(private val dbHelper: DatabaseHelper) {

    /**
     * 添加一条记忆，如果 content 完全相同则更新 updated_at
     * 返回记忆 ID
     */
    fun add(target: String, content: String, tags: String = ""): Long {
        val db = dbHelper.writableDatabase
        val now = System.currentTimeMillis()

        // Check for duplicate
        val cursor = db.query(
            DatabaseHelper.TABLE_MEMORIES,
            arrayOf(DatabaseHelper.COL_MEM_ID),
            "${DatabaseHelper.COL_MEM_CONTENT} = ?",
            arrayOf(content),
            null, null, null
        )
        cursor.use {
            if (it.moveToFirst()) {
                val id = it.getLong(0)
                val values = ContentValues().apply {
                    put(DatabaseHelper.COL_MEM_UPDATED_AT, now)
                    put(DatabaseHelper.COL_MEM_TAGS, tags)
                }
                db.update(DatabaseHelper.TABLE_MEMORIES, values,
                    "${DatabaseHelper.COL_MEM_ID} = ?", arrayOf(id.toString()))
                return id
            }
        }

        val values = ContentValues().apply {
            put(DatabaseHelper.COL_MEM_TARGET, target)
            put(DatabaseHelper.COL_MEM_CONTENT, content)
            put(DatabaseHelper.COL_MEM_TAGS, tags)
            put(DatabaseHelper.COL_MEM_CREATED_AT, now)
            put(DatabaseHelper.COL_MEM_UPDATED_AT, now)
        }
        return db.insert(DatabaseHelper.TABLE_MEMORIES, null, values)
    }

    /**
     * 搜索近期记忆（最多 20 条），按更新时间倒序
     */
    fun getRecent(limit: Int = 20): List<Memory> {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            DatabaseHelper.TABLE_MEMORIES,
            null, null, null, null, null,
            "${DatabaseHelper.COL_MEM_UPDATED_AT} DESC",
            limit.toString()
        )
        val results = mutableListOf<Memory>()
        cursor.use {
            while (it.moveToNext()) {
                results.add(cursorToMemory(it))
            }
        }
        return results
    }

    /**
     * 用 FTS5 全文搜索记忆，支持多关键词
     * 返回结果按 relevance + 更新时间排序
     */
    fun search(query: String, limit: Int = 10): List<Memory> {
        val db = dbHelper.readableDatabase
        val ftsQuery = query.split(" ").joinToString(" OR ") { "\"$it\"" }
        val cursor = db.rawQuery("""
            SELECT m.* FROM ${DatabaseHelper.TABLE_MEMORIES} m
            INNER JOIN ${DatabaseHelper.FTS_MEMORIES} f ON m.${DatabaseHelper.COL_MEM_ID} = f.rowid
            WHERE ${DatabaseHelper.FTS_MEMORIES} MATCH ?
            ORDER BY m.${DatabaseHelper.COL_MEM_UPDATED_AT} DESC
            LIMIT ?
        """, arrayOf(ftsQuery, limit.toString()))
        val results = mutableListOf<Memory>()
        cursor.use {
            while (it.moveToNext()) {
                results.add(cursorToMemory(it))
            }
        }
        return results
    }

    /**
     * 删除一条记忆
     */
    fun remove(id: Long) {
        val db = dbHelper.writableDatabase
        db.delete(DatabaseHelper.TABLE_MEMORIES,
            "${DatabaseHelper.COL_MEM_ID} = ?", arrayOf(id.toString()))
    }

    /**
     * 增加使用计数
     */
    fun incrementUsage(id: Long) {
        val db = dbHelper.writableDatabase
        db.execSQL("""
            UPDATE ${DatabaseHelper.TABLE_MEMORIES}
            SET ${DatabaseHelper.COL_MEM_USAGE_COUNT} = ${DatabaseHelper.COL_MEM_USAGE_COUNT} + 1,
                ${DatabaseHelper.COL_MEM_LAST_USED_AT} = ?
            WHERE ${DatabaseHelper.COL_MEM_ID} = ?
        """, arrayOf(System.currentTimeMillis().toString(), id.toString()))
    }

    /**
     * 获取总记忆数
     */
    fun count(): Int {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM ${DatabaseHelper.TABLE_MEMORIES}", null)
        cursor.use {
            return if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    private fun cursorToMemory(cursor: android.database.Cursor): Memory {
        return Memory(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_MEM_ID)),
            target = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_MEM_TARGET)),
            content = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_MEM_CONTENT)),
            tags = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_MEM_TAGS)) ?: "",
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_MEM_CREATED_AT)),
            updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_MEM_UPDATED_AT)),
            usageCount = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_MEM_USAGE_COUNT)),
            lastUsedAt = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_MEM_LAST_USED_AT))
        )
    }
}
```

---

### Task 1.4: 实现 SkillStore CRUD + 置信度管理

**Objective:** 技能的增删改查 + 置信度升降 + 搜索

**Files:**
- Create: `app/src/main/java/com/example/aicontrolall/memory/SkillStore.kt`

**Complete code:**

```kotlin
package com.example.aicontrolall.memory

import android.content.ContentValues
import com.example.aicontrolall.memory.models.Skill

class SkillStore(private val dbHelper: DatabaseHelper) {

    fun add(skill: Skill): Long {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(DatabaseHelper.COL_SKILL_NAME, skill.name)
            put(DatabaseHelper.COL_SKILL_TITLE, skill.title)
            put(DatabaseHelper.COL_SKILL_DESC, skill.description)
            put(DatabaseHelper.COL_SKILL_STEPS, skill.steps)
            put(DatabaseHelper.COL_SKILL_PITFALLS, skill.pitfalls)
            put(DatabaseHelper.COL_SKILL_CONFIDENCE, skill.confidence)
            put(DatabaseHelper.COL_SKILL_VERSION, skill.version)
            put(DatabaseHelper.COL_SKILL_CREATED_AT, skill.createdAt)
            put(DatabaseHelper.COL_SKILL_UPDATED_AT, skill.updatedAt)
        }
        return db.insert(DatabaseHelper.TABLE_SKILLS, null, values)
    }

    fun getByName(name: String): Skill? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            DatabaseHelper.TABLE_SKILLS, null,
            "${DatabaseHelper.COL_SKILL_NAME} = ?", arrayOf(name),
            null, null, null
        )
        cursor.use {
            return if (it.moveToFirst()) cursorToSkill(it) else null
        }
    }

    fun getAll(limit: Int = 50): List<Skill> {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            DatabaseHelper.TABLE_SKILLS, null, null, null, null, null,
            "${DatabaseHelper.COL_SKILL_CONFIDENCE} DESC",
            limit.toString()
        )
        val results = mutableListOf<Skill>()
        cursor.use {
            while (it.moveToNext()) results.add(cursorToSkill(it))
        }
        return results
    }

    /**
     * 根据描述关键词匹配相关技能（简单 LIKE 搜索）
     * 在上线后可改为基于 FTS 或向量搜索
     */
    fun matchByKeyword(keyword: String, limit: Int = 5): List<Skill> {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            DatabaseHelper.TABLE_SKILLS, null,
            "${DatabaseHelper.COL_SKILL_DESC} LIKE ? OR ${DatabaseHelper.COL_SKILL_TITLE} LIKE ?",
            arrayOf("%$keyword%", "%$keyword%"),
            null, null,
            "${DatabaseHelper.COL_SKILL_CONFIDENCE} DESC",
            limit.toString()
        )
        val results = mutableListOf<Skill>()
        cursor.use {
            while (it.moveToNext()) results.add(cursorToSkill(it))
        }
        return results
    }

    /**
     * 技能执行成功 → 置信度 +0.05（上限 1.0）
     */
    fun incrementConfidence(name: String) {
        val db = dbHelper.writableDatabase
        db.execSQL("""
            UPDATE ${DatabaseHelper.TABLE_SKILLS}
            SET ${DatabaseHelper.COL_SKILL_CONFIDENCE} = MIN(1.0, ${DatabaseHelper.COL_SKILL_CONFIDENCE} + 0.05),
                ${DatabaseHelper.COL_SKILL_UPDATED_AT} = ?,
                ${DatabaseHelper.COL_SKILL_USAGE_COUNT} = ${DatabaseHelper.COL_SKILL_USAGE_COUNT} + 1
            WHERE ${DatabaseHelper.COL_SKILL_NAME} = ?
        """, arrayOf(System.currentTimeMillis().toString(), name))
    }

    /**
     * 技能执行失败 → 置信度 -0.1（下限 0.1）
     */
    fun decrementConfidence(name: String) {
        val db = dbHelper.writableDatabase
        db.execSQL("""
            UPDATE ${DatabaseHelper.TABLE_SKILLS}
            SET ${DatabaseHelper.COL_SKILL_CONFIDENCE} = MAX(0.1, ${DatabaseHelper.COL_SKILL_CONFIDENCE} - 0.1),
                ${DatabaseHelper.COL_SKILL_UPDATED_AT} = ?
            WHERE ${DatabaseHelper.COL_SKILL_NAME} = ?
        """, arrayOf(System.currentTimeMillis().toString(), name))
    }

    /**
     * 更新技能步骤（当发现更好的做法时）
     */
    fun updateSteps(name: String, steps: String, pitfalls: String = "") {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(DatabaseHelper.COL_SKILL_STEPS, steps)
            put(DatabaseHelper.COL_SKILL_PITFALLS, pitfalls)
            put(DatabaseHelper.COL_SKILL_VERSION, getByName(name)?.version?.plus(1) ?: 1)
            put(DatabaseHelper.COL_SKILL_UPDATED_AT, System.currentTimeMillis())
        }
        db.update(DatabaseHelper.TABLE_SKILLS, values,
            "${DatabaseHelper.COL_SKILL_NAME} = ?", arrayOf(name))
    }

    fun remove(name: String) {
        val db = dbHelper.writableDatabase
        db.delete(DatabaseHelper.TABLE_SKILLS,
            "${DatabaseHelper.COL_SKILL_NAME} = ?", arrayOf(name))
    }

    fun count(): Int {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM ${DatabaseHelper.TABLE_SKILLS}", null)
        cursor.use { return if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    private fun cursorToSkill(cursor: android.database.Cursor): Skill {
        return Skill(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SKILL_ID)),
            name = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SKILL_NAME)),
            title = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SKILL_TITLE)),
            description = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SKILL_DESC)),
            steps = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SKILL_STEPS)),
            pitfalls = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SKILL_PITFALLS)) ?: "",
            confidence = cursor.getFloat(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SKILL_CONFIDENCE)),
            version = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SKILL_VERSION)),
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SKILL_CREATED_AT)),
            updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SKILL_UPDATED_AT)),
            usageCount = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SKILL_USAGE_COUNT))
        )
    }
}
```

---

### Task 1.5: 实现 SessionStore CRUD + FTS5 历史搜索

**Objective:** 会话管理 + 消息存储 + 全文搜索历史对话

**Files:**
- Create: `app/src/main/java/com/example/aicontrolall/memory/SessionStore.kt`

**Complete code:**

```kotlin
package com.example.aicontrolall.memory

import android.content.ContentValues
import com.example.aicontrolall.memory.models.Message
import java.util.UUID

class SessionStore(private val dbHelper: DatabaseHelper) {

    fun createSession(): String {
        val db = dbHelper.writableDatabase
        val sessionId = UUID.randomUUID().toString()
        val values = ContentValues().apply {
            put(DatabaseHelper.COL_SESS_ID, sessionId)
            put(DatabaseHelper.COL_SESS_CREATED_AT, System.currentTimeMillis())
        }
        db.insert(DatabaseHelper.TABLE_SESSIONS, null, values)
        return sessionId
    }

    fun addMessage(sessionId: String, role: String, content: String): Long {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(DatabaseHelper.COL_MSG_SESSION_ID, sessionId)
            put(DatabaseHelper.COL_MSG_ROLE, role)
            put(DatabaseHelper.COL_MSG_CONTENT, content)
            put(DatabaseHelper.COL_MSG_TIMESTAMP, System.currentTimeMillis())
        }
        return db.insert(DatabaseHelper.TABLE_MESSAGES, null, values)
    }

    fun getRecentMessages(sessionId: String, limit: Int = 20): List<Message> {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            DatabaseHelper.TABLE_MESSAGES, null,
            "${DatabaseHelper.COL_MSG_SESSION_ID} = ?", arrayOf(sessionId),
            null, null,
            "${DatabaseHelper.COL_MSG_TIMESTAMP} ASC",
            limit.toString()
        )
        val results = mutableListOf<Message>()
        cursor.use {
            while (it.moveToNext()) results.add(cursorToMessage(it))
        }
        return results
    }

    /**
     * FTS5 全文搜索历史消息
     */
    fun searchMessages(query: String, limit: Int = 10): List<Message> {
        val db = dbHelper.readableDatabase
        val ftsQuery = query.split(" ").joinToString(" OR ") { "\"$it\"" }
        val cursor = db.rawQuery("""
            SELECT m.* FROM ${DatabaseHelper.TABLE_MESSAGES} m
            INNER JOIN ${DatabaseHelper.FTS_MESSAGES} f ON m.${DatabaseHelper.COL_MSG_ID} = f.rowid
            WHERE ${DatabaseHelper.FTS_MESSAGES} MATCH ?
            ORDER BY m.${DatabaseHelper.COL_MSG_TIMESTAMP} DESC
            LIMIT ?
        """, arrayOf(ftsQuery, limit.toString()))
        val results = mutableListOf<Message>()
        cursor.use {
            while (it.moveToNext()) results.add(cursorToMessage(it))
        }
        return results
    }

    /**
     * 更新会话标题（通常是对话的第一句话或摘要）
     */
    fun updateSessionTitle(sessionId: String, title: String) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(DatabaseHelper.COL_SESS_TITLE, title)
        }
        db.update(DatabaseHelper.TABLE_SESSIONS, values,
            "${DatabaseHelper.COL_SESS_ID} = ?", arrayOf(sessionId))
    }

    fun getAllSessions(): List<Pair<String, String>> {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            DatabaseHelper.TABLE_SESSIONS,
            arrayOf(DatabaseHelper.COL_SESS_ID, DatabaseHelper.COL_SESS_TITLE),
            null, null, null, null,
            "${DatabaseHelper.COL_SESS_CREATED_AT} DESC"
        )
        val results = mutableListOf<Pair<String, String>>()
        cursor.use {
            while (it.moveToNext()) {
                results.add(
                    it.getString(0) to (it.getString(1) ?: "未命名会话")
                )
            }
        }
        return results
    }

    private fun cursorToMessage(cursor: android.database.Cursor): Message {
        return Message(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_MSG_ID)),
            sessionId = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_MSG_SESSION_ID)),
            role = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_MSG_ROLE)),
            content = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_MSG_CONTENT)),
            timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_MSG_TIMESTAMP))
        )
    }
}
```

---

## Phase 2: MCP 工具网关

### Task 2.1: 定义 McpTool 接口

**Objective:** 统一工具接口，每个设备/功能实现这个接口即可被 McpGateway 发现和调度

**Files:**
- Create: `app/src/main/java/com/example/aicontrolall/mcp/McpTool.kt`

**Complete code:**

```kotlin
package com.example.aicontrolall.mcp

import org.json.JSONObject

/**
 * MCP 工具统一接口。
 * 每个设备/功能实现这个接口，McpGateway 自动发现和调度。
 */
interface McpTool {
    /** 工具名称，唯一标识，如 "capture_photo" */
    val name: String

    /** 人类可读描述，会注入到 LLM prompt 中 */
    val description: String

    /** 参数定义 JSON Schema，如 {"type":"object","properties":{"resolution":{"type":"string"}}} */
    val parameters: String

    /**
     * 执行工具调用
     * @param args 参数 JSON 对象
     * @return 执行结果字符串（成功返回结果，失败返回错误信息）
     */
    suspend fun execute(args: JSONObject): String

    /**
     * 生成给 LLM 看的工具签名
     */
    fun toPrompt(): String {
        return """
Tool: $name
Description: $description
Parameters: $parameters
""".trimIndent()
    }
}
```

---

### Task 2.2: 实现 McpGateway（注册中心 + 调度器）

**Objective:** 管理工具注册、列出可用工具、解析 LLM 返回的工具调用并执行

**Files:**
- Create: `app/src/main/java/com/example/aicontrolall/mcp/McpGateway.kt`

**Complete code:**

```kotlin
package com.example.aicontrolall.mcp

import org.json.JSONArray
import org.json.JSONObject

class McpGateway {

    private val tools = mutableMapOf<String, McpTool>()

    fun register(tool: McpTool) {
        tools[tool.name] = tool
    }

    fun unregister(name: String) {
        tools.remove(name)
    }

    fun listTools(): List<McpTool> = tools.values.toList()

    fun getTool(name: String): McpTool? = tools[name]

    /**
     * 生成所有工具签名摘要，注入到 LLM system prompt
     */
    fun buildToolsPrompt(): String {
        if (tools.isEmpty()) return "No tools available."
        return tools.values.joinToString("\n\n") { it.toPrompt() }
    }

    /**
     * 批量执行 LLM 返回的工具调用
     * @param toolCalls JSONArray，每项格式: {"name":"...","arguments":{...}}
     * @return 每个工具调用的结果列表
     */
    suspend fun executeBatch(toolCalls: JSONArray): List<ToolResult> {
        val results = mutableListOf<ToolResult>()
        for (i in 0 until toolCalls.length()) {
            val call = toolCalls.getJSONObject(i)
            val name = call.optString("name")
            val args = call.optJSONObject("arguments") ?: JSONObject()
            val tool = tools[name]
            if (tool == null) {
                results.add(ToolResult(name, false, "Tool not found: $name"))
                continue
            }
            try {
                val output = tool.execute(args)
                results.add(ToolResult(name, true, output))
            } catch (e: Exception) {
                results.add(ToolResult(name, false, "Error: ${e.message}"))
            }
        }
        return results
    }

    data class ToolResult(
        val toolName: String,
        val success: Boolean,
        val output: String
    )
}
```

---

### Task 2.3: 实现本地 MCP 工具（Camera / Speech / SearchMemories）

**Objective:** 三个基础设备工具——拍照、TTS 说话、搜索记忆

**Files:**
- Create: `app/src/main/java/com/example/aicontrolall/mcp/tools/CameraTool.kt`
- Create: `app/src/main/java/com/example/aicontrolall/mcp/tools/SpeechTool.kt`
- Create: `app/src/main/java/com/example/aicontrolall/mcp/tools/SearchMemoriesTool.kt`

**Step 1: CameraTool**

```kotlin
package com.example.aicontrolall.mcp.tools

import android.content.Context
import android.hardware.camera2.*
import com.example.aicontrolall.mcp.McpTool
import org.json.JSONObject

class CameraTool(private val context: Context) : McpTool {
    override val name = "capture_photo"
    override val description = "用手机摄像头拍一张照片，返回照片的文件路径"
    override val parameters = """{"type":"object","properties":{},"required":[]}"""

    override suspend fun execute(args: JSONObject): String {
        // Phase 1: 用 Camera Intent 拍照（后续可改为 CameraX 直接控制）
        // 当前返回占位，具体实现在后续 task 中通过 CameraX 完成
        return """{"status":"not_implemented","message":"Camera capture will be implemented with CameraX"}"""
    }
}
```

**Step 2: SpeechTool**

```kotlin
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
                    continuation.resume("""{"status":"spoken","text":"$text"}""")
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
```

**Step 3: SearchMemoriesTool**

```kotlin
package com.example.aicontrolall.mcp.tools

import com.example.aicontrolall.mcp.McpTool
import com.example.aicontrolall.memory.MemoryStore
import com.example.aicontrolall.memory.SessionStore
import org.json.JSONArray
import org.json.JSONObject

class SearchMemoriesTool(
    private val memoryStore: MemoryStore,
    private val sessionStore: SessionStore
) : McpTool {
    override val name = "search_memories"
    override val description = "搜索持久记忆和会话历史，返回相关的事实和历史对话。用于 AI 回忆之前和用户的互动。"
    override val parameters = """{"type":"object","properties":{"query":{"type":"string","description":"搜索关键词"}},"required":["query"]}"""

    override suspend fun execute(args: JSONObject): String {
        val query = args.optString("query", "")
        if (query.isBlank()) return "Error: query is required"

        val memories = memoryStore.search(query, limit = 5)
        val messages = sessionStore.searchMessages(query, limit = 5)

        val result = JSONObject()
        result.put("memories", JSONArray().apply {
            memories.forEach { put(it.content) }
        })
        result.put("messages", JSONArray().apply {
            messages.forEach { put("[${it.role}] ${it.content}") }
        })

        return result.toString()
    }
}
```

---

## Phase 3: LLM 客户端 + Prompt 组装

### Task 3.1: 实现 LlmClient（DeepSeek API）

**Objective:** 封装 DeepSeek API 调用，支持工具调用格式。API key 从 SharedPreferences 读取。

**Files:**
- Create: `app/src/main/java/com/example/aicontrolall/llm/LlmClient.kt`
- Create: `app/src/main/java/com/example/aicontrolall/llm/LlmResponse.kt`
- Create: `app/src/main/java/com/example/aicontrolall/util/ConfigManager.kt`

**Step 1: ConfigManager（本地 JSON 文件存储配置）**

```kotlin
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
```

**Step 2: LlmResponse**

```kotlin
package com.example.aicontrolall.llm

import org.json.JSONArray

data class LlmResponse(
    val content: String,            // 文字回复
    val toolCalls: JSONArray? = null // 工具调用列表，null 表示无工具调用
)
```

**Step 3: LlmClient**

```kotlin
package com.example.aicontrolall.llm

import com.example.aicontrolall.util.ConfigManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class LlmClient(private val config: ConfigManager) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    /**
     * 发送对话请求到 DeepSeek
     * @param messages JSONArray 格式的对话历史
     * @param tools 可用工具定义 JSONArray（如果为空则无工具调用）
     */
    suspend fun chat(messages: JSONArray, tools: JSONArray = JSONArray()): LlmResponse =
        withContext(Dispatchers.IO) {
            val requestBody = JSONObject().apply {
                put("model", config.model)
                put("messages", messages)
                put("temperature", 0.7)
                put("max_tokens", 2048)
                if (tools.length() > 0) {
                    put("tools", tools)
                    put("tool_choice", "auto")
                }
            }

            val request = Request.Builder()
                .url("${config.baseUrl}/v1/chat/completions")
                .addHeader("Authorization", "Bearer ${config.apiKey}")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody(JSON_MEDIA))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: throw IOException("Empty response")

            if (!response.isSuccessful) {
                throw IOException("API error ${response.code}: $body")
            }

            parseResponse(JSONObject(body))
        }

    private fun parseResponse(json: JSONObject): LlmResponse {
        val choices = json.getJSONArray("choices")
        if (choices.length() == 0) return LlmResponse("")

        val choice = choices.getJSONObject(0)
        val message = choice.getJSONObject("message")

        val content = message.optString("content", "")
        val toolCalls = message.optJSONArray("tool_calls")

        // Convert tool_calls to simplified format
        val simplifiedTools = if (toolCalls != null && toolCalls.length() > 0) {
            JSONArray().apply {
                for (i in 0 until toolCalls.length()) {
                    val tc = toolCalls.getJSONObject(i)
                    val func = tc.getJSONObject("function")
                    put(JSONObject().apply {
                        put("name", func.getString("name"))
                        put("arguments", JSONObject(func.getString("arguments")))
                    })
                }
            }
        } else null

        return LlmResponse(content = content, toolCalls = simplifiedTools)
    }
}
```

---

### Task 3.2: 实现 PromptBuilder

**Objective:** 组装 system prompt，注入记忆 + 技能 + 可用工具 + 对话历史

**Files:**
- Create: `app/src/main/java/com/example/aicontrolall/core/PromptBuilder.kt`

**Complete code:**

```kotlin
package com.example.aicontrolall.core

import com.example.aicontrolall.mcp.McpGateway
import com.example.aicontrolall.memory.MemoryStore
import com.example.aicontrolall.memory.SessionStore
import com.example.aicontrolall.memory.SkillStore
import com.example.aicontrolall.memory.models.Message
import org.json.JSONArray
import org.json.JSONObject

class PromptBuilder(
    private val memoryStore: MemoryStore,
    private val skillStore: SkillStore,
    private val sessionStore: SessionStore,
    private val mcpGateway: McpGateway
) {
    companion object {
        const val SYSTEM_PROMPT = """
你是一个运行在用户手机上的 AI 助手。你可以通过工具控制手机的硬件设备（摄像头、扬声器、麦克风等）以及其他连接的智能设备。
你的记忆系统记录了关于用户的事实和信息，你会在每次对话开始时自动获得相关记忆。
你有能力从对话中学习——当你发现新的事实时会将它们存入记忆，当你发现某种工作模式重复出现时会将其提炼为技能。

重要规则：
1. 你是用户的伙伴，不是冷冰冰的工具。用自然亲切的中文回复。
2. 读取已知事实部分，如果其中有和用户当前请求相关的信息，直接使用它，不需要重复询问用户。
3. 如果用户的请求涉及设备控制，调用对应的 tool。不要凭空编造结果。
4. 如果对话中出现用户的新偏好、新信息、或环境变化，记录下来（但不要在回复里说你正在记录）。
5. 如果你不确定某个设备是否可用，先调用 search_memories 查一下。
""".trimIndent()
    }

    fun build(
        userInput: String,
        sessionId: String,
        memoriesQuery: String = userInput
    ): JSONArray {
        val messages = JSONArray()

        // 1. System message
        val systemContent = buildSystemContent(memoriesQuery)
        messages.put(JSONObject().apply {
            put("role", "system")
            put("content", systemContent)
        })

        // 2. Recent conversation history (last 10 messages)
        val recentMessages = sessionStore.getRecentMessages(sessionId, limit = 10)
        for (msg in recentMessages) {
            messages.put(JSONObject().apply {
                put("role", msg.role)
                put("content", msg.content)
            })
        }

        // 3. Current user input
        messages.put(JSONObject().apply {
            put("role", "user")
            put("content", userInput)
        })

        return messages
    }

    fun buildTools(): org.json.JSONArray {
        val tools = org.json.JSONArray()
        for (tool in mcpGateway.listTools()) {
            tools.put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", tool.name)
                    put("description", tool.description)
                    put("parameters", JSONObject(tool.parameters))
                })
            })
        }
        return tools
    }

    private fun buildSystemContent(memoriesQuery: String): String {
        val sb = StringBuilder()
        sb.appendLine(SYSTEM_PROMPT)
        sb.appendLine()

        // 注入相关记忆
        val memories = memoryStore.search(memoriesQuery, limit = 10)
            .ifEmpty { memoryStore.getRecent(limit = 5) }
        if (memories.isNotEmpty()) {
            sb.appendLine("## 已知事实")
            for (mem in memories) {
                sb.appendLine("- ${mem.content}")
            }
            sb.appendLine()
        }

        // 注入可用技能
        val skills = skillStore.getAll(limit = 10)
        if (skills.isNotEmpty()) {
            sb.appendLine("## 可用技能")
            for (skill in skills) {
                sb.appendLine("### ${skill.title} (置信度: ${"%.0f".format(skill.confidence * 100)}%)")
                sb.appendLine(skill.steps)
                if (skill.pitfalls.isNotBlank()) {
                    sb.appendLine("⚠ 注意: ${skill.pitfalls}")
                }
                sb.appendLine()
            }
        }

        // 注入可用 MCP 工具
        val toolsPrompt = mcpGateway.buildToolsPrompt()
        if (toolsPrompt.isNotBlank()) {
            sb.appendLine("## 可用设备工具")
            sb.appendLine(toolsPrompt)
        }

        return sb.toString()
    }
}
```

---

## Phase 4: 进化引擎

### Task 4.1: 实现 FactExtractor（事实提炼器）

**Objective:** 从用户对话和 LLM 响应中提取新事实，自动写入记忆

**Files:**
- Create: `app/src/main/java/com/example/aicontrolall/evolution/FactExtractor.kt`

**Complete code:**

```kotlin
package com.example.aicontrolall.evolution

import com.example.aicontrolall.memory.MemoryStore

class FactExtractor(private val memoryStore: MemoryStore) {

    /**
     * 基于启发式规则从用户输入中提取事实。
     * 未来可以改为用小模型做抽取，当前用规则匹配常见模式。
     */
    fun extract(userInput: String, assistantResponse: String): List<String> {
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
```

---

### Task 4.2: 实现 PatternRecognizer（模式识别器）

**Objective:** 检测重复出现的交互模式，超过阈值（默认3次）自动生成技能

**Files:**
- Create: `app/src/main/java/com/example/aicontrolall/evolution/PatternRecognizer.kt`

**Complete code:**

```kotlin
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
        // 最简单的模式识别：相同的触发词出现多次
        val triggerWords = extractTriggerWords(currentUserInput)
        if (triggerWords.isEmpty()) return null

        for (word in triggerWords) {
            val count = recentMessages.count { msg ->
                msg.role == "user" && msg.content.contains(word)
            }
            if (count >= PATTERN_THRESHOLD - 1) { // -1 因为当前消息也算一次
                // 检查是否已有此技能
                val existingSkill = skillStore.getByName("auto-$word")
                if (existingSkill == null) {
                    return createAutoSkill(word)
                }
            }
        }
        return null
    }

    private fun extractTriggerWords(input: String): List<String> {
        // 提取2-4字的关键动作词
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
            steps = "[{\"step\":1,\"action\":\"调用相关 MCP 工具执行${trigger}操作\"},{\"step\":2,\"action\":\"确认结果并告知用户\"}]",
            pitfalls = "这是一个自动生成的技能，可能需要手动调整步骤",
            confidence = 0.3f  // 自动生成的技能低置信度起始
        )
    }

    /**
     * 从最近消息中提取"这个步骤有效"的证据
     * 用于增量提高技能置信度
     */
    fun detectSkillSuccess(skillName: String, recentMessages: List<Message>): Boolean {
        // 如果最近2条 assistant 回复中没有报错，认为技能执行成功
        val lastAssistantMsgs = recentMessages.takeLast(2).filter { it.role == "assistant" }
        if (lastAssistantMsgs.isEmpty()) return false
        return lastAssistantMsgs.none {
            it.content.contains("Error") ||
            it.content.contains("失败") ||
            it.content.contains("无法")
        }
    }
}
```

---

### Task 4.3: 实现 EvolutionCycle（进化编排器）

**Objective:** 在每个对话回合后运行进化循环——提取事实、检测模式、更新置信度

**Files:**
- Create: `app/src/main/java/com/example/aicontrolall/evolution/EvolutionCycle.kt`

**Complete code:**

```kotlin
package com.example.aicontrolall.evolution

import com.example.aicontrolall.memory.MemoryStore
import com.example.aicontrolall.memory.SessionStore
import com.example.aicontrolall.memory.SkillStore
import com.example.aicontrolall.memory.models.Message

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
        toolResults: List<String>,
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
```

---

## Phase 5: Agent Core 主循环 + UI 集成

### Task 5.1: 实现 AgentConfig

**Objective:** 集中管理所有配置

**Files:**
- Create: `app/src/main/java/com/example/aicontrolall/core/AgentConfig.kt`

**Complete code:**

```kotlin
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
```

---

### Task 5.2: 实现 AgentCore（主循环）

**Objective:** 核心调度循环：接收输入 → 查记忆/技能 → 拼 prompt → 调 LLM → 执行工具 → 进化 → 返回结果

**Files:**
- Create: `app/src/main/java/com/example/aicontrolall/core/AgentCore.kt`

**Complete code:**

```kotlin
package com.example.aicontrolall.core

import com.example.aicontrolall.evolution.EvolutionCycle
import com.example.aicontrolall.llm.LlmClient
import com.example.aicontrolall.llm.LlmResponse
import com.example.aicontrolall.mcp.McpGateway
import com.example.aicontrolall.memory.MemoryStore
import com.example.aicontrolall.memory.SessionStore
import com.example.aicontrolall.memory.SkillStore
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

class AgentCore(
    private val config: AgentConfig,
    private val memoryStore: MemoryStore,
    private val skillStore: SkillStore,
    private val sessionStore: SessionStore,
    private val mcpGateway: McpGateway,
    private val llmClient: LlmClient,
    private val promptBuilder: PromptBuilder,
    private val evolutionCycle: EvolutionCycle
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    data class AgentResult(
        val reply: String,
        val toolResults: List<McpGateway.ToolResult> = emptyList()
    )

    /**
     * 处理用户输入，返回 Agent 响应。
     * 这是核心循环的入口，UI 层调用此方法。
     */
    suspend fun processInput(
        userInput: String,
        sessionId: String
    ): AgentResult = withContext(Dispatchers.IO) {
        // 1. 保存用户消息
        sessionStore.addMessage(sessionId, "user", userInput)
        if (sessionStore.getRecentMessages(sessionId, 2).size <= 1) {
            sessionStore.updateSessionTitle(sessionId, userInput.take(30))
        }

        // 2. 组装 prompt
        val messages = promptBuilder.build(userInput, sessionId)
        val tools = promptBuilder.buildTools()

        // 3. 调用 LLM
        val response = llmClient.chat(messages, tools)

        // 4. 如果有工具调用，先执行
        val toolResults = mutableListOf<McpGateway.ToolResult>()
        if (response.toolCalls != null && response.toolCalls.length() > 0) {
            val results = mcpGateway.executeBatch(response.toolCalls)
            toolResults.addAll(results)

            // 将工具结果追加到消息历史，再调一次 LLM 生成最终回复
            messages.put(JSONObject().apply {
                put("role", "assistant")
                put("content", response.content)
                put("tool_calls", response.toolCalls)
            })

            // 添加 tool 结果消息
            for (result in results) {
                messages.put(JSONObject().apply {
                    put("role", "tool")
                    put("content", result.output)
                    put("tool_call_id", result.toolName)
                })
            }

            // 第二次调用 LLM，获取最终回复
            val secondResponse = llmClient.chat(messages, JSONArray())  // 不带 tools
            val finalReply = secondResponse.content

            // 保存 assistant 回复
            sessionStore.addMessage(sessionId, "assistant", finalReply)

            // 进化循环
            evolutionCycle.evolve(
                userInput = userInput,
                assistantResponse = finalReply,
                toolResults = results.map { it.output },
                sessionId = sessionId
            )

            AgentResult(reply = finalReply, toolResults = results)
        } else {
            // 无工具调用，直接返回
            sessionStore.addMessage(sessionId, "assistant", response.content)

            // 进化循环
            evolutionCycle.evolve(
                userInput = userInput,
                assistantResponse = response.content,
                toolResults = emptyList(),
                sessionId = sessionId
            )

            AgentResult(reply = response.content)
        }
    }

    /**
     * 获取系统状态摘要，用于调试
     */
    fun getStatus(): String {
        return buildString {
            appendLine("=== AiControlAll Agent Status ===")
            appendLine("Memories: ${memoryStore.count()}")
            appendLine("Skills: ${skillStore.count()}")
            appendLine("Tools: ${mcpGateway.listTools().size}")
            appendLine("Model: ${config.model}")
            appendLine("Evolution: ${if (config.evolutionEnabled) "enabled" else "disabled"}")
        }
    }

    fun shutdown() {
        scope.cancel()
    }
}
```

---

### Task 5.3: 改造 MainActivity 为聊天界面

**Objective:** 将 Hello World 界面替换为聊天 UI + 初始化所有子系统

**Files:**
- Modify: `app/src/main/java/com/example/aicontrolall/ui/MainActivity.kt`
- Create: `app/src/main/java/com/example/aicontrolall/ui/ChatAdapter.kt`
- Create: `app/src/main/java/com/example/aicontrolall/ui/ChatMessage.kt`
- Modify: `app/src/main/res/layout/activity_main.xml`

**Step 1: ChatMessage UI 模型**

```kotlin
package com.example.aicontrolall.ui

data class ChatMessage(
    val id: Long = System.currentTimeMillis(),
    val text: String,
    val isUser: Boolean,
    val toolResults: String = "" // 如果这轮有工具调用结果，显示在这里
)
```

**Step 2: ChatAdapter（简化版，单布局 chat_message.xml 放在 layout 目录）**

```kotlin
package com.example.aicontrolall.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter : RecyclerView.Adapter<ChatAdapter.ViewHolder>() {
    private val messages = mutableListOf<ChatMessage>()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(android.R.id.text1)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val msg = messages[position]
        val prefix = if (msg.isUser) "👤 " else "🤖 "
        holder.textView.text = "$prefix${msg.text}"
        holder.textView.setBackgroundColor(
            if (msg.isUser) 0xFFE3F2FD.toInt() else 0xFFFFFFFF.toInt()
        )
    }

    override fun getItemCount() = messages.size

    fun addMessage(msg: ChatMessage) {
        messages.add(msg)
        notifyItemInserted(messages.size - 1)
    }
}
```

**Step 3: 重写 activity_main.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="8dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical">

        <TextView
            android:id="@+id/tvStatus"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="记忆: 0 | 技能: 0 | 工具: 0"
            android:textSize="12sp"
            android:textColor="#666"
            android:padding="4dp" />

        <Button
            android:id="@+id/btnSettings"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="⚙"
            android:textSize="16sp"
            android:minWidth="0dp"
            android:padding="8dp" />
    </LinearLayout>

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/rvChat"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal">

        <EditText
            android:id="@+id/etInput"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:hint="输入消息..."
            android:maxLines="3" />

        <Button
            android:id="@+id/btnSend"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="发送" />
    </LinearLayout>
</LinearLayout>
```

**Step 4: MainActivity**

```kotlin
package com.example.aicontrolall.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aicontrolall.R
import com.example.aicontrolall.core.AgentConfig
import com.example.aicontrolall.core.AgentCore
import com.example.aicontrolall.core.PromptBuilder
import com.example.aicontrolall.evolution.EvolutionCycle
import com.example.aicontrolall.llm.LlmClient
import com.example.aicontrolall.mcp.McpGateway
import com.example.aicontrolall.mcp.tools.CameraTool
import com.example.aicontrolall.mcp.tools.SearchMemoriesTool
import com.example.aicontrolall.mcp.tools.SpeechTool
import com.example.aicontrolall.memory.DatabaseHelper
import com.example.aicontrolall.memory.MemoryStore
import com.example.aicontrolall.memory.SessionStore
import com.example.aicontrolall.memory.SkillStore
import com.example.aicontrolall.util.ConfigManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var agentCore: AgentCore
    private lateinit var sessionStore: SessionStore
    private lateinit var mcpGateway: McpGateway
    private lateinit var speechTool: SpeechTool
    private lateinit var chatAdapter: ChatAdapter
    private var sessionId: String = ""

    private lateinit var etInput: EditText
    private lateinit var btnSend: Button
    private lateinit var btnSettings: Button
    private lateinit var tvStatus: TextView
    private lateinit var rvChat: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etInput = findViewById(R.id.etInput)
        btnSend = findViewById(R.id.btnSend)
        btnSettings = findViewById(R.id.btnSettings)
        tvStatus = findViewById(R.id.tvStatus)
        rvChat = findViewById(R.id.rvChat)

        chatAdapter = ChatAdapter()
        rvChat.layoutManager = LinearLayoutManager(this)
        rvChat.adapter = chatAdapter

        // 初始化子系统
        initializeAgent()

        btnSend.setOnClickListener {
            val input = etInput.text.toString().trim()
            if (input.isBlank()) return@setOnClickListener

            etInput.text.clear()
            chatAdapter.addMessage(ChatMessage(text = input, isUser = true))
            rvChat.scrollToPosition(chatAdapter.itemCount - 1)

            processUserInput(input)
        }

        btnSettings.setOnClickListener {
            startActivity(android.content.Intent(this, SettingsActivity::class.java))
        }
    }

    private fun initializeAgent() {
        val dbHelper = DatabaseHelper(this)
        val memoryStore = MemoryStore(dbHelper)
        val skillStore = SkillStore(dbHelper)
        sessionStore = SessionStore(dbHelper)
        val configMgr = ConfigManager(this)

        mcpGateway = McpGateway()
        speechTool = SpeechTool(this)
        mcpGateway.register(speechTool)
        mcpGateway.register(CameraTool(this))
        mcpGateway.register(SearchMemoriesTool(memoryStore, sessionStore))

        val config = AgentConfig.fromConfigManager(configMgr)
        val llmClient = LlmClient(configMgr)
        val promptBuilder = PromptBuilder(memoryStore, skillStore, sessionStore, mcpGateway)
        val evolutionCycle = EvolutionCycle(memoryStore, skillStore, sessionStore)

        agentCore = AgentCore(
            config = config,
            memoryStore = memoryStore,
            skillStore = skillStore,
            sessionStore = sessionStore,
            mcpGateway = mcpGateway,
            llmClient = llmClient,
            promptBuilder = promptBuilder,
            evolutionCycle = evolutionCycle
        )

        // 创建会话
        sessionId = sessionStore.createSession()

        updateStatusBar()
    }

    private fun processUserInput(input: String) {
        lifecycleScope.launch {
            try {
                val result = agentCore.processInput(input, sessionId)
                chatAdapter.addMessage(ChatMessage(text = result.reply, isUser = false))

                if (result.toolResults.isNotEmpty()) {
                    val toolsSummary = result.toolResults.joinToString("\n") {
                        "${if (it.success) "✅" else "❌"} ${it.toolName}: ${it.output}"
                    }
                    chatAdapter.addMessage(ChatMessage(text = "🔧 工具调用:\n$toolsSummary", isUser = false))
                }

                rvChat.scrollToPosition(chatAdapter.itemCount - 1)
                updateStatusBar()
            } catch (e: Exception) {
                chatAdapter.addMessage(ChatMessage(
                    text = "错误: ${e.message}",
                    isUser = false
                ))
            }
        }
    }

    private fun updateStatusBar() {
        val status = agentCore.getStatus().replace("\n", " | ")
        tvStatus.text = status
    }

    override fun onDestroy() {
        super.onDestroy()
        speechTool.shutdown()
        agentCore.shutdown()
    }
}
```

---

### Task 5.4: 添加 Android 依赖（OkHttp, Coroutines, RecyclerView）

**Objective:** 在 build.gradle.kts 中添加必要的依赖

**Files:**
- Modify: `app/build.gradle.kts`

**Change:** 在 dependencies 块中添加：

```kotlin
// OkHttp for DeepSeek API calls
implementation("com.squareup.okhttp3:okhttp:4.12.0")

// Gson for JSON parsing (backup, though we use org.json mostly)
implementation("com.google.code.gson:gson:2.10.1")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

// RecyclerView (for chat)
implementation("androidx.recyclerview:recyclerview:1.3.2")
```

**Step:** 修改 `app/build.gradle.kts` 的 dependencies 块

```kotlin
dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Agent Core dependencies
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
```

---

### Task 5.5: 添加网络权限到 AndroidManifest

**Objective:** 允许应用访问网络（调用 DeepSeek API）

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

**Change:** 在 `<manifest>` 内添加权限：

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

以及在 `<application>` 内注册 SettingsActivity：

```xml
<activity
    android:name=".ui.SettingsActivity"
    android:label="Agent 设置"
    android:exported="false"
    android:theme="@style/Theme.AiControlAll" />
```

---

### Task 5.6: 创建设置页面（SettingsActivity）

**Objective:** 用户可在应用内配置 API Key、Model、Base URL，配置持久化到本地 JSON 文件

**Files:**
- Create: `app/src/main/java/com/example/aicontrolall/ui/SettingsActivity.kt`
- Create: `app/src/main/res/layout/activity_settings.xml`

**Step 1: SettingsActivity**

```kotlin
package com.example.aicontrolall.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.aicontrolall.R
import com.example.aicontrolall.util.ConfigManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var configMgr: ConfigManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        configMgr = ConfigManager(this)

        val etApiKey = findViewById<EditText>(R.id.etApiKey)
        val etModel = findViewById<EditText>(R.id.etModel)
        val etBaseUrl = findViewById<EditText>(R.id.etBaseUrl)
        val swEvolution = findViewById<Switch>(R.id.swEvolution)
        val tvConfigPath = findViewById<TextView>(R.id.tvConfigPath)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val btnBack = findViewById<Button>(R.id.btnBack)

        // 加载当前配置
        etApiKey.setText(configMgr.apiKey)
        etModel.setText(configMgr.model)
        etBaseUrl.setText(configMgr.baseUrl)
        swEvolution.isChecked = configMgr.evolutionEnabled
        tvConfigPath.text = "配置文件: ${configMgr.getConfigFilePath()}"

        btnSave.setOnClickListener {
            configMgr.apiKey = etApiKey.text.toString().trim()
            configMgr.model = etModel.text.toString().trim()
            configMgr.baseUrl = etBaseUrl.text.toString().trim()
            configMgr.evolutionEnabled = swEvolution.isChecked

            Toast.makeText(this, "✅ 设置已保存到本地文件", Toast.LENGTH_SHORT).show()
        }

        btnBack.setOnClickListener {
            finish()
        }
    }
}
```

**Step 2: activity_settings.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="🤖 Agent 设置"
        android:textSize="22sp"
        android:textStyle="bold"
        android:paddingBottom="16dp" />

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="API Key"
        android:textStyle="bold"
        android:paddingTop="8dp" />
    <EditText
        android:id="@+id/etApiKey"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="sk-..."
        android:inputType="textPassword"
        android:singleLine="true" />

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Model"
        android:textStyle="bold"
        android:paddingTop="16dp" />
    <EditText
        android:id="@+id/etModel"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="deepseek-chat"
        android:singleLine="true" />

    <TextView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Base URL"
        android:textStyle="bold"
        android:paddingTop="16dp" />
    <EditText
        android:id="@+id/etBaseUrl"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="https://api.deepseek.com"
        android:singleLine="true" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:paddingTop="16dp"
        android:gravity="center_vertical">

        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="自进化引擎"
            android:textStyle="bold" />
        <Switch
            android:id="@+id/swEvolution"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content" />
    </LinearLayout>

    <TextView
        android:id="@+id/tvConfigPath"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textSize="11sp"
        android:textColor="#999"
        android:paddingTop="16dp" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:layout_marginTop="24dp"
        android:gravity="end">

        <Button
            android:id="@+id/btnBack"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="← 返回"
            android:layout_marginEnd="8dp" />

        <Button
            android:id="@+id/btnSave"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="💾 保存设置" />
    </LinearLayout>
</LinearLayout>
```

**Step 3: 更新 AndroidManifest 注册 SettingsActivity**

在 `AndroidManifest.xml` 的 `<application>` 内添加：

```xml
<activity
    android:name=".ui.SettingsActivity"
    android:label="Agent 设置"
    android:exported="false"
    android:theme="@style/Theme.AiControlAll" />
```

---

## 依赖图

```
ConfigManager
       │
       ├──→ LlmClient ──→ OkHttp ──→ DeepSeek API
       │       │
       │       ▼
       │   PromptBuilder ──→ MemoryStore ──┐
       │       │             SkillStore ───┤
       │       │             SessionStore ─┤
       │       │             McpGateway ───┘
       │       ▼
       │   AgentCore ──→ EvolutionCycle
       │                      │
       │            ┌─────────┼──────────┐
       │            ▼         ▼          ▼
       │          FactExtractor  PatternRecognizer
       │                 │           │
       │                 ▼           ▼
       └──→ SettingsActivity ←─ MainActivity
```

---

## 验证检查点

| Phase | 验证方式 | 预期结果 |
|-------|---------|---------|
| Phase 1 | 编译 + 单测验证 DatabaseHelper 创建成功 | 三张表 + 两个 FTS 虚拟表存在 |
| Phase 1 | 写入/搜索/删除记忆 | MemoryStore CRUD 正常 |
| Phase 1 | 写入技能 + 升/降置信度 | confidence 在 0.1~1.0 区间 |
| Phase 1 | 搜索历史消息 | FTS5 全文搜索返回正确结果 |
| Phase 2 | 注册工具 → McpGateway.listTools() | 返回 3 个工具 |
| Phase 2 | 调用 speak_text → 听到声音 | TTS 正常播放 |
| Phase 3 | 配置 API key → 发送"你好" → 有回复 | DeepSeek API 返回文本 |
| Phase 4 | 说"我住在深圳" → 查 MemoryStore | 自动存入事实 |
| Phase 4 | 重复执行同一操作 3 次 → 查 SkillStore | 自动生成技能 |
| Phase 5 | 聊天界面输入"你好" → 收到回复 | 完整闭环跑通 |
| Phase 5 | 说"拍照" → 调用 Camera → 返回结果 | MCP 工具调度正常 |
| Phase 5 | 打开设置页 → 修改 API key → 保存 | 配置文件 `agent_config.json` 被正确写入 |

---

## Edge Cases & Pitfalls

1. **API key 为空** → AgentCore.processInput 应检查并返回友好提示
2. **网络断开** → LlmClient 应 catch IOException 并返回本地降级响应
3. **记忆爆炸** → getRecent 加 limit=20，定期清理低使用率的旧记忆
4. **技能冲突** → getByName 用 UNIQUE 约束，重复名称插入失败
5. **同时多个工具调用** → executeBatch 顺序执行，避免并发冲突
6. **TTS 未初始化完成就调用** → SpeechTool 用 suspendCancellableCoroutine 等待回调
7. **配置文件损坏** → ConfigManager 读 JSON 失败时返回空对象，写入时覆盖修复
