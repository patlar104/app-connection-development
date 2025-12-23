package dev.appconnect.domain.model

data class Device(
    val id: String,
    val name: String,
    val publicKey: String,
    val certificateFingerprint: String,
    val lastSeen: Long,
    val isTrusted: Boolean,
    val cdmAssociationId: String?,
    val bluetoothAddress: String? = null
)

enum class Transport {
    WEBSOCKET,
    BLUETOOTH,
    WIFI
}

