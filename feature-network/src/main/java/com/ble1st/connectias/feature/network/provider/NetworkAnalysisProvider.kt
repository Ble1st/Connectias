package com.ble1st.connectias.feature.network.provider

import com.ble1st.connectias.core.models.ConnectionType
import com.ble1st.connectias.core.services.NetworkService
import com.ble1st.connectias.feature.network.models.NetworkAnalysis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for network analysis information.
 * Uses NetworkService for all network APIs to reduce code duplication.
 */
@Singleton
class NetworkAnalysisProvider @Inject constructor(
    private val networkService: NetworkService
) {
    /**
     * Gets comprehensive network analysis information.
     * Executes network service calls in parallel for better performance.
     */
    suspend fun getNetworkAnalysis(): NetworkAnalysis = withContext(Dispatchers.IO) {
        Timber.d("Network analysis started")
        try {
            supervisorScope {
                // Execute all network service calls in parallel
                Timber.d("Starting parallel network service calls")
                val isConnectedDeferred = async { 
                    Timber.d("Fetching connection status")
                    networkService.isConnected()
                }
                val connectionTypeDeferred = async { 
                    Timber.d("Fetching connection type")
                    networkService.getConnectionType()
                }
                val dnsServersDeferred = async { 
                    Timber.d("Fetching DNS servers")
                    networkService.getDnsServers()
                }
                val gatewayDeferred = async { 
                    Timber.d("Fetching gateway")
                    networkService.getGateway()
                }
                
                // Await all results
                Timber.d("Awaiting all network service results")
                val isConnected = isConnectedDeferred.await()
                Timber.d("Connection status: $isConnected")
                
                val connectionType = connectionTypeDeferred.await()
                Timber.d("Connection type: ${connectionType.name}")
                
                val dnsServers = dnsServersDeferred.await()
                Timber.d("DNS servers: ${dnsServers.size} servers - ${dnsServers.joinToString(", ")}")
                
                val gateway = gatewayDeferred.await()
                Timber.d("Gateway: $gateway")
                
                val analysis = NetworkAnalysis(
                    isConnected = isConnected,
                    dnsServers = dnsServers,
                    gateway = gateway,
                    networkSpeed = null, // Can be extended later
                    connectionType = connectionType
                )
                Timber.d("Network analysis completed successfully")
                analysis
            }
        } catch (e: Exception) {
            Timber.e(e, "Network analysis failed")
            NetworkAnalysis(
                isConnected = false,
                dnsServers = emptyList(),
                gateway = null,
                networkSpeed = null,
                connectionType = ConnectionType.UNKNOWN
            )
        }
    }
}

