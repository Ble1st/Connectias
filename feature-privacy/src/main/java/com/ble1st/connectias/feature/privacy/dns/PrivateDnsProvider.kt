package com.ble1st.connectias.feature.privacy.dns

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for Private DNS (DoH/DoT) functionality.
 */
@Singleton
class PrivateDnsProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val _currentProvider = MutableStateFlow<DnsProvider?>(null)
    val currentProvider: StateFlow<DnsProvider?> = _currentProvider.asStateFlow()

    /**
     * Available DoH/DoT providers.
     */
    val availableProviders = listOf(
        DnsProvider(
            name = "Cloudflare",
            dohUrl = "https://cloudflare-dns.com/dns-query",
            dotHost = "1dot1dot1dot1.cloudflare-dns.com",
            primaryIp = "1.1.1.1",
            secondaryIp = "1.0.0.1",
            privacyFocused = true,
            noLogging = true
        ),
        DnsProvider(
            name = "Google",
            dohUrl = "https://dns.google/dns-query",
            dotHost = "dns.google",
            primaryIp = "8.8.8.8",
            secondaryIp = "8.8.4.4",
            privacyFocused = false,
            noLogging = false
        ),
        DnsProvider(
            name = "Quad9",
            dohUrl = "https://dns.quad9.net/dns-query",
            dotHost = "dns.quad9.net",
            primaryIp = "9.9.9.9",
            secondaryIp = "149.112.112.112",
            privacyFocused = true,
            noLogging = true,
            malwareBlocking = true
        ),
        DnsProvider(
            name = "NextDNS",
            dohUrl = "https://dns.nextdns.io",
            dotHost = "dns.nextdns.io",
            primaryIp = "45.90.28.0",
            secondaryIp = "45.90.30.0",
            privacyFocused = true,
            noLogging = true,
            adBlocking = true
        ),
        DnsProvider(
            name = "AdGuard",
            dohUrl = "https://dns.adguard.com/dns-query",
            dotHost = "dns.adguard.com",
            primaryIp = "94.140.14.14",
            secondaryIp = "94.140.15.15",
            privacyFocused = true,
            noLogging = true,
            adBlocking = true
        ),
        DnsProvider(
            name = "Mullvad",
            dohUrl = "https://doh.mullvad.net/dns-query",
            dotHost = "doh.mullvad.net",
            primaryIp = "194.242.2.2",
            secondaryIp = null,
            privacyFocused = true,
            noLogging = true,
            adBlocking = true
        )
    )

    /**
     * Resolves a domain using DNS-over-HTTPS.
     */
    suspend fun resolveDoH(
        domain: String,
        provider: DnsProvider = availableProviders.first(),
        recordType: DnsRecordType = DnsRecordType.A
    ): DnsResult = withContext(Dispatchers.IO) {
        try {
            val url = provider.dohUrl.toHttpUrl().newBuilder()
                .addQueryParameter("name", domain)
                .addQueryParameter("type", recordType.name)
                .build()

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/dns-json")
                .get()
                .build()

            val startTime = System.currentTimeMillis()
            val response = httpClient.newCall(request).execute()
            val endTime = System.currentTimeMillis()

            val body = response.body?.string() ?: throw Exception("Empty response")

            // Parse JSON response
            val answers = parseDnsJsonResponse(body, recordType)

            DnsResult(
                domain = domain,
                success = true,
                provider = provider.name,
                method = "DoH",
                recordType = recordType,
                answers = answers,
                queryTime = (endTime - startTime).toInt()
            )
        } catch (e: Exception) {
            Timber.e(e, "DoH resolution failed for $domain")
            DnsResult(
                domain = domain,
                success = false,
                provider = provider.name,
                method = "DoH",
                recordType = recordType,
                error = e.message
            )
        }
    }

    /**
     * Resolves using traditional DNS (for comparison).
     */
    suspend fun resolveTraditional(
        domain: String,
        dnsServer: String = "8.8.8.8"
    ): DnsResult = withContext(Dispatchers.IO) {
        try {
            val startTime = System.currentTimeMillis()

            // Build DNS query
            val query = buildDnsQuery(domain, DnsRecordType.A)

            val socket = DatagramSocket()
            socket.soTimeout = 5000

            val serverAddress = InetAddress.getByName(dnsServer)
            val packet = DatagramPacket(query, query.size, serverAddress, 53)
            socket.send(packet)

            val buffer = ByteArray(512)
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)
            socket.close()

            val endTime = System.currentTimeMillis()

            val answers = parseDnsResponse(buffer, response.length)

            DnsResult(
                domain = domain,
                success = true,
                provider = dnsServer,
                method = "UDP",
                recordType = DnsRecordType.A,
                answers = answers,
                queryTime = (endTime - startTime).toInt()
            )
        } catch (e: Exception) {
            Timber.e(e, "Traditional DNS resolution failed for $domain")
            DnsResult(
                domain = domain,
                success = false,
                method = "UDP",
                recordType = DnsRecordType.A,
                error = e.message
            )
        }
    }

    /**
     * Tests a DNS provider.
     */
    suspend fun testProvider(provider: DnsProvider): ProviderTestResult = withContext(Dispatchers.IO) {
        val testDomains = listOf("google.com", "cloudflare.com", "example.com")
        val results = mutableListOf<DnsResult>()
        var totalTime = 0L

        for (domain in testDomains) {
            val result = resolveDoH(domain, provider)
            results.add(result)
            totalTime += result.queryTime
        }

        val successRate = results.count { it.success } * 100 / results.size
        val avgTime = if (results.isNotEmpty()) totalTime / results.size else 0L

        ProviderTestResult(
            provider = provider,
            successRate = successRate,
            averageResponseTime = avgTime.toInt(),
            results = results
        )
    }

    private fun buildDnsQuery(domain: String, recordType: DnsRecordType): ByteArray {
        val output = ByteArrayOutputStream()

        // Transaction ID
        output.write(0x00)
        output.write(0x01)

        // Flags (standard query)
        output.write(0x01)
        output.write(0x00)

        // Questions = 1
        output.write(0x00)
        output.write(0x01)

        // Answer RRs, Authority RRs, Additional RRs = 0
        repeat(6) { output.write(0x00) }

        // Domain name
        for (label in domain.split(".")) {
            output.write(label.length)
            output.write(label.toByteArray())
        }
        output.write(0x00) // Null terminator

        // Type (A = 1)
        output.write(0x00)
        output.write(recordType.value)

        // Class (IN = 1)
        output.write(0x00)
        output.write(0x01)

        return output.toByteArray()
    }

    private fun parseDnsJsonResponse(json: String, recordType: DnsRecordType): List<DnsAnswer> {
        // Simple JSON parsing for DNS answers
        val answers = mutableListOf<DnsAnswer>()
        val pattern = """"data"\s*:\s*"([^"]+)"""".toRegex()

        pattern.findAll(json).forEach { match ->
            answers.add(
                DnsAnswer(
                    type = recordType,
                    value = match.groupValues[1],
                    ttl = 300 // Default TTL
                )
            )
        }

        return answers
    }

    private fun parseDnsResponse(buffer: ByteArray, length: Int): List<DnsAnswer> {
        // Simplified DNS response parsing
        val answers = mutableListOf<DnsAnswer>()

        if (length < 12) return answers

        val answerCount = ((buffer[6].toInt() and 0xFF) shl 8) or (buffer[7].toInt() and 0xFF)

        // Skip header and question section (simplified)
        var offset = 12
        while (offset < length && buffer[offset] != 0.toByte()) {
            offset++
        }
        offset += 5 // Skip null terminator, type, and class

        // Parse answers (simplified)
        repeat(answerCount) {
            if (offset + 12 > length) return@repeat

            // Skip name, type, class
            offset += 10

            val rdLength = ((buffer[offset].toInt() and 0xFF) shl 8) or (buffer[offset + 1].toInt() and 0xFF)
            offset += 2

            if (rdLength == 4 && offset + 4 <= length) {
                val ip = "${buffer[offset].toInt() and 0xFF}." +
                        "${buffer[offset + 1].toInt() and 0xFF}." +
                        "${buffer[offset + 2].toInt() and 0xFF}." +
                        "${buffer[offset + 3].toInt() and 0xFF}"
                answers.add(DnsAnswer(type = DnsRecordType.A, value = ip, ttl = 300))
            }
            offset += rdLength
        }

        return answers
    }
}

/**
 * DNS provider configuration.
 */
@Serializable
data class DnsProvider(
    val name: String,
    val dohUrl: String,
    val dotHost: String,
    val primaryIp: String,
    val secondaryIp: String?,
    val privacyFocused: Boolean = false,
    val noLogging: Boolean = false,
    val malwareBlocking: Boolean = false,
    val adBlocking: Boolean = false
)

/**
 * DNS record types.
 */
enum class DnsRecordType(val value: Int) {
    A(1),
    AAAA(28),
    CNAME(5),
    MX(15),
    TXT(16),
    NS(2),
    SOA(6),
    PTR(12)
}

/**
 * DNS resolution result.
 */
@Serializable
data class DnsResult(
    val domain: String,
    val success: Boolean,
    val provider: String? = null,
    val method: String,
    val recordType: DnsRecordType,
    val answers: List<DnsAnswer> = emptyList(),
    val queryTime: Int = 0,
    val error: String? = null
)

/**
 * DNS answer record.
 */
@Serializable
data class DnsAnswer(
    val type: DnsRecordType,
    val value: String,
    val ttl: Int
)

/**
 * Provider test result.
 */
@Serializable
data class ProviderTestResult(
    val provider: DnsProvider,
    val successRate: Int,
    val averageResponseTime: Int,
    val results: List<DnsResult>
)
