package dev.appconnect.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "paired_devices")
data class PairedDeviceEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val publicKey: String,            // For encryption key exchange
    val certificateFingerprint: String, // SHA-256 of X.509 cert
    val lastSeen: Long,
    val isTrusted: Boolean,
    val cdmAssociationId: String?,     // CDM association ID
    val bluetoothAddress: String? = null
)

