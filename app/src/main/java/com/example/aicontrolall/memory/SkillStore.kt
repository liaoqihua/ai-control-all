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
        val existing = getByName(name)
        val values = ContentValues().apply {
            put(DatabaseHelper.COL_SKILL_STEPS, steps)
            put(DatabaseHelper.COL_SKILL_PITFALLS, pitfalls)
            put(DatabaseHelper.COL_SKILL_VERSION, (existing?.version ?: 0) + 1)
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
