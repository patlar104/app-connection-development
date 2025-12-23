package dev.appconnect.network

import dev.appconnect.data.local.database.dao.PairedDeviceDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import timber.log.Timber
import java.security.SecureRandom
import java.security.cert.X509Certificate
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

    fun setMessageListener(listener: (String) -> Unit) {
        messageListener = listener
    }

    fun connect(host: String, port: Int): Boolean {
        return try {
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(trustManager as TrustManager), SecureRandom())
            }

            val client = OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustManager)
                .build()

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
        Timber.d("WebSocket disconnected")
    }

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
                _connectionState.value = ConnectionState.Connected
                Timber.d("WebSocket connected")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                super.onMessage(webSocket, text)
                Timber.d("Received WebSocket message: ${text.take(50)}")
                messageListener?.invoke(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosing(webSocket, code, reason)
                _connectionState.value = ConnectionState.Disconnecting
                Timber.d("WebSocket closing: $code - $reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosed(webSocket, code, reason)
                _connectionState.value = ConnectionState.Disconnected
                Timber.d("WebSocket closed: $code - $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                super.onFailure(webSocket, t, response)
                _connectionState.value = ConnectionState.Disconnected
                Timber.e(t, "WebSocket failure")
            }
        }
    }

    enum class ConnectionState {
        Disconnected,
        Connecting,
        Connected,
        Disconnecting
    }
}

