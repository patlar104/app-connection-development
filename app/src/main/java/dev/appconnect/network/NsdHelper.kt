package dev.appconnect.network

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NsdHelper @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        const val MDNS_SERVICE_TYPE = "_appconnect._tcp"
    }
    
    private val nsdManager: NsdManager? by lazy {
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    }

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var resolveListener: NsdManager.ResolveListener? = null

    fun startDiscovery(onServiceFound: (NsdServiceInfo) -> Unit) {
        val manager = nsdManager ?: run {
            Timber.e("NSD Manager not available")
            return
        }

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Timber.d("NSD discovery started: $serviceType")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Timber.d("Service found: ${service.serviceName}")
                if (service.serviceType.contains(MDNS_SERVICE_TYPE)) {
                    // resolveService is deprecated but no replacement API available yet
                    @Suppress("DEPRECATION")
                    manager.resolveService(service, createResolveListener(onServiceFound))
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Timber.d("Service lost: ${service.serviceName}")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Timber.d("NSD discovery stopped: $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Timber.e("Discovery failed: $serviceType, error: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Timber.e("Stop discovery failed: $serviceType, error: $errorCode")
            }
        }

        manager.discoverServices(MDNS_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    private fun createResolveListener(
        onServiceResolved: (NsdServiceInfo) -> Unit
    ): NsdManager.ResolveListener {
        return object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Timber.e("Resolve failed for ${serviceInfo.serviceName}, error: $errorCode")
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val hostAddress = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    serviceInfo.hostAddresses?.firstOrNull()?.hostAddress ?: "unknown"
                } else {
                    @Suppress("DEPRECATION")
                    serviceInfo.host?.hostAddress ?: "unknown"
                }
                Timber.d("Service resolved: ${serviceInfo.serviceName}, host: $hostAddress, port: ${serviceInfo.port}")
                onServiceResolved(serviceInfo)
            }
        }
    }

    fun stopDiscovery() {
        discoveryListener?.let {
            nsdManager?.stopServiceDiscovery(it)
            discoveryListener = null
        }
    }
}

