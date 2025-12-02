package com.ble1st.connectias.feature.network.mdns

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for mDNS/Bonjour service discovery.
 */
@Singleton
class MdnsBrowserProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val _discoveredServices = MutableStateFlow<List<MdnsService>>(emptyList())
    val discoveredServices: StateFlow<List<MdnsService>> = _discoveredServices.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private var discoveryListener: NsdManager.DiscoveryListener? = null

    /**
     * Common mDNS service types.
     */
    val commonServiceTypes = listOf(
        ServiceType("_http._tcp", "HTTP Web Server"),
        ServiceType("_https._tcp", "HTTPS Web Server"),
        ServiceType("_ssh._tcp", "SSH"),
        ServiceType("_sftp-ssh._tcp", "SFTP"),
        ServiceType("_smb._tcp", "SMB/CIFS"),
        ServiceType("_afpovertcp._tcp", "AFP (Apple Filing Protocol)"),
        ServiceType("_ftp._tcp", "FTP"),
        ServiceType("_nfs._tcp", "NFS"),
        ServiceType("_printer._tcp", "Printer"),
        ServiceType("_ipp._tcp", "IPP Printing"),
        ServiceType("_raop._tcp", "AirPlay"),
        ServiceType("_airplay._tcp", "AirPlay"),
        ServiceType("_homekit._tcp", "HomeKit"),
        ServiceType("_googlecast._tcp", "Google Cast"),
        ServiceType("_spotify-connect._tcp", "Spotify Connect"),
        ServiceType("_daap._tcp", "iTunes/DAAP"),
        ServiceType("_dpap._tcp", "iPhoto"),
        ServiceType("_workstation._tcp", "Workstation"),
        ServiceType("_device-info._tcp", "Device Info"),
        ServiceType("_rdlink._tcp", "Remote Desktop"),
        ServiceType("_rfb._tcp", "VNC"),
        ServiceType("_mqtt._tcp", "MQTT"),
        ServiceType("_hap._tcp", "HomeKit Accessory Protocol")
    )

    /**
     * Discovers services of a specific type.
     */
    fun discoverServices(serviceType: String): Flow<MdnsEvent> = callbackFlow {
        _isDiscovering.value = true
        _discoveredServices.value = emptyList()

        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Timber.e("Discovery start failed: $errorCode")
                trySend(MdnsEvent.Error("Discovery start failed: $errorCode"))
                _isDiscovering.value = false
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Timber.e("Discovery stop failed: $errorCode")
                trySend(MdnsEvent.Error("Discovery stop failed: $errorCode"))
            }

            override fun onDiscoveryStarted(serviceType: String?) {
                Timber.d("Discovery started for $serviceType")
                trySend(MdnsEvent.Started(serviceType ?: ""))
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                Timber.d("Discovery stopped for $serviceType")
                trySend(MdnsEvent.Stopped)
                _isDiscovering.value = false
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                serviceInfo?.let { info ->
                    Timber.d("Service found: ${info.serviceName}")
                    resolveService(info) { resolved ->
                        resolved?.let { service ->
                            _discoveredServices.update { it + service }
                            trySend(MdnsEvent.ServiceFound(service))
                        }
                    }
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
                serviceInfo?.let { info ->
                    Timber.d("Service lost: ${info.serviceName}")
                    _discoveredServices.update { services ->
                        services.filter { it.name != info.serviceName }
                    }
                    trySend(MdnsEvent.ServiceLost(info.serviceName))
                }
            }
        }

        discoveryListener = listener

        try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Timber.e(e, "Error starting discovery")
            trySend(MdnsEvent.Error(e.message ?: "Unknown error"))
        }

        awaitClose {
            stopDiscovery()
        }
    }

    /**
     * Stops service discovery.
     */
    fun stopDiscovery() {
        discoveryListener?.let { listener ->
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (e: Exception) {
                Timber.w(e, "Error stopping discovery")
            }
            discoveryListener = null
        }
        _isDiscovering.value = false
    }

    /**
     * Resolves service details.
     */
    private fun resolveService(serviceInfo: NsdServiceInfo, callback: (MdnsService?) -> Unit) {
        nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                Timber.e("Resolve failed: $errorCode")
                callback(null)
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
                serviceInfo?.let { info ->
                    val service = MdnsService(
                        name = info.serviceName,
                        type = info.serviceType,
                        host = info.host?.hostAddress,
                        port = info.port,
                        txtRecords = extractTxtRecords(info)
                    )
                    callback(service)
                }
            }
        })
    }

    private fun extractTxtRecords(serviceInfo: NsdServiceInfo): Map<String, String> {
        val records = mutableMapOf<String, String>()
        try {
            serviceInfo.attributes.forEach { (key, value) ->
                records[key] = value?.let { String(it) } ?: ""
            }
        } catch (e: Exception) {
            Timber.w(e, "Error extracting TXT records")
        }
        return records
    }

    /**
     * Discovers all common services.
     */
    suspend fun discoverAllServices(): List<MdnsService> {
        val allServices = mutableListOf<MdnsService>()
        
        for (serviceType in commonServiceTypes) {
            try {
                discoverServices(serviceType.type).collect { event ->
                    if (event is MdnsEvent.ServiceFound) {
                        allServices.add(event.service)
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Error discovering ${serviceType.type}")
            }
        }
        
        return allServices
    }
}

/**
 * Discovered mDNS service.
 */
@Serializable
data class MdnsService(
    val name: String,
    val type: String,
    val host: String?,
    val port: Int,
    val txtRecords: Map<String, String> = emptyMap()
)

/**
 * mDNS service type.
 */
data class ServiceType(
    val type: String,
    val description: String
)

/**
 * mDNS discovery event.
 */
sealed class MdnsEvent {
    data class Started(val serviceType: String) : MdnsEvent()
    data object Stopped : MdnsEvent()
    data class ServiceFound(val service: MdnsService) : MdnsEvent()
    data class ServiceLost(val serviceName: String) : MdnsEvent()
    data class Error(val message: String) : MdnsEvent()
}
