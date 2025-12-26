package dev.appconnect.network

import dev.appconnect.core.encryption.KeyExchangeManager
import dev.appconnect.core.encryption.SessionEncryptionManager
import dev.appconnect.data.local.database.dao.PairedDeviceDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import timber.log.Timber
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@Singleton
class WebSocketClient @Inject constructor(
    private val trustManager: PairedDeviceTrustManager
) {
    private var webSocket: WebSocket? = null
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private var messageListener: ((String) -> Unit)? = null
    private var sessionEncryption: SessionEncryptionManager? = null
    private var pendingRsaPublicKey: String? = null
    private var keyExchangeCompleted = false
    
    private val keyExchangeManager = KeyExchangeManager()

    fun setMessageListener(listener: (String) -> Unit) {
        messageListener = listener
    }

    fun connect(host: String, port: Int, rsaPublicKey: String? = null): Boolean {
        return try {
            // Store RSA public key for key exchange
            pendingRsaPublicKey = rsaPublicKey
            keyExchangeCompleted = false
            
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(trustManager as TrustManager), SecureRandom())
            }

            val clientBuilder = OkHttpClient.Builder()
            
            // sslSocketFactory is deprecated in OkHttp 4.x, but still functional
            // The replacement (X509TrustManager) is already set via trustManager parameter
            // For OkHttp 4.x, we use the deprecated method with proper TrustManager
            @Suppress("DEPRECATION")
            clientBuilder.sslSocketFactory(sslContext.socketFactory, trustManager)
            
            val client = clientBuilder.build()

            val url = "wss://$host:$port"
            val request = Request.Builder()
                .url(url)
                .build()

            webSocket = client.newWebSocket(request, createWebSocketListener())
            _connectionState.value = ConnectionState.Connecting
            Timber.d("Connecting to WebSocket: $url")
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to connect to WebSocket")
            _connectionState.value = ConnectionState.Disconnected
            false
        }
    }

    fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected
        keyExchangeCompleted = false
        sessionEncryption = null
        Timber.d("WebSocket disconnected")
    }
    
    /**
     * Get the session encryption manager for encrypting/decrypting messages.
     * Returns null if key exchange hasn't completed yet.
     */
    fun getSessionEncryption(): SessionEncryptionManager? = sessionEncryption

    fun send(message: String): Boolean {
        val ws = webSocket ?: return false
        val result = ws.send(message)
        if (result) {
            Timber.d("Sent WebSocket message: ${message.take(50)}")
        } else {
            Timber.w("Failed to send WebSocket message")
        }
        return result
    }

    private fun createWebSocketListener(): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                super.onOpen(webSocket, response)
                Timber.d("WebSocket opened, starting key exchange...")
                
                // Perform key exchange immediately after connection
                performKeyExchange(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                super.onMessage(webSocket, text)
                Timber.d("Received WebSocket message: ${text.take(50)}")
                
                // Handle key exchange acknowledgment
                if (!keyExchangeCompleted) {
                    handleKeyExchangeResponse(text)
                } else {
                    // Normal message handling
                    messageListener?.invoke(text)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosing(webSocket, code, reason)
                _connectionState.value = ConnectionState.Disconnecting
                Timber.d("WebSocket closing: $code - $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosed(webSocket, code, reason)
                _connectionState.value = ConnectionState.Disconnected
                keyExchangeCompleted = false
                sessionEncryption = null
                Timber.d("WebSocket closed: $code - $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                super.onFailure(webSocket, t, response)
                _connectionState.value = ConnectionState.Disconnected
                keyExchangeCompleted = false
                sessionEncryption = null
                Timber.e(t, "WebSocket failure")
            }
        }
    }
    
    private fun performKeyExchange(webSocket: WebSocket) {
        try {
            val rsaPublicKey = pendingRsaPublicKey
            if (rsaPublicKey == null) {
                Timber.e("No RSA public key available for key exchange")
                _connectionState.value = ConnectionState.Disconnected
                webSocket.close(1008, "No RSA public key")
                return
            }
            
            // Generate session key
            val sessionKey: SecretKey = keyExchangeManager.generateSessionKey()
            
            // Encrypt session key with PC's RSA public key
            val encryptedKeyBase64 = keyExchangeManager.encryptSessionKey(sessionKey, rsaPublicKey)
            
            // Create session encryption manager
            sessionEncryption = SessionEncryptionManager(sessionKey)
            
            // Send key exchange message
            val keyExchangeMessage = """{"type":"key_exchange","encrypted_key":"$encryptedKeyBase64"}"""
            webSocket.send(keyExchangeMessage)
            
            Timber.d("Sent key exchange message")
        } catch (e: Exception) {
            Timber.e(e, "Key exchange failed")
            _connectionState.value = ConnectionState.Disconnected
            webSocket.close(1008, "Key exchange failed")
        }
    }
    
    private fun handleKeyExchangeResponse(text: String) {
        try {
            val json = Json.parseToJsonElement(text).jsonObject
            val type = json["type"]?.jsonPrimitive?.content
            
            if (type == "key_exchange_ack") {
                val status = json["status"]?.jsonPrimitive?.content
                if (status == "ok") {
                    keyExchangeCompleted = true
                    _connectionState.value = ConnectionState.Connected
                    Timber.d("Key exchange completed successfully")
                } else {
                    val message = json["message"]?.jsonPrimitive?.content ?: "Unknown error"
                    Timber.e("Key exchange failed: $message")
                    _connectionState.value = ConnectionState.Disconnected
                    webSocket?.close(1008, "Key exchange rejected")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse key exchange response")
        }
    }

    enum class ConnectionState {
        Disconnected,
        Connecting,
        Connected,
        Disconnecting
    }
}

