package dev.appconnect.core.encryption

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptionManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        const val KEYSTORE_KEY_ALIAS = "AppConnectEncryptionKey"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val TRANSFORMATION_AES_GCM = "AES/GCM/NoPadding"
        const val TRANSFORMATION_RSA_OAEP = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
        const val KEY_ALGORITHM_AES = "AES"
        const val KEY_ALGORITHM_RSA = "RSA"
        const val HASH_ALGORITHM_SHA256 = "SHA-256"
        const val AES_KEY_SIZE = 256
        const val GCM_TAG_LENGTH = 128
        const val IV_LENGTH = 12
        const val CERT_FINGERPRINT_PREFIX = "SHA256:"
    }
    
    private val keyStore: KeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
        load(null)
    }

    private val keyAlias = KEYSTORE_KEY_ALIAS
    private val transformation = TRANSFORMATION_AES_GCM
    private val gcmTagLength = GCM_TAG_LENGTH
    private val ivLength = IV_LENGTH

    init {
        ensureKeyExists()
    }

    private fun ensureKeyExists() {
        if (!keyStore.containsAlias(keyAlias)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(AES_KEY_SIZE)
                .build()

            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
            Timber.d("Generated new encryption key")
        }
    }

    private fun getSecretKey(): SecretKey {
        val key = keyStore.getKey(keyAlias, null) as? SecretKey
        return key ?: throw IllegalStateException("Encryption key not found")
    }

    fun encrypt(data: String): EncryptedData {
        return try {
            val cipher = Cipher.getInstance(transformation)
            val secretKey = getSecretKey()
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val iv = cipher.iv
            val encryptedBytes = cipher.doFinal(data.toByteArray(Charsets.UTF_8))

            EncryptedData(
                encryptedBytes = encryptedBytes,
                iv = iv
            )
        } catch (e: Exception) {
            Timber.e(e, "Encryption failed for data of length ${data.length}")
            throw EncryptionException("Failed to encrypt data", e)
        }
    }

    fun decrypt(encryptedData: EncryptedData): String {
        return try {
            val cipher = Cipher.getInstance(transformation)
            val secretKey = getSecretKey()
            val spec = GCMParameterSpec(gcmTagLength, encryptedData.iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

            val decryptedBytes = cipher.doFinal(encryptedData.encryptedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Timber.e(e, "Decryption failed - IV length: ${encryptedData.iv.size}, encrypted length: ${encryptedData.encryptedBytes.size}")
            throw EncryptionException("Failed to decrypt data", e)
        }
    }
}

data class EncryptedData(
    val encryptedBytes: ByteArray,
    val iv: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EncryptedData

        if (!encryptedBytes.contentEquals(other.encryptedBytes)) return false
        if (!iv.contentEquals(other.iv)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = encryptedBytes.contentHashCode()
        result = 31 * result + iv.contentHashCode()
        return result
    }
}

class EncryptionException(message: String, cause: Throwable) : Exception(message, cause)

