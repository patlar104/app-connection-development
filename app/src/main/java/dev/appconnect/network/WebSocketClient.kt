package dev.appconnect.network

import dev.appconnect.core.encryption.KeyExchangeManager
import dev.appconnect.core.encryption.SessionEncryptionManager
import dev.appconnect.core.NotificationManager
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
    companion object {
        // JSON message keys
        const val JSON_KEY_TYPE = "type"
        const val JSON_KEY_STATUS = "status"
        const val JSON_KEY_MESSAGE = "message"
        const val JSON_KEY_ENCRYPTED_KEY = "encrypted_key"
        
        // Message types
        const val MESSAGE_TYPE_KEY_EXCHANGE = "key_exchange"
        const val MESSAGE_TYPE_KEY_EXCHANGE_ACK = "key_exchange_ack"
        
        // Status values
        const val STATUS_OK = "ok"
        const val STATUS_ERROR = "error"
        
        // Network constants
        const val MAX_RECONNECT_ATTEMPTS = 5
        const val INITIAL_RECONNECT_DELAY_MS = 1000L
        const val MAX_RECONNECT_DELAY_MS = 30000L
        
        // WebSocket close codes
        const val CLOSE_CODE_NORMAL = 1000
        const val CLOSE_CODE_POLICY_VIOLATION = 1008
        
        // Close messages
        const val CLOSE_MESSAGE_CLIENT_DISCONNECT = "Client disconnect"
        const val CLOSE_MESSAGE_NO_RSA_KEY = "No RSA public key"
        const val CLOSE_MESSAGE_KEY_EXCHANGE_FAILED = "Key exchange failed"
        const val CLOSE_MESSAGE_KEY_EXCHANGE_REJECTED = "Key exchange rejected"
        
        // URL format
        const val WEBSOCKET_URL_FORMAT = "wss://%s:%d"
        
        // Message preview length
        const val MESSAGE_PREVIEW_LENGTH = 50
    }
    
    private var webSocket: WebSocket? = null
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private var messageListener: ((String) -> Unit)? = null
    private var sessionEncryption: SessionEncryptionManager? = null
    private var pendingRsaPublicKey: String? = null
    private var keyExchangeCompleted = false
    
    // Reconnection state
    private var lastHost: String? = null
    private var lastPort: Int? = null
    private var shouldReconnect = false
    private var reconnectAttempts = 0
    
    @Inject
    private lateinit var keyExchangeManager: KeyExchangeManager

    fun setMessageListener(listener: (String) -> Unit) {
        messageListener = listener
    }

    fun connect(host: String, port: Int, rsaPublicKey: String? = null): Boolean {
        return try {
            // Store connection details for reconnection
            lastHost = host
            lastPort = port
            pendingRsaPublicKey = rsaPublicKey
            keyExchangeCompleted = false
            shouldReconnect = true
            reconnectAttempts = 0
            
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

            val url = String.format(WEBSOCKET_URL_FORMAT, host, port)
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
        shouldReconnect = false
        webSocket?.close(CLOSE_CODE_NORMAL, CLOSE_MESSAGE_CLIENT_DISCONNECT)
        webSocket = null
        _connectionState.value = ConnectionState.Disconnected
        keyExchangeCompleted = false
        sessionEncryption = null
        reconnectAttempts = 0
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
            Timber.d("Sent WebSocket message: ${message.take(MESSAGE_PREVIEW_LENGTH)}")
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
                Timber.d("Received WebSocket message: ${text.take(MESSAGE_PREVIEW_LENGTH)}")
                
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
                
                // Attempt automatic reconnection
                attemptReconnection()
            }
        }
    }
    
    private fun attemptReconnection() {
        if (!shouldReconnect || lastHost == null || lastPort == null) {
            Timber.d("Reconnection not applicable")
            return
        }
        
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Timber.w("Max reconnection attempts ($MAX_RECONNECT_ATTEMPTS) reached")
            shouldReconnect = false
            return
        }
        
        reconnectAttempts++
        val delayMs = minOf(INITIAL_RECONNECT_DELAY_MS * (1 shl (reconnectAttempts - 1)), MAX_RECONNECT_DELAY_MS)
        
        Timber.d("Reconnection attempt $reconnectAttempts/$MAX_RECONNECT_ATTEMPTS in ${delayMs}ms")
        
        // Schedule reconnection on background thread
        Thread {
            try {
                Thread.sleep(delayMs)
                if (shouldReconnect) {
                    Timber.d("Attempting to reconnect...")
                    connect(lastHost!!, lastPort!!, pendingRsaPublicKey)
                }
            } catch (e: InterruptedException) {
                Timber.w("Reconnection interrupted")
            }
        }.start()
    }
    
    private fun performKeyExchange(webSocket: WebSocket) {
        try {
            val rsaPublicKey = pendingRsaPublicKey
            if (rsaPublicKey == null) {
                Timber.e("No RSA public key available for key exchange")
                _connectionState.value = ConnectionState.Disconnected
                webSocket.close(CLOSE_CODE_POLICY_VIOLATION, CLOSE_MESSAGE_NO_RSA_KEY)
                return
            }
            
            // Generate session key
            val sessionKey: SecretKey = keyExchangeManager.generateSessionKey()
            
            // Encrypt session key with PC's RSA public key
            val encryptedKeyBase64 = keyExchangeManager.encryptSessionKey(sessionKey, rsaPublicKey)
            
            // Create session encryption manager
            sessionEncryption = SessionEncryptionManager(sessionKey)
            
            // Send key exchange message
            val keyExchangeMessage = """{"$JSON_KEY_TYPE":"$MESSAGE_TYPE_KEY_EXCHANGE","$JSON_KEY_ENCRYPTED_KEY":"$encryptedKeyBase64"}"""
            webSocket.send(keyExchangeMessage)
            
            Timber.d("Sent key exchange message")
        } catch (e: Exception) {
            Timber.e(e, "Key exchange failed")
            _connectionState.value = ConnectionState.Disconnected
            webSocket.close(CLOSE_CODE_POLICY_VIOLATION, CLOSE_MESSAGE_KEY_EXCHANGE_FAILED)
        }
    }
    
    private fun handleKeyExchangeResponse(text: String) {
        try {
            val json = Json.parseToJsonElement(text).jsonObject
            val type = json[JSON_KEY_TYPE]?.jsonPrimitive?.content
            
            if (type == MESSAGE_TYPE_KEY_EXCHANGE_ACK) {
                val status = json[JSON_KEY_STATUS]?.jsonPrimitive?.content
                if (status == STATUS_OK) {
                    keyExchangeCompleted = true
                    _connectionState.value = ConnectionState.Connected
                    Timber.d("Key exchange completed successfully")
                } else {
                    val message = json[JSON_KEY_MESSAGE]?.jsonPrimitive?.content ?: "Unknown error"
                    Timber.e("Key exchange failed: $message")
                    _connectionState.value = ConnectionState.Disconnected
                    webSocket?.close(CLOSE_CODE_POLICY_VIOLATION, CLOSE_MESSAGE_KEY_EXCHANGE_REJECTED)
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

