package com.liaoban.ai.ai

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.liaoban.ai.settings.AppSettings
import com.liaoban.ai.storage.AppDb
import com.liaoban.ai.storage.Contact
import kotlinx.coroutines.delay
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

data class PlannedMessage(
    val text: String,
    val delayMs: Long
)

class ChatEngine(context: Context) {
    private val db = AppDb.get(context)
    private val gateway = RunApiGateway(context)
    private val contextEngine = ContextEngine(context)
    private val settings = AppSettings(context)

    suspend fun send(
        contactId: Int,
        text: String,
        onChanged: () -> Unit = {}
    ) {
        val clean = text.trim()
        if (clean.isBlank()) return

        val contact = db.getContact(contactId) ?: return

        db.addMessage(contactId, "user", clean)
        onChanged()

        if (!settings.configured()) {
            db.addMessage(
                contactId,
                "assistant",
                "先去“AI设置”里填 RunAPI 的完整接口地址和 API Key，然后我才能真正回复。"
            )
            onChanged()
            return
        }

        val bundle = contextEngine.build(contact, clean)
        val raw = gateway.chat(
            model = contact.model,
            systemPrompt = bundle.systemPrompt,
            userPrompt = bundle.userPrompt
        )

        val plan = parsePlan(raw)
        for (item in plan) {
            delay(item.delayMs.coerceIn(150, 2800))
            db.addMessage(
                contactId,
                "assistant",
                item.text.trim().take(1600)
            )
            onChanged()
        }
    }

    suspend fun processMemory(contactId: Int) {
        val contact = db.getContact(contactId) ?: return
        val count = db.userMessageCount(contactId)

        if (!settings.configured()) return
        if (count == 0 || count % 6 != 0) return

        val model = settings.memoryModel.ifBlank { contact.model }
        if (model.isBlank()) return

        val recent = db.getRecentMessages(contactId, 14)
        val oldSummary = db.getSummary(contactId)?.summary.orEmpty()

        val transcript = recent.joinToString("\n") {
            "${if (it.role == "user") "用户" else contact.name}：${it.text}"
        }

        val system = """
            你是聊天记忆整理器，不是聊天角色。
            只保存未来真的可能有用的信息，不把每句闲聊都变成长记忆。
            不保存密码、API Key 等秘密。
            不推断用户没有明确说过的敏感属性。
            如果存在用户明确说出的未来时间事件，可给 ISO-8601 due_at；无法可靠判断时间就填 null。
        """.trimIndent()

        val user = """
            旧摘要：
            ${oldSummary.ifBlank { "暂无" }}

            最近聊天：
            $transcript

            更新近期摘要，并提取最多 4 条真正值得长期保留的记忆。
            type 仅使用：
            preference,event,person,habit,shared_history,communication_style

            只输出 JSON：
            {
              "summary":"不超过140字的更新后摘要",
              "memories":[
                {
                  "type":"event",
                  "content":"一条独立可读的记忆",
                  "tags":"关键词",
                  "importance":0.0,
                  "confidence":0.0,
                  "due_at":null
                }
              ]
            }
        """.trimIndent()

        val raw = runCatching {
            gateway.chat(model, system, user)
        }.getOrNull() ?: return

        val obj = parseJsonObject(raw) ?: return

        val summary = obj.get("summary")
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.trim()
            .orEmpty()

        if (summary.isNotBlank()) {
            db.upsertSummary(contactId, summary.take(500))
        }

        val arr = obj.getAsJsonArray("memories") ?: return
        val max = minOf(arr.size(), 4)

        for (i in 0 until max) {
            val el = arr[i]
            if (!el.isJsonObject) continue
            val mo = el.asJsonObject

            val content = mo.get("content")
                ?.takeIf { it.isJsonPrimitive }
                ?.asString
                ?.trim()
                .orEmpty()

            if (content.length < 2 || db.memoryExists(contactId, content)) continue

            val importance = mo.get("importance")
                ?.takeIf { it.isJsonPrimitive }
                ?.asDouble
                ?.coerceIn(0.0, 1.0)
                ?: 0.5

            val confidence = mo.get("confidence")
                ?.takeIf { it.isJsonPrimitive }
                ?.asDouble
                ?.coerceIn(0.0, 1.0)
                ?: 0.7

            if (importance < 0.35 || confidence < 0.5) continue

            val dueAt = mo.get("due_at")
                ?.takeIf { !it.isJsonNull && it.isJsonPrimitive }
                ?.asString
                ?.let { parseTime(it) }

            db.addMemory(
                contactId = contactId,
                type = mo.get("type")
                    ?.takeIf { it.isJsonPrimitive }
                    ?.asString
                    ?: "event",
                content = content.take(320),
                tags = mo.get("tags")
                    ?.takeIf { it.isJsonPrimitive }
                    ?.asString
                    .orEmpty()
                    .take(120),
                importance = importance,
                confidence = confidence,
                dueAt = dueAt
            )
        }
    }

    suspend fun maybeGenerateProactive(
        contact: Contact,
        reason: String
    ): String? {
        if (!settings.configured()) return null

        val model = settings.proactiveModel.ifBlank { contact.model }
        if (model.isBlank()) return null

        val bundle = contextEngine.buildProactive(contact, reason)
        val raw = runCatching {
            gateway.chat(
                model = model,
                systemPrompt = bundle.systemPrompt,
                userPrompt = bundle.userPrompt
            )
        }.getOrNull() ?: return null

        val obj = parseJsonObject(raw) ?: return null
        val send = obj.get("send")
            ?.takeIf { it.isJsonPrimitive }
            ?.asBoolean
            ?: false

        val text = obj.get("text")
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.trim()
            .orEmpty()

        if (!send || text.isBlank()) return null

        db.addMessage(contact.id, "assistant", text.take(500))
        db.updateContact(
            contact.copy(lastProactiveAt = System.currentTimeMillis())
        )
        return text
    }

    private fun parsePlan(raw: String): List<PlannedMessage> {
        val obj = parseJsonObject(raw)
        val arr = obj?.getAsJsonArray("messages")

        if (arr != null) {
            val result = mutableListOf<PlannedMessage>()
            val max = minOf(arr.size(), 3)

            for (i in 0 until max) {
                val el = arr[i]
                if (!el.isJsonObject) continue
                val item = el.asJsonObject

                val text = item.get("text")
                    ?.takeIf { it.isJsonPrimitive }
                    ?.asString
                    ?.trim()
                    .orEmpty()

                if (text.isBlank()) continue

                val delayMs = item.get("delay_ms")
                    ?.takeIf { it.isJsonPrimitive }
                    ?.asLong
                    ?: 500L

                result += PlannedMessage(text, delayMs)
            }

            if (result.isNotEmpty()) return result
        }

        val fallback = stripFence(raw)
        return listOf(
            PlannedMessage(
                text = fallback.take(1600),
                delayMs = 450L
            )
        )
    }

    private fun parseJsonObject(raw: String): JsonObject? {
        val stripped = stripFence(raw)

        runCatching {
            JsonParser.parseString(stripped).asJsonObject
        }.getOrNull()?.let { return it }

        val start = stripped.indexOf('{')
        val end = stripped.lastIndexOf('}')

        if (start >= 0 && end > start) {
            return runCatching {
                JsonParser.parseString(
                    stripped.substring(start, end + 1)
                ).asJsonObject
            }.getOrNull()
        }

        return null
    }

    private fun stripFence(raw: String): String {
        return raw.trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    private fun parseTime(value: String): Long? {
        if (value.isBlank() || value.equals("null", ignoreCase = true)) return null
        return try {
            OffsetDateTime.parse(value).toInstant().toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
