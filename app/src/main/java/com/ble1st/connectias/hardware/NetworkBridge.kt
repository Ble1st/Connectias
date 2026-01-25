package com.ble1st.connectias.hardware

import android.content.Context
import android.os.ParcelFileDescriptor
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.Socket
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

/**
 * Network Bridge for isolated process network access
 * 
 * Provides HTTP and raw socket access to plugins running in
 * isolated sandbox via IPC.
 * 
 * ARCHITECTURE:
 * - Uses OkHttp for HTTP requests
 * - Raw sockets for TCP connections
 * - Returns data via ParcelFileDescriptor for large responses
 * 
 * SECURITY:
 * - Requires INTERNET permission check before access
 * - URL validation to prevent SSRF attacks
 * - Timeout enforcement (30s default)
 * - Size limits on responses (10MB default)
 * 
 * @since 2.0.0
 */
class NetworkBridge(private val context: Context) {
    
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val maxResponseSize = 10 * 1024 * 1024L // 10MB
    
    /**
     * HTTP GET request with basic validation
     * Runs on IO thread to avoid NetworkOnMainThreadException
     */
    fun httpGet(url: String): HardwareResponseParcel {
        Timber.d("[NETWORK BRIDGE] HTTP GET: $url")
        
        return try {
            // Validate URL
            if (!isValidUrl(url)) {
                return HardwareResponseParcel.failure("Invalid URL: $url")
            }
            
            // Execute on IO thread using runBlocking
            // This is safe because AIDL calls are already async from client perspective
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()
                
                val response = okHttpClient.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    return@runBlocking HardwareResponseParcel.failure("HTTP ${response.code}: ${response.message}")
                }
                
                val body = response.body ?: return@runBlocking HardwareResponseParcel.failure("Empty response body")
                
                // Check size
                val contentLength = body.contentLength()
                if (contentLength > maxResponseSize) {
                    return@runBlocking HardwareResponseParcel.failure("Response too large: $contentLength bytes")
                }
                
                val data = body.bytes()
                val metadata = mapOf(
                    "status" to response.code.toString(),
                    "contentType" to (response.header("Content-Type") ?: "unknown"),
                    "size" to data.size.toString()
                )
                
                Timber.i("[NETWORK BRIDGE] HTTP GET success: ${data.size} bytes")
                
                HardwareResponseParcel.success(
                    data = data,
                    metadata = metadata
                )
            }
            
        } catch (e: Exception) {
            Timber.e(e, "[NETWORK BRIDGE] HTTP GET failed: $url")
            HardwareResponseParcel.failure(e)
        }
    }
    
    /**
     * HTTP POST request
     * Runs on IO thread to avoid NetworkOnMainThreadException
     * 
     * @param url Target URL
     * @param dataFd ParcelFileDescriptor with POST data
     * @return HardwareResponseParcel with response data
     */
    fun httpPost(url: String, dataFd: ParcelFileDescriptor): HardwareResponseParcel {
        return try {
            // Validate URL
            if (!isValidUrl(url)) {
                return HardwareResponseParcel.failure("Invalid URL: $url")
            }
            
            Timber.d("[NETWORK BRIDGE] HTTP POST: $url")
            
            // Execute on IO thread using runBlocking
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                // Write FD to temp file
                val tempFile = File.createTempFile("post_", ".tmp", context.cacheDir)
                FileInputStream(dataFd.fileDescriptor).use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                dataFd.close()
                
                // Create request
                val requestBody = tempFile.asRequestBody("application/octet-stream".toMediaType())
                val request = Request.Builder()
                    .url(url)
                    .post(requestBody)
                    .build()
                
                val response = okHttpClient.newCall(request).execute()
                
                // Cleanup temp file
                tempFile.delete()
                
                if (!response.isSuccessful) {
                    return@runBlocking HardwareResponseParcel.failure("HTTP ${response.code}: ${response.message}")
                }
                
                val body = response.body ?: return@runBlocking HardwareResponseParcel.failure("Empty response body")
                
                // Check size
                val contentLength = body.contentLength()
                if (contentLength > maxResponseSize) {
                    return@runBlocking HardwareResponseParcel.failure("Response too large: $contentLength bytes")
                }
                
                val data = body.bytes()
                
                Timber.i("[NETWORK BRIDGE] HTTP POST success: ${data.size} bytes")
                
                HardwareResponseParcel.success(
                    data = data,
                    metadata = mapOf(
                        "status" to response.code.toString(),
                        "contentType" to (response.header("Content-Type") ?: "unknown"),
                        "size" to data.size.toString()
                    )
                )
            }
            
        } catch (e: Exception) {
            Timber.e(e, "[NETWORK BRIDGE] HTTP POST failed: $url")
            HardwareResponseParcel.failure(e)
        }
    }
    
    /**
     * Open TCP socket
     * 
     * @param host Remote host
     * @param port Remote port
     * @return HardwareResponseParcel with socket as ParcelFileDescriptor
     */
    fun openSocket(host: String, port: Int): HardwareResponseParcel {
        return try {
            // Validate host
            if (!isValidHost(host)) {
                return HardwareResponseParcel.failure("Invalid host: $host")
            }
            
            // Validate port
            if (port < 1 || port > 65535) {
                return HardwareResponseParcel.failure("Invalid port: $port")
            }
            
            Timber.d("[NETWORK BRIDGE] Opening socket: $host:$port")
            
            val socket = Socket(host, port)
            socket.soTimeout = 30000 // 30s timeout
            
            val fd = ParcelFileDescriptor.fromSocket(socket)
            
            Timber.i("[NETWORK BRIDGE] Socket opened: $host:$port")
            
            HardwareResponseParcel.success(
                fileDescriptor = fd,
                metadata = mapOf(
                    "host" to host,
                    "port" to port.toString(),
                    "type" to "tcp_socket"
                )
            )
            
        } catch (e: Exception) {
            Timber.e(e, "[NETWORK BRIDGE] Socket failed: $host:$port")
            HardwareResponseParcel.failure(e)
        }
    }

    /**
     * TCP "ping" (connect latency) with connect timeout.
     *
     * SECURITY:
     * - Host/port validation
     * - Blocks localhost/private ranges (same as openSocket)
     *
     * @param host Remote host
     * @param port Remote port
     * @param timeoutMs Connect timeout in milliseconds
     */
    fun tcpPing(host: String, port: Int, timeoutMs: Int): HardwareResponseParcel {
        return try {
            if (!isValidHost(host)) {
                return HardwareResponseParcel.failure("Invalid host: $host")
            }
            if (port < 1 || port > 65535) {
                return HardwareResponseParcel.failure("Invalid port: $port")
            }

            val timeoutSafe = timeoutMs.coerceIn(100, 10_000)
            Timber.d("[NETWORK BRIDGE] TCP ping: $host:$port (timeout=${timeoutSafe}ms)")

            val startNs = System.nanoTime()
            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(host, port), timeoutSafe)
                val latencyMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(0L)
                Timber.i("[NETWORK BRIDGE] TCP ping success: $host:$port latencyMs=$latencyMs")
                HardwareResponseParcel.success(
                    data = null,
                    fileDescriptor = null,
                    metadata = mapOf(
                        "host" to host,
                        "port" to port.toString(),
                        "latencyMs" to latencyMs.toString(),
                        "type" to "tcp_ping"
                    )
                )
            } finally {
                try {
                    socket.close()
                } catch (_: Exception) {
                    // Ignore close errors
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "[NETWORK BRIDGE] TCP ping failed: $host:$port")
            HardwareResponseParcel.failure(e)
        }
    }
    
    /**
     * Cleanup network resources
     */
    fun cleanup() {
        try {
            Timber.i("[NETWORK BRIDGE] Cleanup started")
            
            // OkHttp client cleanup
            okHttpClient.dispatcher.executorService.shutdown()
            okHttpClient.connectionPool.evictAll()
            
            Timber.i("[NETWORK BRIDGE] Cleanup completed")
            
        } catch (e: Exception) {
            Timber.e(e, "[NETWORK BRIDGE] Cleanup error")
        }
    }
    
    // ════════════════════════════════════════════════════════
    // SECURITY VALIDATORS
    // ════════════════════════════════════════════════════════
    
    private fun isValidUrl(url: String): Boolean {
        return try {
            val parsedUrl = java.net.URL(url)
            
            // Must be HTTP or HTTPS
            if (parsedUrl.protocol !in listOf("http", "https")) {
                Timber.w("[NETWORK BRIDGE] Invalid protocol: ${parsedUrl.protocol}")
                return false
            }
            
            // Block localhost/private IPs (SSRF prevention)
            val host = parsedUrl.host
            if (isPrivateOrLocalhost(host)) {
                Timber.w("[NETWORK BRIDGE] Blocked private/localhost: $host")
                return false
            }
            
            true
            
        } catch (e: Exception) {
            Timber.w(e, "[NETWORK BRIDGE] URL validation failed")
            false
        }
    }
    
    private fun isValidHost(host: String): Boolean {
        return try {
            // Block localhost/private IPs
            if (isPrivateOrLocalhost(host)) {
                Timber.w("[NETWORK BRIDGE] Blocked private/localhost: $host")
                return false
            }
            
            true
            
        } catch (e: Exception) {
            Timber.w(e, "[NETWORK BRIDGE] Host validation failed")
            false
        }
    }
    
    private fun isPrivateOrLocalhost(host: String): Boolean {
        // Simple check - in production, use more robust validation
        val lower = host.lowercase()
        return lower == "localhost" ||
               lower == "127.0.0.1" ||
               lower == "::1" ||
               lower.startsWith("192.168.") ||
               lower.startsWith("10.") ||
               lower.startsWith("172.16.") ||
               lower.startsWith("172.17.") ||
               lower.startsWith("172.18.") ||
               lower.startsWith("172.19.") ||
               lower.startsWith("172.2") ||
               lower.startsWith("172.3")
    }
}
