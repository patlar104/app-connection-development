package dev.appconnect.core.util

import android.util.Base64
import dev.appconnect.core.encryption.EncryptedData

/**
 * Utility object for serializing and deserializing EncryptedData to/from Base64 string format.
 * 
 * Format: "ivBase64|encryptedBytesBase64"
 */
object EncryptedDataSerializer {
    /**
     * Serializes EncryptedData to a pipe-delimited Base64 string format.
     * Format: "ivBase64|encryptedBytesBase64"
     */
    fun serialize(data: EncryptedData): String {
        val ivBase64 = Base64.encodeToString(data.iv, Base64.NO_WRAP)
        val encryptedBase64 = Base64.encodeToString(data.encryptedBytes, Base64.NO_WRAP)
        return "$ivBase64|$encryptedBase64"
    }

    /**
     * Parses a pipe-delimited Base64 string back to EncryptedData.
     * @throws IllegalArgumentException if format is invalid
     */
    fun parse(serialized: String): EncryptedData {
        val parts = serialized.split("|")
        require(parts.size == 2) { "Invalid encrypted data format: expected 'iv|encrypted', got ${parts.size} parts" }
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val encryptedBytes = Base64.decode(parts[1], Base64.NO_WRAP)
        return EncryptedData(encryptedBytes = encryptedBytes, iv = iv)
    }
}

