package dev.appconnect.network

import dev.appconnect.core.encryption.KeyExchangeManager
import dev.appconnect.core.encryption.SessionEncryptionManager
import dev.appconnect.core.NotificationManager
import dev.appconnect.data.local.database.dao.PairedDeviceDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.net.ssl.HostnameVerifier
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
    private val trustManager: PairedDeviceTrustManager,
    private val keyExchangeManager: KeyExchangeManager
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
        const val MESSAGE_TYPE_ERROR_REPORT = "error_report"
        const val MESSAGE_TYPE_CONNECTION_STATUS = "connection_status"
        const val MESSAGE_TYPE_CLIPBOARD_SYNC_RESULT = "clipboard_sync_result"
        
        // Status values
        const val STATUS_OK = "ok"
        const val STATUS_ERROR = "error"
        
        // Network constants
        const val MAX_RECONNECT_ATTEMPTS = 10  // More attempts for mobile networks
        const val INITIAL_RECONNECT_DELAY_MS = 2000L  // Start with 2 seconds (more lenient)
        const val MAX_RECONNECT_DELAY_MS = 60000L  // Max 60 seconds (more lenient for mobile)
        
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
            
            // Disable hostname verification since we use certificate pinning via TrustManager
            // The TrustManager verifies the certificate fingerprint, which provides security
            // even without hostname verification. This allows connections to IP addresses
            // that may not be in the certificate's Subject Alternative Names.
            clientBuilder.hostnameVerifier(HostnameVerifier { _, _ -> true })
            
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
        val ws = webSocket ?: run {
            Timber.w("Cannot send: WebSocket is null")
            // Don't immediately reconnect - wait a bit to see if connection recovers
            // Only reconnect if we've been disconnected for a while
            if (shouldReconnect && _connectionState.value == ConnectionState.Disconnected) {
                // Delay reconnection slightly to avoid rapid reconnection attempts
                Thread {
                    try {
                        Thread.sleep(2000) // Wait 2 seconds
                        if (shouldReconnect && webSocket == null) {
                            Timber.d("Attempting reconnection due to null WebSocket")
                            attemptReconnection()
                        }
                    } catch (e: InterruptedException) {
                        Timber.w("Reconnection delay interrupted")
                    }
                }.start()
            }
            return false
        }
        
        // Check if connection is ready
        if (!keyExchangeCompleted) {
            Timber.w("Cannot send: Key exchange not completed")
            return false
        }
        
        val result = ws.send(message)
        if (result) {
            Timber.d("Sent WebSocket message: ${message.take(MESSAGE_PREVIEW_LENGTH)}")
        } else {
            Timber.w("Failed to send WebSocket message - connection may be dead")
            // Don't immediately reconnect - give it a moment to recover
            // The onFailure or onClosed callbacks will handle reconnection
            if (shouldReconnect) {
                // Delay reconnection slightly
                Thread {
                    try {
                        Thread.sleep(3000) // Wait 3 seconds before reconnecting
                        if (shouldReconnect && !result) {
                            Timber.d("Attempting reconnection due to send failure after delay")
                            attemptReconnection()
                        }
                    } catch (e: InterruptedException) {
                        Timber.w("Reconnection delay interrupted")
                    }
                }.start()
            }
        }
        return result
    }
    
    /**
     * Force reconnection attempt (useful for manual reconnection).
     */
    fun forceReconnect(): Boolean {
        if (lastHost == null || lastPort == null) {
            Timber.w("Cannot force reconnect: no connection details stored")
            return false
        }
        
        Timber.d("Force reconnecting...")
        shouldReconnect = true
        reconnectAttempts = 0  // Reset attempts for manual reconnection
        return connect(lastHost!!, lastPort!!, pendingRsaPublicKey)
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
                    // Check if this is a control message (error_report, connection_status, etc.)
                    try {
                        val json = Json.parseToJsonElement(text).jsonObject
                        val type = json[JSON_KEY_TYPE]?.jsonPrimitive?.content
                        
                        when (type) {
                            MESSAGE_TYPE_ERROR_REPORT -> {
                                handleErrorReport(json)
                                return  // Don't pass to message listener
                            }
                            MESSAGE_TYPE_CONNECTION_STATUS -> {
                                handleConnectionStatus(json)
                                return  // Don't pass to message listener
                            }
                            else -> {
                                // Normal clipboard message - pass to listener
                                messageListener?.invoke(text)
                            }
                        }
                    } catch (e: Exception) {
                        // If parsing fails, assume it's a normal clipboard message
                        Timber.w(e, "Failed to parse message type, treating as clipboard message")
                        messageListener?.invoke(text)
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosing(webSocket, code, reason)
                _connectionState.value = ConnectionState.Disconnecting
                Timber.d("WebSocket closing: $code - $reason")
                // Note: onClosed should be called after onClosing, but if server stops abruptly,
                // onClosed might not fire. In that case, onFailure will be called instead.
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosed(webSocket, code, reason)
                _connectionState.value = ConnectionState.Disconnected
                keyExchangeCompleted = false
                sessionEncryption = null
                Timber.d("WebSocket closed: $code - $reason")
                
                // Only attempt reconnection if this was an unexpected closure
                // Normal closures (code 1000) typically mean intentional disconnection
                // Also don't reconnect for policy violations (key exchange failures, etc.)
                if (shouldReconnect && code != CLOSE_CODE_NORMAL && code != CLOSE_CODE_POLICY_VIOLATION) {
                    Timber.d("Unexpected closure (code $code), will attempt reconnection")
                    attemptReconnection()
                } else {
                    // Stop reconnection attempts for normal closures or policy violations
                    if (code == CLOSE_CODE_NORMAL) {
                        Timber.d("Normal closure, not reconnecting")
                    } else if (code == CLOSE_CODE_POLICY_VIOLATION) {
                        Timber.w("Policy violation closure, not reconnecting (likely key exchange failure)")
                    }
                    shouldReconnect = false
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                super.onFailure(webSocket, t, response)
                _connectionState.value = ConnectionState.Disconnected
                keyExchangeCompleted = false
                sessionEncryption = null
                Timber.e(t, "WebSocket failure")
                
                // Attempt automatic reconnection only if we should reconnect
                // (onFailure is called when server stops or network issues occur)
                if (shouldReconnect) {
                    attemptReconnection()
                }
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
        // Exponential backoff with jitter
        val baseDelay = minOf(INITIAL_RECONNECT_DELAY_MS * (1 shl (reconnectAttempts - 1)), MAX_RECONNECT_DELAY_MS)
        val jitter = (Math.random() * 1000).toLong() // Add up to 1 second jitter
        val delayMs = baseDelay + jitter
        
        Timber.d("Reconnection attempt $reconnectAttempts/$MAX_RECONNECT_ATTEMPTS in ${delayMs}ms")
        
        // Schedule reconnection on background thread
        Thread {
            try {
                Thread.sleep(delayMs)
                if (shouldReconnect && lastHost != null && lastPort != null) {
                    Timber.d("Attempting to reconnect to $lastHost:$lastPort...")
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
                    // Reset reconnection attempts on successful connection
                    reconnectAttempts = 0
                    Timber.d("Key exchange completed successfully - connection established")
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

    private fun handleErrorReport(json: JsonObject) {
        try {
            val errorType = json["error_type"]?.jsonPrimitive?.content ?: "unknown"
            val message = json["message"]?.jsonPrimitive?.content ?: "Unknown error"
            val timestamp = try {
                json["timestamp"]?.jsonPrimitive?.content?.toLongOrNull() ?: System.currentTimeMillis()
            } catch (e: Exception) {
                System.currentTimeMillis()
            }
            val details = json["details"]?.jsonObject
            
            Timber.e("Error report from PC: [$errorType] $message (timestamp: $timestamp)")
            if (details != null) {
                Timber.d("Error details: $details")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse error report")
        }
    }
    
    private fun handleConnectionStatus(json: JsonObject) {
        try {
            val status = json["status"]?.jsonPrimitive?.content ?: "unknown"
            val stats = json["stats"]?.jsonObject
            
            Timber.d("Connection status from PC: $status")
            if (stats != null) {
                val messagesSent = try {
                    stats["messages_sent"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                } catch (e: Exception) {
                    0
                }
                val messagesReceived = try {
                    stats["messages_received"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                } catch (e: Exception) {
                    0
                }
                val uptime = try {
                    stats["uptime"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                } catch (e: Exception) {
                    0
                }
                Timber.d("PC stats: sent=$messagesSent, received=$messagesReceived, uptime=${uptime}s")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse connection status")
        }
    }
    
    /**
     * Send error report to PC server.
     */
    fun sendErrorReport(errorType: String, message: String, details: Map<String, Any>? = null): Boolean {
        if (!keyExchangeCompleted) {
            Timber.w("Cannot send error report: key exchange not completed")
            return false
        }
        
        return try {
            val errorReport = buildJsonObject {
                put(JSON_KEY_TYPE, MESSAGE_TYPE_ERROR_REPORT)
                put("error_type", errorType)
                put("message", message)
                put("timestamp", System.currentTimeMillis())
                put("details", details?.let { Json.encodeToJsonElement(it) } ?: buildJsonObject { })
            }
            
            send(Json.encodeToString(JsonObject.serializer(), errorReport))
        } catch (e: Exception) {
            Timber.e(e, "Failed to send error report")
            false
        }
    }
    
    /**
     * Send connection status to PC server.
     */
    fun sendConnectionStatus(status: String): Boolean {
        if (!keyExchangeCompleted) {
            Timber.w("Cannot send connection status: key exchange not completed")
            return false
        }
        
        return try {
            val statusReport = buildJsonObject {
                put(JSON_KEY_TYPE, MESSAGE_TYPE_CONNECTION_STATUS)
                put("status", status)
                put("timestamp", System.currentTimeMillis())
            }
            
            send(Json.encodeToString(JsonObject.serializer(), statusReport))
        } catch (e: Exception) {
            Timber.e(e, "Failed to send connection status")
            false
        }
    }

    enum class ConnectionState {
        Disconnected,
        Connecting,
        Connected,
        Disconnecting
    }
}

