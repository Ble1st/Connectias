package com.ble1st.connectias.feature.utilities.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for API testing operations.
 * Uses OkHttp for HTTP requests.
 */
@Singleton
class ApiTesterProvider @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
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
            val requestBuilder = Request.Builder().url(url)

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

