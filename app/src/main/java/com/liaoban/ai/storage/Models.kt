package com.liaoban.ai.storage

data class Contact(
    val id: Int,
    val name: String,
    val subtitle: String,
    val model: String,
    val corePersona: String,
    val styleRules: String,
    val proactiveEnabled: Boolean,
    val lastProactiveAt: Long?
)

data class ChatMessage(
    val id: Long,
    val contactId: Int,
    val role: String,
    val text: String,
    val createdAt: Long
)

data class MemoryItem(
    val id: Long,
    val contactId: Int,
    val type: String,
    val content: String,
    val tags: String,
    val importance: Double,
    val confidence: Double,
    val dueAt: Long?,
    val createdAt: Long
)

data class SummaryItem(
    val contactId: Int,
    val summary: String,
    val updatedAt: Long
)
