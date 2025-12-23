package dev.appconnect.network

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothManager @Inject constructor(
    private val context: Context
) {
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        BluetoothAdapter.getDefaultAdapter()
    }

    private var bluetoothSocket: BluetoothSocket? = null
    private val _connectionState = MutableStateFlow<BluetoothConnectionState>(BluetoothConnectionState.Disconnected)
    val connectionState: StateFlow<BluetoothConnectionState> = _connectionState

    private var messageListener: ((String) -> Unit)? = null

    // UUID for RFCOMM (standard serial port profile)
    private val appConnectUuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun setMessageListener(listener: (String) -> Unit) {
        messageListener = listener
    }

    suspend fun connect(deviceAddress: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val adapter = bluetoothAdapter ?: return@withContext Result.failure(
            Exception("Bluetooth adapter not available")
        )

        if (!adapter.isEnabled) {
            return@withContext Result.failure(Exception("Bluetooth is disabled"))
        }

        return@withContext try {
            val device = adapter.getRemoteDevice(deviceAddress)
            Timber.d("Connecting to Bluetooth device: ${device.name} ($deviceAddress)")

            _connectionState.value = BluetoothConnectionState.Connecting

            val socket = device.createRfcommSocketToServiceRecord(appConnectUuid)
            socket.connect()

            bluetoothSocket = socket
            _connectionState.value = BluetoothConnectionState.Connected

            // Start listening for messages
            startListening(socket)

            Timber.d("Bluetooth connected successfully")
            Result.success(true)
        } catch (e: IOException) {
            Timber.e(e, "Bluetooth connection failed")
            _connectionState.value = BluetoothConnectionState.Disconnected
            Result.failure(e)
        } catch (e: SecurityException) {
            Timber.e(e, "Bluetooth permission denied")
            _connectionState.value = BluetoothConnectionState.Disconnected
            Result.failure(e)
        }
    }

    fun disconnect() {
        try {
            bluetoothSocket?.close()
            bluetoothSocket = null
            _connectionState.value = BluetoothConnectionState.Disconnected
            Timber.d("Bluetooth disconnected")
        } catch (e: Exception) {
            Timber.e(e, "Error disconnecting Bluetooth")
        }
    }

    suspend fun send(message: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val socket = bluetoothSocket ?: return@withContext Result.failure(
            Exception("Not connected")
        )

        return@withContext try {
            val outputStream = socket.outputStream
            outputStream.write(message.toByteArray())
            outputStream.flush()
            Timber.d("Sent Bluetooth message: ${message.take(50)}")
            Result.success(true)
        } catch (e: IOException) {
            Timber.e(e, "Failed to send Bluetooth message")
            Result.failure(e)
        }
    }

    private suspend fun startListening(socket: BluetoothSocket) = withContext(Dispatchers.IO) {
        try {
            val inputStream = socket.inputStream
            val buffer = ByteArray(1024)

            while (_connectionState.value == BluetoothConnectionState.Connected) {
                val bytes = inputStream.read(buffer)
                if (bytes > 0) {
                    val message = String(buffer, 0, bytes, Charsets.UTF_8)
                    Timber.d("Received Bluetooth message: ${message.take(50)}")
                    messageListener?.invoke(message)
                }
            }
        } catch (e: IOException) {
            if (_connectionState.value != BluetoothConnectionState.Disconnected) {
                Timber.e(e, "Bluetooth read error")
                _connectionState.value = BluetoothConnectionState.Disconnected
            }
        }
    }

    enum class BluetoothConnectionState {
        Disconnected,
        Connecting,
        Connected
    }
}

