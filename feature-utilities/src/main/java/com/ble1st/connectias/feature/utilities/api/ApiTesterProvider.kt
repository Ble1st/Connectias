package com.ble1st.connectias.feature.utilities.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CertificatePinner
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Configuration for API Tester.
 * Allows optional SSL pinning for enhanced security.
 */
data class ApiTesterConfig(
    val enableSslPinning: Boolean = false,
    val pinnedDomains: Map<String, List<String>> = emptyMap()
)

/**
 * Provider for API testing operations.
 * Uses OkHttp for HTTP requests.
 * Supports optional SSL certificate pinning for enhanced security.
 */
@Singleton
class ApiTesterProvider @Inject constructor(
    private val config: ApiTesterConfig
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .apply {
            // Configure SSL pinning if enabled
            if (config.enableSslPinning && config.pinnedDomains.isNotEmpty()) {
                val pinnerBuilder = CertificatePinner.Builder()
                config.pinnedDomains.forEach { (host, pins) ->
                    pins.forEach { pin ->
                        pinnerBuilder.add(host, pin)
                    }
                }
                certificatePinner(pinnerBuilder.build())
            }
        }
        .build()

    /**
     * HTTP methods supported.
     */
    enum class HttpMethod {
        GET,
        POST,
        PUT,
        DELETE,
        PATCH
    }

    /**
     * Executes an HTTP request.
     * 
     * @param url The request URL
     * @param method The HTTP method
     * @param headers Map of headers (key-value pairs)
     * @param body The request body (for POST, PUT, PATCH)
     * @return ApiResponse with status, headers, and body
     */
    suspend fun executeRequest(
        url: String,
        method: HttpMethod,
        headers: Map<String, String> = emptyMap(),
        body: String? = null
    ): ApiResponse = withContext(Dispatchers.IO) {
        try {
            // Validate URL to prevent SSRF attacks
            val validatedUrl = validateUrl(url) ?: return@withContext ApiResponse(
                statusCode = 0,
                statusMessage = "Invalid or blocked URL",
                headers = emptyMap(),
                body = "",
                duration = 0,
                isSuccess = false,
                error = "URL validation failed"
            )
            
            val requestBuilder = Request.Builder().url(validatedUrl)

            // Add headers
            headers.forEach { (key, value) ->
                requestBuilder.addHeader(key, value)
            }

            // Add body for methods that support it
            if (body != null && method in listOf(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH)) {
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBody = body.toRequestBody(mediaType)
                when (method) {
                    HttpMethod.POST -> requestBuilder.post(requestBody)
                    HttpMethod.PUT -> requestBuilder.put(requestBody)
                    HttpMethod.PATCH -> requestBuilder.patch(requestBody)
                    else -> requestBuilder
                }
            } else {
                when (method) {
                    HttpMethod.GET -> requestBuilder.get()
                    HttpMethod.DELETE -> requestBuilder.delete()
                    else -> requestBuilder
                }
            }

            val request = requestBuilder.build()
            val startTime = System.currentTimeMillis()
            val response = client.newCall(request).execute()
            val duration = System.currentTimeMillis() - startTime

            val responseBody = response.body?.string() ?: ""
            val responseHeaders = response.headers.toMultimap()

            ApiResponse(
                statusCode = response.code,
                statusMessage = response.message,
                headers = responseHeaders,
                body = responseBody,
                duration = duration,
                isSuccess = response.isSuccessful
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to execute HTTP request")
            ApiResponse(
                statusCode = 0,
                statusMessage = e.message ?: "Unknown error",
                headers = emptyMap(),
                body = "",
                duration = 0,
                isSuccess = false,
                error = e.message
            )
        }
    }

    /**
     * Validates URL to prevent SSRF (Server-Side Request Forgery) attacks.
     * Blocks private IPs, localhost, and non-HTTP(S) protocols.
     * 
     * @param urlString The URL to validate
     * @return Validated URL string or null if validation fails
     */
    private fun validateUrl(urlString: String): String? {
        return try {
            val url = URL(urlString)
            
            // Only allow HTTP/HTTPS
            if (url.protocol !in listOf("http", "https")) {
                Timber.w("Blocked non-HTTP(S) protocol: ${url.protocol}")
                return null
            }
            
            // Block private IP ranges (SSRF protection)
            val host = url.host.lowercase()
            
            // Block localhost hostname before DNS resolution
            if (host == "localhost" || host == "0.0.0.0" || host == "::1") {
                Timber.w("Blocked localhost hostname: $host")
                return null
            }
            
            // Resolve host to IP address for proper validation
            // This prevents SSRF bypass via hostname manipulation (e.g., "127.evil.com")
            val inetAddress = try {
                java.net.InetAddress.getByName(host)
            } catch (e: Exception) {
                Timber.w("Failed to resolve host: $host")
                return null
            }
            
            val resolvedIp = inetAddress.hostAddress

            // Block localhost, link-local, site-local, and private addresses
            if (inetAddress.isLoopbackAddress || 
                inetAddress.isAnyLocalAddress || 
                inetAddress.isLinkLocalAddress || 
                inetAddress.isSiteLocalAddress) {
                Timber.w("Blocked internal IP: $host (resolved to $resolvedIp)")
                return null
            }
            
            // Additional check: Block private IP ranges by parsing the resolved IP
            if (resolvedIp != null && isPrivateIp(resolvedIp)) {
                Timber.w("Blocked private IP: $host (resolved to $resolvedIp)")
                return null
            }
            
            urlString
        } catch (e: Exception) {
            Timber.e(e, "Invalid URL format: $urlString")
            null
        }
    }

    /**
     * Checks if a host is a private IP address.
     * 
     * @param host The host to check
     * @return true if the host is a private IP address
     */
    private fun isPrivateIp(host: String): Boolean {
        // Check for private IP ranges: 10.x.x.x, 172.16-31.x.x, 192.168.x.x
        val privateIpPattern = Regex(
            "^(10\\.|172\\.(1[6-9]|2[0-9]|3[01])\\.|192\\.168\\.)"
        )
        return privateIpPattern.containsMatchIn(host)
    }
}

/**
 * Response from API request.
 */
data class ApiResponse(
    val statusCode: Int,
    val statusMessage: String,
    val headers: Map<String, List<String>>,
    val body: String,
    val duration: Long,
    val isSuccess: Boolean,
    val error: String? = null
)

