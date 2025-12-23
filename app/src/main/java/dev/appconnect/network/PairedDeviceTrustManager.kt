package dev.appconnect.network

import dev.appconnect.data.local.database.dao.PairedDeviceDao
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.net.ssl.X509TrustManager

class PairedDeviceTrustManager @Inject constructor(
    private val pairedDeviceDao: PairedDeviceDao
) : X509TrustManager {

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
        // Client cert validation (not used for our use case)
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        if (chain.isEmpty()) {
            throw CertificateException("Empty certificate chain")
        }

        val serverCert = chain[0]
        val certFingerprint = calculateSha256Fingerprint(serverCert)

        // Note: Using runBlocking here is acceptable because SSL validation must be synchronous
        // This is called during SSL handshake and cannot be suspended
        val pairedDevices = runBlocking {
            pairedDeviceDao.getTrustedDevices()
        }

        val isTrusted = pairedDevices.any { device ->
            device.certificateFingerprint == certFingerprint
        }

        if (!isTrusted) {
            Timber.w("Untrusted certificate fingerprint: $certFingerprint")
            throw CertificateException("Untrusted certificate: $certFingerprint")
        }

        Timber.d("Certificate verified for fingerprint: $certFingerprint")
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    private fun calculateSha256Fingerprint(certificate: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val fingerprint = digest.digest(certificate.encoded)
        return "SHA256:" + fingerprint.joinToString("") { "%02X".format(it) }
    }
}

