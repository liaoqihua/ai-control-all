package com.example.aicontrolall.memory

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val TAG = "DatabaseHelper"
        const val DATABASE_NAME = "aicontrolall.db"
        const val DATABASE_VERSION = 1

        // Memories table
        const val TABLE_MEMORIES = "memories"
        const val COL_MEM_ID = "id"
        const val COL_MEM_TARGET = "target"
        const val COL_MEM_CONTENT = "content"
        const val COL_MEM_TAGS = "tags"
        const val COL_MEM_CREATED_AT = "created_at"
        const val COL_MEM_UPDATED_AT = "updated_at"
        const val COL_MEM_USAGE_COUNT = "usage_count"
        const val COL_MEM_LAST_USED_AT = "last_used_at"

        // Skills table
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

        // Sessions table
        const val TABLE_SESSIONS = "sessions"
        const val COL_SESS_ID = "id"
        const val COL_SESS_TITLE = "title"
        const val COL_SESS_SUMMARY = "summary"
        const val COL_SESS_CREATED_AT = "created_at"

        // Messages table
        const val TABLE_MESSAGES = "messages"
        const val COL_MSG_ID = "id"
        const val COL_MSG_SESSION_ID = "session_id"
        const val COL_MSG_ROLE = "role"
        const val COL_MSG_CONTENT = "content"
        const val COL_MSG_TIMESTAMP = "timestamp"

        // FTS virtual tables (may not be available on all devices)
        const val FTS_MEMORIES = "memories_fts"
        const val FTS_MESSAGES = "messages_fts"
    }

    /** Whether FTS5 is available on this device */
    var ftsAvailable: Boolean = false
        private set

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

        // Try FTS5 — not all devices have it compiled in (e.g. some MIUI builds)
        try {
            db.execSQL("CREATE VIRTUAL TABLE $FTS_MEMORIES USING fts5($COL_MEM_CONTENT, content=$TABLE_MEMORIES)")
            db.execSQL("CREATE VIRTUAL TABLE $FTS_MESSAGES USING fts5($COL_MSG_SESSION_ID, $COL_MSG_ROLE, $COL_MSG_CONTENT, content=$TABLE_MESSAGES)")
            ftsAvailable = true
            Log.i(TAG, "FTS5 enabled")
        } catch (e: Exception) {
            Log.w(TAG, "FTS5 not available on this device, falling back to LIKE search")
            ftsAvailable = false
        }

        // Indexes
        db.execSQL("CREATE INDEX idx_mem_target ON $TABLE_MEMORIES($COL_MEM_TARGET)")
        db.execSQL("CREATE INDEX idx_mem_updated ON $TABLE_MEMORIES($COL_MEM_UPDATED_AT DESC)")
        db.execSQL("CREATE INDEX idx_skill_name ON $TABLE_SKILLS($COL_SKILL_NAME)")
        db.execSQL("CREATE INDEX idx_skill_confidence ON $TABLE_SKILLS($COL_SKILL_CONFIDENCE DESC)")
        db.execSQL("CREATE INDEX idx_msg_session ON $TABLE_MESSAGES($COL_MSG_SESSION_ID)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        try { db.execSQL("DROP TABLE IF EXISTS $FTS_MEMORIES") } catch (_: Exception) {}
        try { db.execSQL("DROP TABLE IF EXISTS $FTS_MESSAGES") } catch (_: Exception) {}
        db.execSQL("DROP TABLE IF EXISTS $TABLE_MEMORIES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SKILLS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_MESSAGES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SESSIONS")
        onCreate(db)
    }
}
