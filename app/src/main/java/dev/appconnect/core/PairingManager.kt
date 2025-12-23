package dev.appconnect.core

import dev.appconnect.data.repository.ClipboardRepository
import dev.appconnect.domain.model.QrConnectionInfo
import dev.appconnect.network.CompanionDeviceManagerHelper
import dev.appconnect.network.Transport
import dev.appconnect.network.WebSocketClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PairingManager @Inject constructor(
    private val repository: ClipboardRepository,
    private val webSocketClient: WebSocketClient,
    private val cdmHelper: CompanionDeviceManagerHelper
) {
    private val executor = Executors.newSingleThreadExecutor()

    suspend fun pairFromQrCode(qrJson: String): kotlin.Result<Boolean> = withContext(Dispatchers.IO) {
        return@withContext try {
            // 1. Parse Data
            val info = Json.decodeFromString<QrConnectionInfo>(qrJson)
            Timber.d("Parsed QR code for device: ${info.name}")

            // 2. Validate Reachability (Ping the IP)
            if (!isReachable(info.ip, info.port)) {
                return@withContext kotlin.Result.failure(
                    Exception("PC unreachable at ${info.ip}:${info.port}")
                )
            }

            // 3. Save as "Trusted Device" immediately (Pin the fingerprint)
            val newDevice = dev.appconnect.domain.model.Device(
                id = UUID.randomUUID().toString(),
                name = info.name,
                publicKey = info.publicKey,
                certificateFingerprint = info.certFingerprint,
                lastSeen = System.currentTimeMillis(),
                isTrusted = true,
                cdmAssociationId = null
            )
            repository.savePairedDevice(newDevice)

            // 4. Trigger Companion Device Manager (CDM)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val callback = object : android.companion.CompanionDeviceManager.Callback() {
                    override fun onDeviceFound(chooserLauncher: android.content.IntentSender?) {
                        Timber.d("CDM: Device found")
                        // Launch the chooser
                    }

                    override fun onFailure(error: CharSequence?) {
                        Timber.e("CDM association failed: $error")
                    }
                }
                cdmHelper.associateDevice(info.name, Transport.WIFI, executor, callback)
            }

            // 5. Connect WebSocket
            val connected = webSocketClient.connect(info.ip, info.port)
            if (!connected) {
                return@withContext kotlin.Result.failure(Exception("WebSocket connection failed"))
            }

            Timber.d("Successfully paired with device: ${info.name}")
            kotlin.Result.success(true)
        } catch (e: Exception) {
            Timber.e(e, "QR pairing failed")
            kotlin.Result.failure(e)
        }
    }

    private suspend fun isReachable(ip: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val socket = Socket()
            socket.connect(InetSocketAddress(ip, port), 3000)
            socket.close()
            Timber.d("Device is reachable: $ip:$port")
            true
        } catch (e: Exception) {
            Timber.w("Device not reachable: $ip:$port - ${e.message}")
            false
        }
    }
}

