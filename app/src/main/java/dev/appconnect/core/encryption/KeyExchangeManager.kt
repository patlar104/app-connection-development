package dev.appconnect.core.encryption

import android.util.Base64
import timber.log.Timber
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Manages RSA-based key exchange for establishing shared AES encryption key with PC server.
 */
class KeyExchangeManager {
    
    /**
     * Generate a random AES-256 key for session encryption.
     */
    fun generateSessionKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(256)
        return keyGenerator.generateKey()
    }
    
    /**
     * Encrypt the AES session key with PC's RSA public key.
     * 
     * @param sessionKey The AES key to encrypt
     * @param rsaPublicKeyBase64 Base64-encoded RSA public key from QR code
     * @return Base64-encoded encrypted key
     */
    fun encryptSessionKey(sessionKey: SecretKey, rsaPublicKeyBase64: String): String {
        try {
            // Decode the Base64 public key
            val publicKeyBytes = Base64.decode(rsaPublicKeyBase64, Base64.NO_WRAP)
            
            // Reconstruct the public key
            val keySpec = X509EncodedKeySpec(publicKeyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            val publicKey = keyFactory.generatePublic(keySpec)
            
            // Encrypt the session key with RSA-OAEP
            val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)
            val encryptedKey = cipher.doFinal(sessionKey.encoded)
            
            // Return as Base64
            return Base64.encodeToString(encryptedKey, Base64.NO_WRAP)
        } catch (e: Exception) {
            Timber.e(e, "Failed to encrypt session key")
            throw KeyExchangeException("Failed to encrypt session key with RSA public key", e)
        }
    }
}

class KeyExchangeException(message: String, cause: Throwable) : Exception(message, cause)
