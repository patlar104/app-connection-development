package dev.appconnect.core.encryption

import timber.log.Timber
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages AES-GCM encryption/decryption using a session key shared with PC server.
 * Unlike EncryptionManager which uses Android Keystore, this uses an in-memory key
 * established through RSA key exchange.
 */
class SessionEncryptionManager(private val sessionKey: SecretKey) {
    
    private val transformation = EncryptionManager.TRANSFORMATION_AES_GCM
    private val gcmTagLength = EncryptionManager.GCM_TAG_LENGTH
    
    fun encrypt(data: String): EncryptedData {
        return try {
            val cipher = Cipher.getInstance(transformation)
            cipher.init(Cipher.ENCRYPT_MODE, sessionKey)
            
            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
            
            EncryptedData(
                encryptedBytes = encryptedBytes,
                iv = iv
            )
        } catch (e: Exception) {
            Timber.e(e, "Session encryption failed for data of length ${data.length}")
            throw EncryptionException("Failed to encrypt data with session key", e)
        }
    }
    
    fun decrypt(encryptedData: EncryptedData): String {
        return try {
            val cipher = Cipher.getInstance(transformation)
            val spec = GCMParameterSpec(gcmTagLength, encryptedData.iv)
            cipher.init(Cipher.DECRYPT_MODE, sessionKey, spec)
            
            val decryptedBytes = cipher.doFinal(encryptedData.encryptedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Timber.e(e, "Session decryption failed - IV length: ${encryptedData.iv.size}, encrypted length: ${encryptedData.encryptedBytes.size}")
            throw EncryptionException("Failed to decrypt data with session key", e)
        }
    }
}
