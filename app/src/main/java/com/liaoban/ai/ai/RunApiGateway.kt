package com.liaoban.ai.ai

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.liaoban.ai.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class RunApiGateway(context: Context) {
    private val settings = AppSettings(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun chat(
        model: String,
        systemPrompt: String,
        userPrompt: String
    ): String = withContext(Dispatchers.IO) {

        val endpoint = settings.endpoint.trim()
        val key = settings.apiKey.trim()

        require(endpoint.startsWith("https://")) {
            "请先在“AI设置”里填写 RunAPI 文档提供的完整 HTTPS Chat Completions 接口地址。"
        }
        require(key.isNotBlank()) {
            "请先在“AI设置”里填写 RunAPI API Key。"
        }
        require(model.isNotBlank()) {
            "这个联系人的模型 ID 还没填写，请进入联系人设置填写 RunAPI 中的实际模型 ID。"
        }

        val bodyObject = JsonObject().apply {
            addProperty("model", model)
            add("messages", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("role", "system")
                    addProperty("content", systemPrompt)
                })
                add(JsonObject().apply {
                    addProperty("role", "user")
                    addProperty("content", userPrompt)
                })
            })
        }

        val body = bodyObject.toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $key")
            .header("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val responseText = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                val safe = responseText
                    .replace(key, "***")
                    .take(800)
                error("RunAPI 请求失败 HTTP ${response.code}: $safe")
            }

            val json = runCatching {
                JsonParser.parseString(responseText)
            }.getOrElse {
                error("RunAPI 返回的不是有效 JSON：${responseText.take(500)}")
            }

            extractText(json)
                ?: error("RunAPI 返回成功，但没有找到聊天文本。原始返回：${responseText.take(500)}")
        }
    }

    private fun extractText(json: JsonElement): String? {
        if (!json.isJsonObject) return null
        val root = json.asJsonObject

        root.get("output_text")
            ?.takeIf { it.isJsonPrimitive }
            ?.asString
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        val choices = root.getAsJsonArray("choices")
        if (choices != null && choices.size() > 0) {
            val first = choices[0].asJsonObject
            val message = first.getAsJsonObject("message")
            val content = message?.get("content")
            extractContent(content)?.let { return it }

            first.get("text")
                ?.takeIf { it.isJsonPrimitive }
                ?.asString
                ?.takeIf { it.isNotBlank() }
                ?.let { return it }
        }

        val output = root.getAsJsonArray("output")
        if (output != null) {
            val pieces = mutableListOf<String>()
            for (item in output) {
                if (!item.isJsonObject) continue
                val content = item.asJsonObject.getAsJsonArray("content") ?: continue
                for (part in content) {
                    if (!part.isJsonObject) continue
                    val text = part.asJsonObject.get("text")
                    if (text != null && text.isJsonPrimitive) {
                        val value = text.asString
                        if (value.isNotBlank()) pieces += value
                    }
                }
            }
            if (pieces.isNotEmpty()) return pieces.joinToString("\n")
        }

        return null
    }

    private fun extractContent(content: JsonElement?): String? {
        if (content == null || content.isJsonNull) return null

        if (content.isJsonPrimitive) {
            return content.asString.takeIf { it.isNotBlank() }
        }

        if (content.isJsonArray) {
            val pieces = mutableListOf<String>()
            for (part in content.asJsonArray) {
                if (part.isJsonPrimitive) {
                    val value = part.asString
                    if (value.isNotBlank()) pieces += value
                } else if (part.isJsonObject) {
                    val obj = part.asJsonObject
                    val text = obj.get("text")
                        ?.takeIf { it.isJsonPrimitive }
                        ?.asString
                    if (!text.isNullOrBlank()) pieces += text
                }
            }
            if (pieces.isNotEmpty()) return pieces.joinToString("\n")
        }

        return null
    }
}
