package com.ble1st.connectias.feature.network.headers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for HTTP header analysis functionality.
 */
@Singleton
class HttpHeaderAnalyzerProvider @Inject constructor() {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    private val securityHeaders = mapOf(
        "Strict-Transport-Security" to "HSTS - Enforces HTTPS connections",
        "Content-Security-Policy" to "CSP - Controls allowed content sources",
        "X-Content-Type-Options" to "Prevents MIME type sniffing",
        "X-Frame-Options" to "Prevents clickjacking attacks",
        "X-XSS-Protection" to "XSS filtering (deprecated but still used)",
        "Referrer-Policy" to "Controls referrer information",
        "Permissions-Policy" to "Controls browser features",
        "Cross-Origin-Opener-Policy" to "Isolates browsing context",
        "Cross-Origin-Resource-Policy" to "Controls cross-origin access",
        "Cross-Origin-Embedder-Policy" to "Controls embedding permissions"
    )

    /**
     * Analyzes HTTP headers for a URL.
     */
    suspend fun analyzeHeaders(url: String): HeaderAnalysisResult = withContext(Dispatchers.IO) {
        try {
            val normalizedUrl = if (!url.startsWith("http")) "https://$url" else url

            val request = Request.Builder()
                .url(normalizedUrl)
                .head()
                .build()

            val response = httpClient.newCall(request).execute()

            val headers = response.headers.toMultimap().mapValues { it.value.joinToString("; ") }
            val securityAnalysis = analyzeSecurityHeaders(headers)
            val cachingAnalysis = analyzeCachingHeaders(headers)
            val serverInfo = extractServerInfo(headers)

            HeaderAnalysisResult(
                url = normalizedUrl,
                success = true,
                statusCode = response.code,
                statusMessage = response.message,
                headers = headers,
                securityScore = calculateSecurityScore(securityAnalysis),
                securityAnalysis = securityAnalysis,
                cachingAnalysis = cachingAnalysis,
                serverInfo = serverInfo,
                responseTime = response.receivedResponseAtMillis - response.sentRequestAtMillis
            )
        } catch (e: Exception) {
            Timber.e(e, "Error analyzing headers for $url")
            HeaderAnalysisResult(
                url = url,
                success = false,
                error = e.message
            )
        }
    }

    /**
     * Analyzes security headers.
     */
    private fun analyzeSecurityHeaders(headers: Map<String, String>): List<SecurityHeaderCheck> {
        val checks = mutableListOf<SecurityHeaderCheck>()

        for ((header, description) in securityHeaders) {
            val value = headers.entries.find { 
                it.key.equals(header, ignoreCase = true) 
            }?.value

            val status = when {
                value == null -> HeaderStatus.MISSING
                isHeaderWellConfigured(header, value) -> HeaderStatus.PRESENT
                else -> HeaderStatus.WEAK
            }

            checks.add(
                SecurityHeaderCheck(
                    header = header,
                    value = value,
                    status = status,
                    description = description,
                    recommendation = getRecommendation(header, status, value)
                )
            )
        }

        return checks
    }

    private fun isHeaderWellConfigured(header: String, value: String): Boolean {
        return when (header) {
            "Strict-Transport-Security" -> 
                value.contains("max-age") && value.contains("includeSubDomains")
            "X-Content-Type-Options" -> 
                value.equals("nosniff", ignoreCase = true)
            "X-Frame-Options" -> 
                value.equals("DENY", ignoreCase = true) || 
                value.equals("SAMEORIGIN", ignoreCase = true)
            "Content-Security-Policy" -> 
                !value.contains("unsafe-inline") && !value.contains("unsafe-eval")
            else -> true
        }
    }

    private fun getRecommendation(header: String, status: HeaderStatus, value: String?): String {
        return when {
            status == HeaderStatus.PRESENT -> "Header is properly configured"
            status == HeaderStatus.MISSING -> "Add $header header for improved security"
            header == "Strict-Transport-Security" && value != null ->
                "Add 'includeSubDomains' and increase max-age to at least 31536000"
            header == "Content-Security-Policy" && value?.contains("unsafe") == true ->
                "Remove 'unsafe-inline' and 'unsafe-eval' directives"
            else -> "Review and strengthen the header configuration"
        }
    }

    /**
     * Analyzes caching headers.
     */
    private fun analyzeCachingHeaders(headers: Map<String, String>): CachingAnalysis {
        val cacheControl = headers.entries.find { 
            it.key.equals("Cache-Control", ignoreCase = true) 
        }?.value
        val expires = headers.entries.find { 
            it.key.equals("Expires", ignoreCase = true) 
        }?.value
        val etag = headers.entries.find { 
            it.key.equals("ETag", ignoreCase = true) 
        }?.value
        val lastModified = headers.entries.find { 
            it.key.equals("Last-Modified", ignoreCase = true) 
        }?.value

        val isCacheable = cacheControl?.let {
            !it.contains("no-store") && !it.contains("private")
        } ?: true

        val maxAge = cacheControl?.let {
            Regex("""max-age=(\d+)""").find(it)?.groupValues?.get(1)?.toIntOrNull()
        }

        return CachingAnalysis(
            cacheControl = cacheControl,
            expires = expires,
            etag = etag,
            lastModified = lastModified,
            isCacheable = isCacheable,
            maxAgeSeconds = maxAge,
            hasValidation = etag != null || lastModified != null
        )
    }

    /**
     * Extracts server information.
     */
    private fun extractServerInfo(headers: Map<String, String>): ServerInfo {
        return ServerInfo(
            server = headers.entries.find { it.key.equals("Server", ignoreCase = true) }?.value,
            poweredBy = headers.entries.find { it.key.equals("X-Powered-By", ignoreCase = true) }?.value,
            aspNetVersion = headers.entries.find { it.key.equals("X-AspNet-Version", ignoreCase = true) }?.value,
            contentType = headers.entries.find { it.key.equals("Content-Type", ignoreCase = true) }?.value,
            via = headers.entries.find { it.key.equals("Via", ignoreCase = true) }?.value
        )
    }

    private fun calculateSecurityScore(checks: List<SecurityHeaderCheck>): Int {
        val totalHeaders = checks.size
        val presentScore = checks.count { it.status == HeaderStatus.PRESENT } * 10
        val weakScore = checks.count { it.status == HeaderStatus.WEAK } * 5
        return ((presentScore + weakScore) * 100) / (totalHeaders * 10)
    }

    /**
     * Compares headers between two URLs.
     */
    suspend fun compareHeaders(url1: String, url2: String): HeaderComparison = withContext(Dispatchers.IO) {
        val result1 = analyzeHeaders(url1)
        val result2 = analyzeHeaders(url2)

        val allHeaders = (result1.headers.keys + result2.headers.keys).distinct()
        val differences = mutableListOf<HeaderDifference>()

        for (header in allHeaders) {
            val value1 = result1.headers[header]
            val value2 = result2.headers[header]

            if (value1 != value2) {
                differences.add(
                    HeaderDifference(
                        header = header,
                        value1 = value1,
                        value2 = value2
                    )
                )
            }
        }

        HeaderComparison(
            url1 = url1,
            url2 = url2,
            result1 = result1,
            result2 = result2,
            differences = differences
        )
    }
}

/**
 * Result of header analysis.
 */
@Serializable
data class HeaderAnalysisResult(
    val url: String,
    val success: Boolean,
    val statusCode: Int = 0,
    val statusMessage: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val securityScore: Int = 0,
    val securityAnalysis: List<SecurityHeaderCheck> = emptyList(),
    val cachingAnalysis: CachingAnalysis? = null,
    val serverInfo: ServerInfo? = null,
    val responseTime: Long = 0,
    val error: String? = null
)

/**
 * Security header check result.
 */
@Serializable
data class SecurityHeaderCheck(
    val header: String,
    val value: String?,
    val status: HeaderStatus,
    val description: String,
    val recommendation: String
)

/**
 * Header status.
 */
enum class HeaderStatus {
    PRESENT,
    WEAK,
    MISSING
}

/**
 * Caching header analysis.
 */
@Serializable
data class CachingAnalysis(
    val cacheControl: String?,
    val expires: String?,
    val etag: String?,
    val lastModified: String?,
    val isCacheable: Boolean,
    val maxAgeSeconds: Int?,
    val hasValidation: Boolean
)

/**
 * Server information.
 */
@Serializable
data class ServerInfo(
    val server: String?,
    val poweredBy: String?,
    val aspNetVersion: String?,
    val contentType: String?,
    val via: String?
)

/**
 * Header comparison result.
 */
@Serializable
data class HeaderComparison(
    val url1: String,
    val url2: String,
    val result1: HeaderAnalysisResult,
    val result2: HeaderAnalysisResult,
    val differences: List<HeaderDifference>
)

/**
 * Header difference.
 */
@Serializable
data class HeaderDifference(
    val header: String,
    val value1: String?,
    val value2: String?
)
