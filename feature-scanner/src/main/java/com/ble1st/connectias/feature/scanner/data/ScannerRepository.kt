package com.ble1st.connectias.feature.scanner.data

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.PeerListListener
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

interface ScannerRepository {
    fun discoverScanners(): Flow<List<ScannerDevice>>
    fun scanDocument(device: ScannerDevice, source: ScanSource): Flow<ScanProgress>
}

sealed class ScanProgress {
    data class Progress(val percentage: Int, val message: String) : ScanProgress()
    data class PageScanned(val bitmap: Bitmap) : ScanProgress()
    data object Completed : ScanProgress()
    data class Error(val message: String) : ScanProgress()
}

@Singleton
@Suppress("DEPRECATION")
class ScannerRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val esclClient: EsclClient
) : ScannerRepository {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiP2pManager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val wifiChannel = wifiP2pManager?.initialize(context, Looper.getMainLooper(), null)

    @SuppressLint("MissingPermission")
    override fun discoverScanners(): Flow<List<ScannerDevice>> = callbackFlow {
        val foundDevices = mutableSetOf<ScannerDevice>()
        val activeListeners = mutableListOf<NsdManager.DiscoveryListener>()
        
        // --- 1. NSD (Network Service Discovery) for Mopria/eSCL ---
        // Look for _uscan._tcp (Universal Scan) and _ipp._tcp (often implies scanning too)
        
        // Create a shared service resolution handler
        fun handleServiceResolved(serviceInfo: NsdServiceInfo) {
            Timber.d("Service resolved: ${serviceInfo.host} : ${serviceInfo.port}")
            val host = serviceInfo.host.hostAddress ?: return
            val device = ScannerDevice(
                name = serviceInfo.serviceName ?: "Unknown Scanner",
                address = host,
                isMopriaCompliant = true
            )
            if (foundDevices.add(device)) {
                trySend(foundDevices.toList())
            }
        }
        
        // Create discovery listener factory
        fun createDiscoveryListener(): NsdManager.DiscoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Timber.d("NSD Discovery started: $regType")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Timber.d("NSD Service found: ${service.serviceName} ${service.serviceType}")
                // Resolve service to get IP/Port
                try {
                    nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            Timber.e("Resolve failed: $errorCode")
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            handleServiceResolved(serviceInfo)
                        }
                    })
                } catch (e: Exception) {
                     Timber.e(e, "Error resolving service")
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Timber.d("Service lost: ${service.serviceName}")
                // Removing logic could be added here, but often IPs persist
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Timber.i("Discovery stopped: $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Timber.e("Discovery start failed: $errorCode")
                try { nsdManager.stopServiceDiscovery(this) } catch (e: Exception) {}
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Timber.e("Discovery stop failed: $errorCode")
                try { nsdManager.stopServiceDiscovery(this) } catch (e: Exception) {}
            }
        }

        // --- 2. Wi-Fi Direct (P2P) ---
        PeerListListener { peers ->
            val refreshedPeers = peers.deviceList
            refreshedPeers.forEach { wifiP2pDevice ->
                Timber.d("P2P Device found: ${wifiP2pDevice.deviceName}")
                // Filter for printers/scanners if possible (primary device type)
                // For now, add everything that looks like a printer/scanner or generic
                if (wifiP2pDevice.primaryDeviceType?.contains("Printer") == true || 
                    wifiP2pDevice.primaryDeviceType?.contains("Scanner") == true) {
                        
                    val device = ScannerDevice(
                        name = wifiP2pDevice.deviceName ?: "Unknown P2P Device",
                        address = wifiP2pDevice.deviceAddress, // MAC address, connection requires P2P flow
                        isMopriaCompliant = false // P2P usually requires specific connection first
                    )
                    if (foundDevices.add(device)) {
                         trySend(foundDevices.toList())
                    }
                }
            }
        }

        // Start NSD - use separate listener for each service type to avoid conflicts
        val serviceTypes = listOf("_uscan._tcp", "_ipp._tcp")
        
        // Start discovery for each service type with its own listener
        serviceTypes.forEach { serviceType ->
            val listener = createDiscoveryListener()
            activeListeners.add(listener)
            try {
                nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            } catch (e: IllegalArgumentException) {
                // Handle "listener already in use" error by stopping and retrying
                Timber.w(e, "Listener already in use for $serviceType, stopping and retrying")
                try {
                    nsdManager.stopServiceDiscovery(listener)
                    delay(100) // Brief delay before retry
                    nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
                } catch (retryException: Exception) {
                    Timber.e(retryException, "Failed to start NSD discovery for $serviceType after retry")
                    activeListeners.remove(listener)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to start NSD discovery for $serviceType")
                activeListeners.remove(listener)
            }
        }

        // Start P2P
        if (wifiP2pManager != null && wifiChannel != null) {
            wifiP2pManager.discoverPeers(wifiChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Timber.d("P2P Discovery initiated")
                }
                override fun onFailure(reasonCode: Int) {
                    Timber.e("P2P Discovery failed: $reasonCode")
                }
            })
            // Note: In a real app, you'd register a BroadcastReceiver for WIFI_P2P_PEERS_CHANGED_ACTION
            // to trigger requestPeers(wifiChannel, peerListListener). 
            // For this implementation, we assume the receiver is handled or we use a periodic poller if simple.
            // But strict implementation requires Receiver. 
            // We will simplify for this context: The UI usually triggers refresh which calls this flow.
        }
        
        // Keep simulation for testing if no devices found immediately (User Experience fallback)
        // launchMockDiscovery() - removed as it's no longer needed

        awaitClose { 
            // Stop all active discoveries
            activeListeners.forEach { listener ->
                try {
                    nsdManager.stopServiceDiscovery(listener)
                } catch (e: Exception) {
                    Timber.d("Error stopping discovery: ${e.message}")
                }
            }
            Timber.d("Stopped scanner discovery")
        }
    }

    override fun scanDocument(device: ScannerDevice, source: ScanSource): Flow<ScanProgress> = callbackFlow {
        Timber.i("Starting scan from ${device.name} via $source")
        
        try {
            trySend(ScanProgress.Progress(0, "Connecting to ${device.address}..."))
            
            // For ADF (Automatic Document Feeder), always use multi-page scan to get all pages
            if (source == ScanSource.ADF) {
                Timber.d("ADF source detected, using multi-page scan")
                val pages = esclClient.performMultiPageScan(device.address, source) { progress, message ->
                    trySend(ScanProgress.Progress(progress, message))
                }
                
                if (pages.isNotEmpty()) {
                    // Send each page individually as it's scanned
                    pages.forEach { pageBitmap ->
                        trySend(ScanProgress.PageScanned(pageBitmap))
                    }
                    trySend(ScanProgress.Completed)
                } else {
                    trySend(ScanProgress.Error("Failed to scan: Scanner may not support eSCL protocol or no pages detected in ADF"))
                }
            } else {
                // For Flatbed, use single page scan
                Timber.d("Flatbed source detected, using single-page scan")
                val bitmap = esclClient.performScan(device.address, source) { progress, message ->
                    trySend(ScanProgress.Progress(progress, message))
                }
                
                if (bitmap != null) {
                    // Successfully scanned via eSCL
                    trySend(ScanProgress.PageScanned(bitmap))
                    trySend(ScanProgress.Completed)
                } else {
                    // eSCL not supported or failed
                    trySend(ScanProgress.Error("Failed to scan: Scanner may not support eSCL protocol. Please ensure the scanner is eSCL/Mopria compatible."))
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "Scan failed")
            trySend(ScanProgress.Error("Scan failed: ${e.message ?: "Unknown error"}"))
        }

        awaitClose { }
    }
    
}
