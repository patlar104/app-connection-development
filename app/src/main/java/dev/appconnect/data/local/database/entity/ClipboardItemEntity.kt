package dev.appconnect.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clipboard_items")
data class ClipboardItemEntity(
    @PrimaryKey
    val id: String,
    val content: String,              // Encrypted payload
    val contentType: String,          // TEXT, IMAGE, FILE
    val timestamp: Long,
    val ttl: Long,                    // Time-to-live in milliseconds
    val synced: Boolean,
    val sourceDeviceId: String?,
    val hash: String                  // SHA-256 for loop prevention
)

