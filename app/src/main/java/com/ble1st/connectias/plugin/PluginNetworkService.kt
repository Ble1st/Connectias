package com.ble1st.connectias.plugin

import com.ble1st.connectias.api.NetworkResult
import com.ble1st.connectias.api.NetworkService
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
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
            address.isLoopbackAddress || address.isSiteLocalAddress
        } catch (e: Exception) {
            Timber.e(e, "Plugin $pluginId: Error checking if URL is localhost: $url")
            false
        }
    }
    
    override suspend fun get(url: String, headers: Map<String, String>): NetworkResult {
        if (isLocalhost(url)) {
            Timber.w("Plugin $pluginId: Blocked localhost access to $url")
            return NetworkResult.Error(403, "Access to localhost is forbidden for plugins.")
        }
        
        return try {
            val request = Request.Builder()
                .url(url)
                .headers(Headers.of(headers))
                .get()
                .build()
            
            val response = okHttpClient.newCall(request).execute()
            val result = NetworkResult.Success(
                response.code,
                response.body?.string(),
                response.headers.toMap()
            )
            
            Timber.d("Plugin $pluginId: GET request to $url returned ${response.code}")
            result
        } catch (e: Exception) {
            Timber.e(e, "Plugin $pluginId: Network GET request failed for $url")
            NetworkResult.Exception(e)
        }
    }
    
    override suspend fun post(url: String, body: String, headers: Map<String, String>): NetworkResult {
        if (isLocalhost(url)) {
            Timber.w("Plugin $pluginId: Blocked localhost access to $url")
            return NetworkResult.Error(403, "Access to localhost is forbidden for plugins.")
        }
        
        return try {
            val requestBody = body.toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(url)
                .headers(Headers.of(headers))
                .post(requestBody)
                .build()
            
            val response = okHttpClient.newCall(request).execute()
            val result = NetworkResult.Success(
                response.code,
                response.body?.string(),
                response.headers.toMap()
            )
            
            Timber.d("Plugin $pluginId: POST request to $url returned ${response.code}")
            result
        } catch (e: Exception) {
            Timber.e(e, "Plugin $pluginId: Network POST request failed for $url")
            NetworkResult.Exception(e)
        }
    }
    
    override suspend fun ping(host: String, timeout: Int): Boolean {
        if (isLocalhost("http://$host")) {
            Timber.w("Plugin $pluginId: Blocked localhost ping to $host")
            return false
        }
        
        return try {
            val runtime = Runtime.getRuntime()
            val ipProcess = runtime.exec("/system/bin/ping -c 1 -W ${timeout / 1000} $host")
            val exitValue = ipProcess.waitFor()
            val success = exitValue == 0
            
            Timber.d("Plugin $pluginId: Ping to $host ${if (success) "successful" else "failed"}")
            success
        } catch (e: Exception) {
            Timber.e(e, "Plugin $pluginId: Ping failed for $host")
            false
        }
    }
    
    override suspend fun traceroute(host: String): List<String> {
        if (isLocalhost("http://$host")) {
            Timber.w("Plugin $pluginId: Blocked localhost traceroute to $host")
            return emptyList()
        }
        
        val hops = mutableListOf<String>()
        try {
            val runtime = Runtime.getRuntime()
            val process = runtime.exec("traceroute $host")
            val reader = process.inputStream.bufferedReader()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                hops.add(line!!)
            }
            process.waitFor()
            
            Timber.d("Plugin $pluginId: Traceroute to $host completed with ${hops.size} hops")
        } catch (e: Exception) {
            Timber.e(e, "Plugin $pluginId: Traceroute failed for $host")
        }
        return hops
    }
}
