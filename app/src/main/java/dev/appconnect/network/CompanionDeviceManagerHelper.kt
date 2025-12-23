package dev.appconnect.network

import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.BluetoothLeDeviceFilter
import android.companion.CompanionDeviceManager
import android.companion.WifiDeviceFilter
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import dev.appconnect.domain.model.Transport
import java.util.concurrent.Executor
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class CompanionDeviceManagerHelper @Inject constructor(
    private val context: Context
) {
    private val cdm: CompanionDeviceManager? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(Context.COMPANION_DEVICE_SERVICE) as? CompanionDeviceManager
        } else {
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun associateDevice(
        deviceName: String,
        transport: Transport,
        executor: Executor,
        callback: CompanionDeviceManager.Callback
    ) {
        val manager = cdm ?: run {
            Timber.e("CompanionDeviceManager not available")
            callback.onFailure("CompanionDeviceManager not available")
            return
        }

        val deviceFilter = when (transport) {
            Transport.BLUETOOTH -> BluetoothDeviceFilter.Builder()
                .setNamePattern(Pattern.compile(deviceName))
                .build()
            Transport.BLE -> BluetoothLeDeviceFilter.Builder()
                .setNamePattern(Pattern.compile(deviceName))
                .build()
            Transport.WIFI -> WifiDeviceFilter.Builder()
                .setWifiDeviceFilterType(WifiDeviceFilter.WIFI_DEVICE_FILTER_TYPE_STATIC)
                .apply {
                    // Note: getUriPattern is not available in all API levels
                    // Using setWifiDeviceFilterType with STATIC is the alternative approach
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        // Try to use URI pattern if available
                        try {
                            val method = WifiDeviceFilter.Builder::class.java.getMethod(
                                "getUriPattern",
                                Pattern::class.java
                            )
                            method.invoke(this, Pattern.compile(".*_appconnect._tcp.*"))
                        } catch (e: Exception) {
                            Timber.w(e, "URI pattern not available, using default filter")
                        }
                    }
                }
                .build()
        }

        val associationRequest = AssociationRequest.Builder()
            .addDeviceFilter(deviceFilter)
            .setSingleDevice(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            manager.associate(associationRequest, executor, callback)
        } else {
            @Suppress("DEPRECATION")
            manager.associate(associationRequest, callback, null)
        }

        Timber.d("Initiated CDM association for device: $deviceName via $transport")
    }
}

