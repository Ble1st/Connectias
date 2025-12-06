package com.ble1st.connectias.feature.security.scanner.plugins

import com.ble1st.connectias.feature.security.scanner.models.ScanConfiguration
import com.ble1st.connectias.feature.security.scanner.models.ScanType
import com.ble1st.connectias.feature.security.scanner.models.SecurityRecommendation
import com.ble1st.connectias.feature.security.scanner.models.Severity
import com.ble1st.connectias.feature.security.scanner.models.SslTlsInfo
import com.ble1st.connectias.feature.security.scanner.models.Vulnerability
import com.ble1st.connectias.feature.security.scanner.models.VulnerabilityCategory
import com.ble1st.connectias.feature.security.scanner.scanner.SslTlsAnalyzer
import com.ble1st.connectias.feature.security.scanner.utils.DomainResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.net.URL
import javax.inject.Inject

/**
 * Plugin for web security scanning including SSL/TLS, headers, and OWASP Top 10 checks.
 */
class WebSecurityPlugin @Inject constructor(
    private val httpClient: OkHttpClient,
    private val sslAnalyzer: SslTlsAnalyzer,
    private val domainResolver: DomainResolver
) : ScannerPlugin {

    override val supportedTypes = setOf(ScanType.WEB_SERVER)

    override suspend fun scan(config: ScanConfiguration): List<Vulnerability> = withContext(Dispatchers.IO) {
        val vulnerabilities = mutableListOf<Vulnerability>()
        
        try {
            val url = normalizeUrl(config.target)
            Timber.d("Starting web security scan for $url")
            
            // 1. Check HTTP vs HTTPS
            val httpVulnerabilities = checkHttpHttps(url)
            vulnerabilities.addAll(httpVulnerabilities)
            
            // 2. SSL/TLS Analysis (if HTTPS)
            if (url.startsWith("https://")) {
                val sslInfo = sslAnalyzer.analyzeSslTls(url)
                val sslVulnerabilities = checkSslTls(sslInfo, url)
                vulnerabilities.addAll(sslVulnerabilities)
                
                // Add SSL recommendations
                val sslRecommendations = sslAnalyzer.generateRecommendations(sslInfo)
                sslRecommendations.forEach { recommendation ->
                    vulnerabilities.add(
                        Vulnerability(
                            category = VulnerabilityCategory.SSL_TLS,
                            severity = recommendation.priority,
                            name = "SSL/TLS Recommendation: ${recommendation.title}",
                            description = recommendation.description,
                            impact = "Following this recommendation will improve SSL/TLS security",
                            affectedComponent = url,
                            remediation = recommendation.implementationSteps.joinToString("\n") { "• $it" }
                        )
                    )
                }
            }
            
            // 3. Security Headers Check
            val headerVulnerabilities = checkSecurityHeaders(url)
            vulnerabilities.addAll(headerVulnerabilities)
            
            // 4. Basic OWASP Top 10 Checks
            val owaspVulnerabilities = performOwaspChecks(url)
            vulnerabilities.addAll(owaspVulnerabilities)
            
            Timber.d("Web security scan completed. Found ${vulnerabilities.size} vulnerabilities")
        } catch (e: Exception) {
            Timber.e(e, "Error during web security scan")
            vulnerabilities.add(
                Vulnerability(
                    category = VulnerabilityCategory.NETWORK,
                    severity = Severity.HIGH,
                    name = "Web Security Scan Error",
                    description = "Failed to complete web security scan: ${e.message}",
                    impact = "Unable to assess web security",
                    affectedComponent = config.target,
                    remediation = "Check URL validity and network connectivity"
                )
            )
        }
        
        return@withContext vulnerabilities
    }

    /**
     * Normalizes URL to include protocol and format IPv6 addresses correctly.
     */
    private fun normalizeUrl(target: String): String {
        val url = when {
            target.startsWith("http://") || target.startsWith("https://") -> target
            else -> "https://$target"
        }
        
        // First, try to extract and format IPv6 addresses before URL parsing
        // This handles cases where IPv6 addresses cause URL parsing to fail
        val protocol = if (url.startsWith("https://")) "https://" else "http://"
        val withoutProtocol = url.removePrefix("https://").removePrefix("http://")
        
        // Try to extract host part (before first / or ?)
        val hostEnd = withoutProtocol.indexOfAny(listOf("/", "?", "#"))
        val hostPart = if (hostEnd >= 0) withoutProtocol.substring(0, hostEnd) else withoutProtocol
        val rest = if (hostEnd >= 0) withoutProtocol.substring(hostEnd) else ""
        
        // Check if hostPart is an IP address (not a domain)
        // Remove square brackets if already present
        val cleanHost = hostPart.removePrefix("[").removeSuffix("]")
        val formattedHost = if (domainResolver.isValidIpAddress(cleanHost)) {
            domainResolver.formatIpForUrl(cleanHost)
        } else {
            hostPart // Keep original if it's a domain
        }
        
        // Try to parse as URL to extract port if present
        return try {
            val fullUrl = "$protocol$formattedHost$rest"
            val urlObj = URL(fullUrl)
            // Reconstruct URL with properly formatted host
            val port = if (urlObj.port != -1 && urlObj.port != urlObj.defaultPort) ":${urlObj.port}" else ""
            val path = urlObj.path.ifEmpty { "" }
            val query = if (urlObj.query != null) "?${urlObj.query}" else ""
            val ref = if (urlObj.ref != null) "#${urlObj.ref}" else ""
            "${urlObj.protocol}://$formattedHost$port$path$query$ref"
        } catch (e: Exception) {
            // If URL parsing still fails, return the formatted URL
            "$protocol$formattedHost$rest"
        }
    }

    /**
     * Checks for HTTP vs HTTPS issues.
     */
    private suspend fun checkHttpHttps(url: String): List<Vulnerability> = withContext(Dispatchers.IO) {
        val vulnerabilities = mutableListOf<Vulnerability>()
        
        try {
            val httpUrl = url.replace("https://", "http://")
            val request = Request.Builder().url(httpUrl).build()
            val response = httpClient.newCall(request).execute()
            
            if (response.isSuccessful) {
                vulnerabilities.add(
                    Vulnerability(
                        category = VulnerabilityCategory.NETWORK,
                        severity = Severity.HIGH,
                        name = "HTTP Accessible",
                        description = "Website is accessible via unencrypted HTTP protocol",
                        impact = "Data transmitted over HTTP can be intercepted, modified, or stolen by attackers",
                        affectedComponent = httpUrl,
                        remediation = "Redirect all HTTP traffic to HTTPS. Implement HSTS header to prevent downgrade attacks."
                    )
                )
            }
        } catch (e: Exception) {
            // HTTP not accessible, which is good
        }
        
        return@withContext vulnerabilities
    }

    /**
     * Checks SSL/TLS configuration.
     */
    private fun checkSslTls(sslInfo: com.ble1st.connectias.feature.security.scanner.models.SslTlsInfo, url: String): List<Vulnerability> {
        val vulnerabilities = mutableListOf<Vulnerability>()
        
        if (!sslInfo.isSecure) {
            vulnerabilities.add(
                Vulnerability(
                    category = VulnerabilityCategory.SSL_TLS,
                    severity = Severity.HIGH,
                    name = "Insecure SSL/TLS Configuration",
                    description = "SSL/TLS configuration has security issues",
                    impact = "Weak SSL/TLS configuration may allow attackers to intercept or decrypt communications",
                    affectedComponent = url,
                    remediation = "Review SSL/TLS configuration. See recommendations for details."
                )
            )
        }
        
        sslInfo.vulnerabilities.forEach { vuln ->
            vulnerabilities.add(
                Vulnerability(
                    category = VulnerabilityCategory.SSL_TLS,
                    severity = Severity.HIGH,
                    name = "SSL/TLS Vulnerability: $vuln",
                    description = vuln,
                    impact = "This SSL/TLS vulnerability may compromise secure communications",
                    affectedComponent = url,
                    remediation = "Update SSL/TLS configuration to address this vulnerability"
                )
            )
        }
        
        if (sslInfo.weakCiphers.isNotEmpty()) {
            vulnerabilities.add(
                Vulnerability(
                    category = VulnerabilityCategory.CRYPTOGRAPHY,
                    severity = Severity.MEDIUM,
                    name = "Weak Cipher Suites",
                    description = "Weak cipher suites detected: ${sslInfo.weakCiphers.joinToString(", ")}",
                    impact = "Weak cipher suites may be vulnerable to cryptographic attacks",
                    affectedComponent = url,
                    remediation = "Disable weak cipher suites and use only strong, modern ciphers"
                )
            )
        }
        
        if (!sslInfo.certificateValid) {
            vulnerabilities.add(
                Vulnerability(
                    category = VulnerabilityCategory.SSL_TLS,
                    severity = Severity.CRITICAL,
                    name = "Invalid SSL Certificate",
                    description = "SSL certificate is invalid or expired",
                    impact = "Invalid certificates may indicate security issues or allow man-in-the-middle attacks",
                    affectedComponent = url,
                    remediation = "Renew SSL certificate and ensure it is from a trusted Certificate Authority"
                )
            )
        }
        
        return vulnerabilities
    }

    /**
     * Checks security headers.
     */
    private suspend fun checkSecurityHeaders(url: String): List<Vulnerability> = withContext(Dispatchers.IO) {
        val vulnerabilities = mutableListOf<Vulnerability>()
        
        try {
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            val headers = response.headers.toMultimap()
            
            // Check for HSTS
            if (!headers.containsKey("strict-transport-security")) {
                vulnerabilities.add(
                    Vulnerability(
                        category = VulnerabilityCategory.CONFIGURATION,
                        severity = Severity.MEDIUM,
                        name = "Missing HSTS Header",
                        description = "HTTP Strict Transport Security (HSTS) header is missing",
                        impact = "Users may be vulnerable to SSL stripping attacks, allowing attackers to downgrade the connection to HTTP",
                        affectedComponent = url,
                        remediation = "Add the 'Strict-Transport-Security' header with a long max-age (e.g., 'max-age=31536000; includeSubDomains')"
                    )
                )
            }
            
            // Check for CSP
            if (!headers.containsKey("content-security-policy")) {
                vulnerabilities.add(
                    Vulnerability(
                        category = VulnerabilityCategory.CONFIGURATION,
                        severity = Severity.MEDIUM,
                        name = "Missing Content-Security-Policy",
                        description = "Content-Security-Policy (CSP) header is not configured",
                        impact = "Increases the risk of Cross-Site Scripting (XSS) and data injection attacks",
                        affectedComponent = url,
                        remediation = "Implement a strict Content-Security-Policy to restrict where resources can be loaded from"
                    )
                )
            }
            
            // Check for X-Frame-Options
            if (!headers.containsKey("x-frame-options")) {
                vulnerabilities.add(
                    Vulnerability(
                        category = VulnerabilityCategory.CONFIGURATION,
                        severity = Severity.LOW,
                        name = "Missing X-Frame-Options",
                        description = "X-Frame-Options header is missing",
                        impact = "The site can be embedded in a frame, making it vulnerable to Clickjacking attacks",
                        affectedComponent = url,
                        remediation = "Set X-Frame-Options to 'DENY' or 'SAMEORIGIN'"
                    )
                )
            }
            
            // Check for X-Content-Type-Options
            if (!headers.containsKey("x-content-type-options")) {
                vulnerabilities.add(
                    Vulnerability(
                        category = VulnerabilityCategory.CONFIGURATION,
                        severity = Severity.LOW,
                        name = "Missing X-Content-Type-Options",
                        description = "X-Content-Type-Options header is missing",
                        impact = "Browser may perform MIME type sniffing, which can lead to security issues",
                        affectedComponent = url,
                        remediation = "Set X-Content-Type-Options to 'nosniff'"
                    )
                )
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error checking security headers")
        }
        
        return@withContext vulnerabilities
    }

    /**
     * Performs basic OWASP Top 10 checks.
     */
    private suspend fun performOwaspChecks(url: String): List<Vulnerability> = withContext(Dispatchers.IO) {
        val vulnerabilities = mutableListOf<Vulnerability>()
        
        try {
            // Check for common sensitive files
            val sensitivePaths = listOf(
                "/.env",
                "/.git/config",
                "/.git/HEAD",
                "/config.php",
                "/.htaccess",
                "/web.config",
                "/.DS_Store",
                "/robots.txt"
            )
            
            sensitivePaths.forEach { path ->
                try {
                    val urlObj = URL(url)
                    val host = urlObj.host
                    val formattedHost = if (domainResolver.isValidIpAddress(host)) {
                        domainResolver.formatIpForUrl(host)
                    } else {
                        host
                    }
                    val port = if (urlObj.port != -1 && urlObj.port != urlObj.defaultPort) ":${urlObj.port}" else ""
                    val testUrlString = "${urlObj.protocol}://$formattedHost$port$path"
                    val request = Request.Builder().url(testUrlString).build()
                    val response = httpClient.newCall(request).execute()
                    
                    if (response.isSuccessful && response.body != null) {
                        vulnerabilities.add(
                            Vulnerability(
                                category = VulnerabilityCategory.DATA_EXPOSURE,
                                severity = Severity.MEDIUM,
                                name = "Sensitive File Exposed: $path",
                                description = "Sensitive file or directory is accessible: $path",
                                impact = "Exposed sensitive files may contain configuration, credentials, or other sensitive information",
                                affectedComponent = "$url$path",
                                remediation = "Remove sensitive files from web root or restrict access via web server configuration"
                            )
                        )
                    }
                } catch (e: Exception) {
                    // File not accessible, which is good
                }
            }
            
            // Check for server information disclosure
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            val serverHeader = response.header("Server")
            
            if (serverHeader != null && serverHeader.isNotBlank()) {
                vulnerabilities.add(
                    Vulnerability(
                        category = VulnerabilityCategory.CONFIGURATION,
                        severity = Severity.LOW,
                        name = "Server Information Disclosure",
                        description = "Server header reveals server software and version: $serverHeader",
                        impact = "Revealing server information may help attackers identify known vulnerabilities",
                        affectedComponent = url,
                        remediation = "Remove or modify Server header to hide server information"
                    )
                )
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error performing OWASP checks")
        }
        
        return@withContext vulnerabilities
    }
}

