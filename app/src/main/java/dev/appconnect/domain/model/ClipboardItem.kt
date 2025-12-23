package dev.appconnect.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class ClipboardItem(
    @SerialName("id") val id: String,
    @SerialName("content") val content: String,
    @SerialName("contentType") val contentType: ContentType,
    @SerialName("timestamp") val timestamp: Long,
    @SerialName("ttl") val ttl: Long,
    @SerialName("synced") val synced: Boolean,
    @SerialName("sourceDeviceId") val sourceDeviceId: String?,
    @SerialName("hash") val hash: String,
    @Transient val previewText: String = content.take(50) // Preview for notifications
)

@Serializable
enum class ContentType {
    TEXT,
    IMAGE,
    FILE
}

