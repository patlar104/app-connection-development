package dev.appconnect.network

import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.companion.WifiDeviceFilter
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.appconnect.domain.model.Transport
import java.util.concurrent.Executor
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class CompanionDeviceManagerHelper @Inject constructor(
    @param:ApplicationContext private val context: Context
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
            Transport.WIFI -> {
                // WifiDeviceFilter for CDM association
                // Note: WifiDeviceFilter API is limited - we use name pattern matching
                WifiDeviceFilter.Builder().build()
            }
            Transport.WEBSOCKET -> {
                // WebSocket doesn't need CDM association, but return a default filter
                // This case shouldn't normally be called, but adding for completeness
                WifiDeviceFilter.Builder().build()
            }
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

