package com.ble1st.connectias.network

import com.ble1st.connectias.api.NetworkResponse
import com.ble1st.connectias.api.NetworkService
import com.ble1st.connectias.api.PingResult
import com.ble1st.connectias.api.TracerouteHop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.net.InetAddress
import kotlin.system.measureTimeMillis

class PluginNetworkService(
    private val pluginId: String,
    private val okHttpClient: OkHttpClient,
    private val hasPermission: () -> Boolean
) : NetworkService {
    
    override suspend fun get(url: String, headers: Map<String, String>): NetworkResponse = withContext(Dispatchers.IO) {
        if (!hasPermission()) throw SecurityException("Plugin missing NETWORK permission")
        
        // Localhost-Blocking
        if (isLocalhost(url)) {
            throw SecurityException("Access to localhost is forbidden for plugins")
        }
        
        val request = Request.Builder()
            .url(url)
            .apply {
                headers.forEach { (key, value) ->
                    addHeader(key, value)
                }
            }
            .build()
        
        val response = okHttpClient.newCall(request).execute()
        
        NetworkResponse(
            statusCode = response.code,
            body = response.body?.string() ?: "",
            headers = response.headers.toMultimap().mapValues { it.value.firstOrNull() ?: "" }
        )
    }
    
    override suspend fun post(url: String, body: String, headers: Map<String, String>): NetworkResponse = withContext(Dispatchers.IO) {
        if (!hasPermission()) throw SecurityException("Plugin missing NETWORK permission")
        
        if (isLocalhost(url)) {
            throw SecurityException("Access to localhost is forbidden for plugins")
        }
        
        val requestBody = body.toRequestBody("application/json; charset=utf-8".toMediaType())
        
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .apply {
                headers.forEach { (key, value) ->
                    addHeader(key, value)
                }
            }
            .build()
        
        val response = okHttpClient.newCall(request).execute()
        
        NetworkResponse(
            statusCode = response.code,
            body = response.body?.string() ?: "",
            headers = response.headers.toMultimap().mapValues { it.value.firstOrNull() ?: "" }
        )
    }
    
    override suspend fun ping(host: String): PingResult = withContext(Dispatchers.IO) {
        if (!hasPermission()) throw SecurityException("Plugin missing NETWORK permission")
        
        if (isLocalhost(host)) {
            throw SecurityException("Access to localhost is forbidden for plugins")
        }
        
        val latency = measureTimeMillis {
            try {
                val address = InetAddress.getByName(host)
                val reachable = address.isReachable(5000)
                if (!reachable) {
                    return@withContext PingResult(host, 0, false)
                }
            } catch (e: Exception) {
                return@withContext PingResult(host, 0, false)
            }
        }
        
        PingResult(host, latency, true)
    }
    
    override suspend fun traceroute(host: String): List<TracerouteHop> = withContext(Dispatchers.IO) {
        if (!hasPermission()) throw SecurityException("Plugin missing NETWORK permission")
        
        if (isLocalhost(host)) {
            throw SecurityException("Access to localhost is forbidden for plugins")
        }
        
        // Basic traceroute implementation
        // Note: This is a simplified version. Real traceroute requires root or special permissions
        val hops = mutableListOf<TracerouteHop>()
        
        try {
            val address = InetAddress.getByName(host)
            val latency = measureTimeMillis {
                address.isReachable(5000)
            }
            
            hops.add(TracerouteHop(1, address.hostAddress ?: host, latency))
        } catch (e: Exception) {
            // Handle network errors
        }
        
        hops
    }
    
    private fun isLocalhost(urlOrHost: String): Boolean {
        val normalized = urlOrHost.lowercase()
        return normalized.contains("localhost") ||
               normalized.contains("127.0.0.1") ||
               normalized.contains("::1") ||
               normalized.contains("0.0.0.0") ||
               normalized.matches(Regex(".*10\\.\\d+\\.\\d+\\.\\d+.*")) || // Private 10.x.x.x
               normalized.matches(Regex(".*192\\.168\\.\\d+\\.\\d+.*")) || // Private 192.168.x.x
               normalized.matches(Regex(".*172\\.(1[6-9]|2[0-9]|3[0-1])\\.\\d+\\.\\d+.*")) // Private 172.16-31.x.x
    }
}
