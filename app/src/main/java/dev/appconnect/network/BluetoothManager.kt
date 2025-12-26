package dev.appconnect.network

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
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
    @param:ApplicationContext private val context: Context
) {
    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bluetoothManager?.adapter
        } else {
            @Suppress("DEPRECATION")
            BluetoothAdapter.getDefaultAdapter()
        }
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

    suspend fun connect(deviceAddress: String): kotlin.Result<Boolean> = withContext(Dispatchers.IO) {
        // Check for BLUETOOTH_CONNECT permission on Android 12+ (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return@withContext kotlin.Result.failure(
                    SecurityException("BLUETOOTH_CONNECT permission not granted")
                )
            }
        }
        
        val adapter = bluetoothAdapter ?: return@withContext kotlin.Result.failure(
            Exception("Bluetooth adapter not available")
        )

        if (!adapter.isEnabled) {
            return@withContext kotlin.Result.failure(Exception("Bluetooth is disabled"))
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
            kotlin.Result.success(true)
        } catch (e: IOException) {
            Timber.e(e, "Bluetooth connection failed")
            _connectionState.value = BluetoothConnectionState.Disconnected
            kotlin.Result.failure(e)
        } catch (e: SecurityException) {
            Timber.e(e, "Bluetooth permission denied")
            _connectionState.value = BluetoothConnectionState.Disconnected
            kotlin.Result.failure(e)
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

    suspend fun send(message: String): kotlin.Result<Boolean> = withContext(Dispatchers.IO) {
        val socket = bluetoothSocket ?: return@withContext kotlin.Result.failure(
            Exception("Not connected")
        )

        return@withContext try {
            val outputStream = socket.outputStream
            outputStream.write(message.toByteArray())
            outputStream.flush()
            Timber.d("Sent Bluetooth message: ${message.take(50)}")
            kotlin.Result.success(true)
        } catch (e: IOException) {
            Timber.e(e, "Failed to send Bluetooth message")
            kotlin.Result.failure(e)
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

