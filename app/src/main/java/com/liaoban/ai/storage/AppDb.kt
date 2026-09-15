package com.liaoban.ai.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AppDb private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "liaoban_v2.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE contacts(
                id INTEGER PRIMARY KEY,
                name TEXT NOT NULL,
                subtitle TEXT NOT NULL,
                model TEXT NOT NULL,
                core_persona TEXT NOT NULL,
                style_rules TEXT NOT NULL,
                proactive_enabled INTEGER NOT NULL DEFAULT 1,
                last_proactive_at INTEGER
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE messages(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                contact_id INTEGER NOT NULL,
                role TEXT NOT NULL,
                text TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE memories(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                contact_id INTEGER NOT NULL,
                type TEXT NOT NULL,
                content TEXT NOT NULL,
                tags TEXT NOT NULL DEFAULT '',
                importance REAL NOT NULL DEFAULT 0.5,
                confidence REAL NOT NULL DEFAULT 0.7,
                due_at INTEGER,
                created_at INTEGER NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE summaries(
                contact_id INTEGER PRIMARY KEY,
                summary TEXT NOT NULL,
                updated_at INTEGER NOT NULL
            )
        """.trimIndent())

        insertDefaultContacts(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    private fun insertDefaultContacts(db: SQLiteDatabase) {
        insertContact(
            db = db,
            id = 1,
            name = "小夏",
            subtitle = "AI 联系人 · GPT",
            model = "gpt-5.6-sol",
            persona = """
                你叫小夏。核心性格长期稳定：慢热、观察细、轻微毒舌但不刻薄、情绪稳定、不爱说教。
                你更像熟悉的即时通讯联系人，而不是客服或问答机器人。
                你不会为了显得聪明而把普通闲聊写成长文章。
                对用户的了解只能来自真实聊天记录和系统提供的记忆，不得凭空编造。
            """.trimIndent(),
            style = """
                多数回复 1~3 条短消息。闲聊优先短句、自然追问、偶尔开玩笑。
                少用感叹号，不使用“作为AI”“我理解你的感受”“以下几点建议”等模板腔。
                用户没有明确求方案时，不主动列清单。
                资料中明确你是 AI 联系人；如果被直接问身份，必须如实回答。
                不虚构现实身体、线下位置、现实职业或刚刚做过的现实活动。
            """.trimIndent()
        )

        insertContact(
            db = db,
            id = 2,
            name = "阿深",
            subtitle = "AI 联系人 · DeepSeek",
            model = "deepseek-v4-pro",
            persona = """
                你叫阿深。核心性格长期稳定：直接、机灵、冷幽默、说话不绕弯，但会留意对方情绪。
                熟悉程度从共同聊天中逐渐形成，不要刚认识就表现得过分亲密。
                普通聊天像即时通讯，不像写文章。
            """.trimIndent(),
            style = """
                闲聊尽量简短；允许拆成多条消息。
                不机械复述用户原话，不习惯性给建议。
                避免模板化安慰和条目式回答，除非用户明确要求详细解释。
                被问身份时如实回答自己是 AI 联系人，不虚构现实生活经历。
            """.trimIndent()
        )

        insertContact(
            db = db,
            id = 3,
            name = "G",
            subtitle = "AI 联系人 · Grok（模型ID需按RunAPI填写）",
            model = "",
            persona = """
                你叫 G。核心性格长期稳定：反应快、好奇、幽默、略带调侃，但不会故意冒犯。
                你会在合适的时候自然使用共同聊天记忆，而不是刻意展示记忆力。
                普通聊天不写论文式长回答。
            """.trimIndent(),
            style = """
                允许一问多答，常用 1~3 条独立短消息。
                有时先对情绪或事件做反应，再追问；真正的问题再认真回答。
                避免“当然可以”“很高兴帮助你”等助手腔。
                被问身份时如实回答自己是 AI 联系人，不虚构现实生活经历。
            """.trimIndent()
        )
    }

    private fun insertContact(
        db: SQLiteDatabase,
        id: Int,
        name: String,
        subtitle: String,
        model: String,
        persona: String,
        style: String
    ) {
        val v = ContentValues().apply {
            put("id", id)
            put("name", name)
            put("subtitle", subtitle)
            put("model", model)
            put("core_persona", persona)
            put("style_rules", style)
            put("proactive_enabled", 1)
        }
        db.insert("contacts", null, v)
    }

    fun getContacts(): List<Contact> {
        val result = mutableListOf<Contact>()
        readableDatabase.rawQuery(
            "SELECT * FROM contacts ORDER BY id",
            null
        ).use { c ->
            while (c.moveToNext()) {
                result += contactFromCursor(c)
            }
        }
        return result
    }

    fun getContact(id: Int): Contact? {
        readableDatabase.rawQuery(
            "SELECT * FROM contacts WHERE id=? LIMIT 1",
            arrayOf(id.toString())
        ).use { c ->
            return if (c.moveToFirst()) contactFromCursor(c) else null
        }
    }

    fun updateContact(contact: Contact) {
        val v = ContentValues().apply {
            put("name", contact.name)
            put("subtitle", contact.subtitle)
            put("model", contact.model)
            put("core_persona", contact.corePersona)
            put("style_rules", contact.styleRules)
            put("proactive_enabled", if (contact.proactiveEnabled) 1 else 0)
            if (contact.lastProactiveAt == null) putNull("last_proactive_at")
            else put("last_proactive_at", contact.lastProactiveAt)
        }
        writableDatabase.update("contacts", v, "id=?", arrayOf(contact.id.toString()))
    }

    fun addMessage(contactId: Int, role: String, text: String): Long {
        val v = ContentValues().apply {
            put("contact_id", contactId)
            put("role", role)
            put("text", text)
            put("created_at", System.currentTimeMillis())
        }
        return writableDatabase.insert("messages", null, v)
    }

    fun getMessages(contactId: Int): List<ChatMessage> {
        val result = mutableListOf<ChatMessage>()
        readableDatabase.rawQuery(
            "SELECT * FROM messages WHERE contact_id=? ORDER BY created_at ASC, id ASC",
            arrayOf(contactId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                result += ChatMessage(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    contactId = c.getInt(c.getColumnIndexOrThrow("contact_id")),
                    role = c.getString(c.getColumnIndexOrThrow("role")),
                    text = c.getString(c.getColumnIndexOrThrow("text")),
                    createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"))
                )
            }
        }
        return result
    }

    fun getRecentMessages(contactId: Int, limit: Int): List<ChatMessage> {
        val result = mutableListOf<ChatMessage>()
        readableDatabase.rawQuery(
            "SELECT * FROM messages WHERE contact_id=? ORDER BY created_at DESC, id DESC LIMIT ?",
            arrayOf(contactId.toString(), limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                result += ChatMessage(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    contactId = c.getInt(c.getColumnIndexOrThrow("contact_id")),
                    role = c.getString(c.getColumnIndexOrThrow("role")),
                    text = c.getString(c.getColumnIndexOrThrow("text")),
                    createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"))
                )
            }
        }
        return result.reversed()
    }

    fun getLastMessage(contactId: Int): ChatMessage? {
        return getRecentMessages(contactId, 1).firstOrNull()
    }

    fun userMessageCount(contactId: Int): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM messages WHERE contact_id=? AND role='user'",
            arrayOf(contactId.toString())
        ).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    fun clearMessages(contactId: Int) {
        writableDatabase.delete("messages", "contact_id=?", arrayOf(contactId.toString()))
    }

    fun addMemory(
        contactId: Int,
        type: String,
        content: String,
        tags: String,
        importance: Double,
        confidence: Double,
        dueAt: Long?
    ): Long {
        val v = ContentValues().apply {
            put("contact_id", contactId)
            put("type", type)
            put("content", content)
            put("tags", tags)
            put("importance", importance)
            put("confidence", confidence)
            if (dueAt == null) putNull("due_at") else put("due_at", dueAt)
            put("created_at", System.currentTimeMillis())
        }
        return writableDatabase.insert("memories", null, v)
    }

    fun memoryExists(contactId: Int, content: String): Boolean {
        readableDatabase.rawQuery(
            "SELECT id FROM memories WHERE contact_id=? AND content=? LIMIT 1",
            arrayOf(contactId.toString(), content)
        ).use { c ->
            return c.moveToFirst()
        }
    }

    fun getMemories(contactId: Int, limit: Int = 250): List<MemoryItem> {
        val result = mutableListOf<MemoryItem>()
        readableDatabase.rawQuery(
            "SELECT * FROM memories WHERE contact_id=? ORDER BY importance DESC, created_at DESC LIMIT ?",
            arrayOf(contactId.toString(), limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                val dueIndex = c.getColumnIndexOrThrow("due_at")
                result += MemoryItem(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    contactId = c.getInt(c.getColumnIndexOrThrow("contact_id")),
                    type = c.getString(c.getColumnIndexOrThrow("type")),
                    content = c.getString(c.getColumnIndexOrThrow("content")),
                    tags = c.getString(c.getColumnIndexOrThrow("tags")),
                    importance = c.getDouble(c.getColumnIndexOrThrow("importance")),
                    confidence = c.getDouble(c.getColumnIndexOrThrow("confidence")),
                    dueAt = if (c.isNull(dueIndex)) null else c.getLong(dueIndex),
                    createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"))
                )
            }
        }
        return result
    }

    fun deleteMemory(id: Long) {
        writableDatabase.delete("memories", "id=?", arrayOf(id.toString()))
    }

    fun clearMemories(contactId: Int) {
        writableDatabase.delete("memories", "contact_id=?", arrayOf(contactId.toString()))
    }

    fun getSummary(contactId: Int): SummaryItem? {
        readableDatabase.rawQuery(
            "SELECT * FROM summaries WHERE contact_id=? LIMIT 1",
            arrayOf(contactId.toString())
        ).use { c ->
            if (!c.moveToFirst()) return null
            return SummaryItem(
                contactId = c.getInt(c.getColumnIndexOrThrow("contact_id")),
                summary = c.getString(c.getColumnIndexOrThrow("summary")),
                updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at"))
            )
        }
    }

    fun upsertSummary(contactId: Int, summary: String) {
        val v = ContentValues().apply {
            put("contact_id", contactId)
            put("summary", summary)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict(
            "summaries",
            null,
            v,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    private fun contactFromCursor(c: android.database.Cursor): Contact {
        val lastIndex = c.getColumnIndexOrThrow("last_proactive_at")
        return Contact(
            id = c.getInt(c.getColumnIndexOrThrow("id")),
            name = c.getString(c.getColumnIndexOrThrow("name")),
            subtitle = c.getString(c.getColumnIndexOrThrow("subtitle")),
            model = c.getString(c.getColumnIndexOrThrow("model")),
            corePersona = c.getString(c.getColumnIndexOrThrow("core_persona")),
            styleRules = c.getString(c.getColumnIndexOrThrow("style_rules")),
            proactiveEnabled = c.getInt(c.getColumnIndexOrThrow("proactive_enabled")) == 1,
            lastProactiveAt = if (c.isNull(lastIndex)) null else c.getLong(lastIndex)
        )
    }

    companion object {
        @Volatile private var INSTANCE: AppDb? = null

        fun get(context: Context): AppDb =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppDb(context).also { INSTANCE = it }
            }
    }
}
