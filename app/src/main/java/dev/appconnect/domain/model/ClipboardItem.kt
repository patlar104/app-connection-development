package dev.appconnect.domain.model

data class ClipboardItem(
    val id: String,
    val content: String,
    val contentType: ContentType,
    val timestamp: Long,
    val ttl: Long,
    val synced: Boolean,
    val sourceDeviceId: String?,
    val hash: String,
    val previewText: String = content.take(50) // Preview for notifications
)

enum class ContentType {
    TEXT,
    IMAGE,
    FILE
}

