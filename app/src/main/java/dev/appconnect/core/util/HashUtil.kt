package dev.appconnect.core.util

import dev.appconnect.core.encryption.EncryptionManager
import java.security.MessageDigest

/**
 * Utility object for hash calculations.
 */
object HashUtil {
    /**
     * Calculates SHA-256 hash of the input string.
     * @return Uppercase hexadecimal representation of the hash
     */
    fun sha256(input: String): String {
        val digest = MessageDigest.getInstance(EncryptionManager.HASH_ALGORITHM_SHA256)
        val hash = digest.digest(input.toByteArray())
        return hash.joinToString("") { "%02X".format(it) }
    }
}

