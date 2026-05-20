package com.example.aicontrolall.memory

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.example.aicontrolall.memory.DatabaseHelper.Companion.COL_MSG_CONTENT
import com.example.aicontrolall.memory.DatabaseHelper.Companion.COL_MSG_ID
import com.example.aicontrolall.memory.DatabaseHelper.Companion.COL_MSG_ROLE
import com.example.aicontrolall.memory.DatabaseHelper.Companion.COL_MSG_SESSION_ID
import com.example.aicontrolall.memory.DatabaseHelper.Companion.COL_MSG_TIMESTAMP
import com.example.aicontrolall.memory.DatabaseHelper.Companion.COL_SESS_CREATED_AT
import com.example.aicontrolall.memory.DatabaseHelper.Companion.COL_SESS_ID
import com.example.aicontrolall.memory.DatabaseHelper.Companion.COL_SESS_TITLE
import com.example.aicontrolall.memory.DatabaseHelper.Companion.FTS_MESSAGES
import com.example.aicontrolall.memory.DatabaseHelper.Companion.TABLE_MESSAGES
import com.example.aicontrolall.memory.DatabaseHelper.Companion.TABLE_SESSIONS
import com.example.aicontrolall.memory.models.Message
import java.util.UUID

class SessionStore(private val dbHelper: DatabaseHelper) {

    fun createSession(): String {
        val db = dbHelper.writableDatabase
        val sessionId = UUID.randomUUID().toString()
        val values = ContentValues().apply {
            put(COL_SESS_ID, sessionId)
            put(COL_SESS_CREATED_AT, System.currentTimeMillis())
        }
        db.insert(TABLE_SESSIONS, null, values)
        return sessionId
    }

    fun addMessage(sessionId: String, role: String, content: String): Long {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(COL_MSG_SESSION_ID, sessionId)
            put(COL_MSG_ROLE, role)
            put(COL_MSG_CONTENT, content)
            put(COL_MSG_TIMESTAMP, System.currentTimeMillis())
        }
        return db.insert(TABLE_MESSAGES, null, values)
    }

    fun getRecentMessages(sessionId: String, limit: Int = 20): List<Message> {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TABLE_MESSAGES, null,
            "$COL_MSG_SESSION_ID = ?", arrayOf(sessionId),
            null, null,
            "$COL_MSG_TIMESTAMP ASC",
            limit.toString()
        )
        val results = mutableListOf<Message>()
        cursor.use {
            while (it.moveToNext()) results.add(cursorToMessage(it))
        }
        return results
    }

    fun searchMessages(query: String, limit: Int = 10): List<Message> {
        val db = dbHelper.readableDatabase
        return if (dbHelper.ftsAvailable) {
            searchFts(db, query, limit)
        } else {
            searchLike(db, query, limit)
        }
    }

    private fun searchFts(db: SQLiteDatabase, query: String, limit: Int): List<Message> {
        val ftsQuery = query.split(" ").joinToString(" OR ") { "\"$it\"" }
        val cursor = db.rawQuery("""
            SELECT m.* FROM $TABLE_MESSAGES m
            INNER JOIN $FTS_MESSAGES f ON m.$COL_MSG_ID = f.rowid
            WHERE $FTS_MESSAGES MATCH ?
            ORDER BY m.$COL_MSG_TIMESTAMP DESC
            LIMIT ?
        """, arrayOf(ftsQuery, limit.toString()))
        return cursorToMessageList(cursor)
    }

    private fun searchLike(db: SQLiteDatabase, query: String, limit: Int): List<Message> {
        val keywords = query.split(" ").filter { it.isNotBlank() }
        if (keywords.isEmpty()) return emptyList()
        val likeClauses = keywords.joinToString(" OR ") { "$COL_MSG_CONTENT LIKE ?" }
        val likeArgs = keywords.map { "%$it%" }.toTypedArray()
        val cursor = db.query(
            TABLE_MESSAGES, null,
            likeClauses, likeArgs,
            null, null,
            "$COL_MSG_TIMESTAMP DESC",
            limit.toString()
        )
        return cursorToMessageList(cursor)
    }

    private fun cursorToMessageList(cursor: android.database.Cursor): List<Message> {
        val results = mutableListOf<Message>()
        cursor.use {
            while (it.moveToNext()) results.add(cursorToMessage(it))
        }
        return results
    }

    fun updateSessionTitle(sessionId: String, title: String) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(COL_SESS_TITLE, title)
        }
        db.update(TABLE_SESSIONS, values,
            "$COL_SESS_ID = ?", arrayOf(sessionId))
    }

    fun getAllSessions(): List<Pair<String, String>> {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TABLE_SESSIONS,
            arrayOf(COL_SESS_ID, COL_SESS_TITLE),
            null, null, null, null,
            "$COL_SESS_CREATED_AT DESC"
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
            id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_MSG_ID)),
            sessionId = cursor.getString(cursor.getColumnIndexOrThrow(COL_MSG_SESSION_ID)),
            role = cursor.getString(cursor.getColumnIndexOrThrow(COL_MSG_ROLE)),
            content = cursor.getString(cursor.getColumnIndexOrThrow(COL_MSG_CONTENT)),
            timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COL_MSG_TIMESTAMP))
        )
    }
}
