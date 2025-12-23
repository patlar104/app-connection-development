package dev.appconnect.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class QrConnectionInfo(
    @SerialName("n") val name: String,
    @SerialName("ip") val ip: String,
    @SerialName("p") val port: Int,
    @SerialName("k") val publicKey: String,
    @SerialName("fp") val certFingerprint: String
)

