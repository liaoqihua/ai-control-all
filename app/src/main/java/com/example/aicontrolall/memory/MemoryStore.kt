package com.example.aicontrolall.memory

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.example.aicontrolall.memory.DatabaseHelper.Companion.COL_MEM_CONTENT
import com.example.aicontrolall.memory.DatabaseHelper.Companion.COL_MEM_CREATED_AT
import com.example.aicontrolall.memory.DatabaseHelper.Companion.COL_MEM_ID
import com.example.aicontrolall.memory.DatabaseHelper.Companion.COL_MEM_LAST_USED_AT
import com.example.aicontrolall.memory.DatabaseHelper.Companion.COL_MEM_TAGS
import com.example.aicontrolall.memory.DatabaseHelper.Companion.COL_MEM_TARGET
import com.example.aicontrolall.memory.DatabaseHelper.Companion.COL_MEM_UPDATED_AT
import com.example.aicontrolall.memory.DatabaseHelper.Companion.COL_MEM_USAGE_COUNT
import com.example.aicontrolall.memory.DatabaseHelper.Companion.FTS_MEMORIES
import com.example.aicontrolall.memory.DatabaseHelper.Companion.TABLE_MEMORIES
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
            TABLE_MEMORIES,
            arrayOf(COL_MEM_ID),
            "${COL_MEM_CONTENT} = ?",
            arrayOf(content),
            null, null, null
        )
        cursor.use {
            if (it.moveToFirst()) {
                val id = it.getLong(0)
                val values = ContentValues().apply {
                    put(COL_MEM_UPDATED_AT, now)
                    put(COL_MEM_TAGS, tags)
                }
                db.update(TABLE_MEMORIES, values,
                    "${COL_MEM_ID} = ?", arrayOf(id.toString()))
                return id
            }
        }

        val values = ContentValues().apply {
            put(COL_MEM_TARGET, target)
            put(COL_MEM_CONTENT, content)
            put(COL_MEM_TAGS, tags)
            put(COL_MEM_CREATED_AT, now)
            put(COL_MEM_UPDATED_AT, now)
        }
        return db.insert(TABLE_MEMORIES, null, values)
    }

    /**
     * 搜索近期记忆（最多 20 条），按更新时间倒序
     */
    fun getRecent(limit: Int = 20): List<Memory> {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            TABLE_MEMORIES,
            null, null, null, null, null,
            "${COL_MEM_UPDATED_AT} DESC",
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
     * 用 FTS5 全文搜索记忆，支持多关键词。
     * 如果设备不支持 FTS5，回退到 LIKE 搜索。
     */
    fun search(query: String, limit: Int = 10): List<Memory> {
        val db = dbHelper.readableDatabase
        return if (dbHelper.ftsAvailable) {
            searchFts(db, query, limit)
        } else {
            searchLike(db, query, limit)
        }
    }

    private fun searchFts(db: SQLiteDatabase, query: String, limit: Int): List<Memory> {
        val ftsQuery = query.split(" ").joinToString(" OR ") { "\"$it\"" }
        val cursor = db.rawQuery("""
            SELECT m.* FROM $TABLE_MEMORIES m
            INNER JOIN $FTS_MEMORIES f ON m.$COL_MEM_ID = f.rowid
            WHERE $FTS_MEMORIES MATCH ?
            ORDER BY m.$COL_MEM_UPDATED_AT DESC
            LIMIT ?
        """, arrayOf(ftsQuery, limit.toString()))
        return cursorToMemoryList(cursor)
    }

    private fun searchLike(db: SQLiteDatabase, query: String, limit: Int): List<Memory> {
        val keywords = query.split(" ").filter { it.isNotBlank() }
        if (keywords.isEmpty()) return getRecent(limit)
        val likeClauses = keywords.joinToString(" OR ") { "$COL_MEM_CONTENT LIKE ?" }
        val likeArgs = keywords.map { "%$it%" }.toTypedArray()
        val cursor = db.query(
            TABLE_MEMORIES, null,
            likeClauses, likeArgs,
            null, null,
            "$COL_MEM_UPDATED_AT DESC",
            limit.toString()
        )
        return cursorToMemoryList(cursor)
    }

    private fun cursorToMemoryList(cursor: android.database.Cursor): List<Memory> {
        val results = mutableListOf<Memory>()
        cursor.use {
            while (it.moveToNext()) results.add(cursorToMemory(it))
        }
        return results
    }

    /**
     * 删除一条记忆
     */
    fun remove(id: Long) {
        val db = dbHelper.writableDatabase
        db.delete(TABLE_MEMORIES,
            "${COL_MEM_ID} = ?", arrayOf(id.toString()))
    }

    /**
     * 增加使用计数
     */
    fun incrementUsage(id: Long) {
        val db = dbHelper.writableDatabase
        db.execSQL("""
            UPDATE ${TABLE_MEMORIES}
            SET ${COL_MEM_USAGE_COUNT} = ${COL_MEM_USAGE_COUNT} + 1,
                ${COL_MEM_LAST_USED_AT} = ?
            WHERE ${COL_MEM_ID} = ?
        """, arrayOf(System.currentTimeMillis().toString(), id.toString()))
    }

    /**
     * 获取总记忆数
     */
    fun count(): Int {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM ${TABLE_MEMORIES}", null)
        cursor.use {
            return if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    private fun cursorToMemory(cursor: android.database.Cursor): Memory {
        return Memory(
            id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_MEM_ID)),
            target = cursor.getString(cursor.getColumnIndexOrThrow(COL_MEM_TARGET)),
            content = cursor.getString(cursor.getColumnIndexOrThrow(COL_MEM_CONTENT)),
            tags = cursor.getString(cursor.getColumnIndexOrThrow(COL_MEM_TAGS)) ?: "",
            createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_MEM_CREATED_AT)),
            updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_MEM_UPDATED_AT)),
            usageCount = cursor.getInt(cursor.getColumnIndexOrThrow(COL_MEM_USAGE_COUNT)),
            lastUsedAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_MEM_LAST_USED_AT))
        )
    }
}
