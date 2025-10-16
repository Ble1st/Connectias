package com.ble1st.connectias.plugin

import com.ble1st.connectias.api.NetworkResponse
import com.ble1st.connectias.api.NetworkService
import com.ble1st.connectias.api.PingResult
import com.ble1st.connectias.api.TracerouteHop
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Headers
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class PluginNetworkService(
    private val pluginId: String
) : NetworkService {
    
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()
    
    private fun isLocalhost(url: String): Boolean {
        return try {
            val host = java.net.URL(url).host
            val address = InetAddress.getByName(host)
            address.isLoopbackAddress || host == "localhost" || host == "127.0.0.1"
        } catch (e: Exception) {
            Timber.e(e, "Plugin $pluginId: Error checking if URL is localhost: $url")
            false
        }
    }
    
    override suspend fun get(url: String, headers: Map<String, String>): NetworkResponse {
        if (isLocalhost(url)) {
            Timber.w("Plugin $pluginId: Blocked localhost access to $url")
            return NetworkResponse(403, "Access to localhost is forbidden for plugins.", emptyMap())
        }
        
        return try {
            val headersBuilder = Headers.Builder()
            headers.forEach { (key, value) -> headersBuilder.add(key, value) }
            val request = Request.Builder()
                .url(url)
                .headers(headersBuilder.build())
                .get()
                .build()
            
            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            val responseHeaders = response.headers.toMap()
            
            NetworkResponse(response.code, responseBody, responseHeaders)
        } catch (e: Exception) {
            Timber.e(e, "Plugin $pluginId: Error making GET request to $url")
            NetworkResponse(500, "Network error: ${e.message}", emptyMap())
        }
    }
    
    override suspend fun post(url: String, body: String, headers: Map<String, String>): NetworkResponse {
        if (isLocalhost(url)) {
            Timber.w("Plugin $pluginId: Blocked localhost access to $url")
            return NetworkResponse(403, "Access to localhost is forbidden for plugins.", emptyMap())
        }
        
        return try {
            val requestBody = body.toRequestBody("application/json".toMediaType())
            val headersBuilder = Headers.Builder()
            headers.forEach { (key, value) -> headersBuilder.add(key, value) }
            val request = Request.Builder()
                .url(url)
                .headers(headersBuilder.build())
                .post(requestBody)
                .build()
            
            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            val responseHeaders = response.headers.toMap()
            
            NetworkResponse(response.code, responseBody, responseHeaders)
        } catch (e: Exception) {
            Timber.e(e, "Plugin $pluginId: Error making POST request to $url")
            NetworkResponse(500, "Network error: ${e.message}", emptyMap())
        }
    }
    
    override suspend fun ping(host: String): PingResult {
        if (isLocalhost(host)) {
            Timber.w("Plugin $pluginId: Blocked localhost ping to $host")
            return PingResult(host, -1, false)
        }
        
        return try {
            val startTime = System.currentTimeMillis()
            val address = InetAddress.getByName(host)
            val isReachable = address.isReachable(5000) // 5 second timeout
            val endTime = System.currentTimeMillis()
            val latency = if (isReachable) endTime - startTime else -1
            
            PingResult(host, latency, isReachable)
        } catch (e: Exception) {
            Timber.e(e, "Plugin $pluginId: Error pinging $host")
            PingResult(host, -1, false)
        }
    }
    
    override suspend fun traceroute(host: String): List<TracerouteHop> {
        if (isLocalhost(host)) {
            Timber.w("Plugin $pluginId: Blocked localhost traceroute to $host")
            return emptyList()
        }
        
        return try {
            // Simplified traceroute implementation
            val hops = mutableListOf<TracerouteHop>()
            val maxHops = 30
            
            for (ttl in 1..maxHops) {
                try {
                    val startTime = System.currentTimeMillis()
                    val address = InetAddress.getByName(host)
                    val isReachable = address.isReachable(1000)
                    val endTime = System.currentTimeMillis()
                    val latency = endTime - startTime
                    
                    hops.add(TracerouteHop(ttl, address.hostAddress ?: host, latency))
                    
                    if (isReachable) break
                } catch (e: Exception) {
                    Timber.e(e, "Plugin $pluginId: Error in traceroute hop $ttl to $host")
                    break
                }
            }
            
            hops
        } catch (e: Exception) {
            Timber.e(e, "Plugin $pluginId: Error performing traceroute to $host")
            emptyList()
        }
    }
}