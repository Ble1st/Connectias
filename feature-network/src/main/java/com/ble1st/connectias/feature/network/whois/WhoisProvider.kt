package com.ble1st.connectias.feature.network.whois

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for WHOIS lookup functionality.
 */
@Singleton
class WhoisProvider @Inject constructor() {

    private val whoisServers = mapOf(
        "com" to "whois.verisign-grs.com",
        "net" to "whois.verisign-grs.com",
        "org" to "whois.pir.org",
        "info" to "whois.afilias.net",
        "io" to "whois.nic.io",
        "co" to "whois.nic.co",
        "de" to "whois.denic.de",
        "uk" to "whois.nic.uk",
        "fr" to "whois.nic.fr",
        "eu" to "whois.eu",
        "ru" to "whois.tcinet.ru",
        "cn" to "whois.cnnic.cn",
        "jp" to "whois.jprs.jp",
        "au" to "whois.auda.org.au",
        "ca" to "whois.cira.ca",
        "nl" to "whois.domain-registry.nl",
        "be" to "whois.dns.be",
        "at" to "whois.nic.at",
        "ch" to "whois.nic.ch",
        "it" to "whois.nic.it",
        "es" to "whois.nic.es",
        "pl" to "whois.dns.pl",
        "br" to "whois.registro.br",
        "in" to "whois.inregistry.net",
        "us" to "whois.nic.us",
        "gov" to "whois.dotgov.gov",
        "edu" to "whois.educause.edu"
    )

    private val ipWhoisServers = listOf(
        "whois.arin.net",
        "whois.ripe.net",
        "whois.apnic.net",
        "whois.lacnic.net",
        "whois.afrinic.net"
    )

    /**
     * Performs a WHOIS lookup for a domain.
     */
    suspend fun lookupDomain(domain: String): WhoisResult = withContext(Dispatchers.IO) {
        try {
            val tld = domain.substringAfterLast(".")
            val server = whoisServers[tld.lowercase()] ?: "whois.iana.org"

            val rawData = queryWhoisServer(server, domain)
            parseWhoisResponse(domain, rawData)
        } catch (e: Exception) {
            Timber.e(e, "WHOIS lookup failed for $domain")
            WhoisResult(
                query = domain,
                success = false,
                error = e.message
            )
        }
    }

    /**
     * Performs a WHOIS lookup for an IP address.
     */
    suspend fun lookupIp(ip: String): WhoisResult = withContext(Dispatchers.IO) {
        try {
            // Try each RIR server
            for (server in ipWhoisServers) {
                val rawData = queryWhoisServer(server, ip)
                if (rawData.isNotEmpty() && !rawData.contains("No match found")) {
                    return@withContext parseIpWhoisResponse(ip, rawData)
                }
            }
            WhoisResult(
                query = ip,
                success = false,
                error = "No WHOIS data found"
            )
        } catch (e: Exception) {
            Timber.e(e, "WHOIS lookup failed for $ip")
            WhoisResult(
                query = ip,
                success = false,
                error = e.message
            )
        }
    }

    /**
     * Queries a WHOIS server.
     */
    private fun queryWhoisServer(server: String, query: String): String {
        return Socket(server, 43).use { socket ->
            socket.soTimeout = 10000
            val writer = PrintWriter(socket.getOutputStream(), true)
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

            writer.println(query)
            reader.readText()
        }
    }

    /**
     * Parses WHOIS response for a domain.
     */
    private fun parseWhoisResponse(domain: String, rawData: String): WhoisResult {
        val lines = rawData.lines()
        val data = mutableMapOf<String, String>()

        for (line in lines) {
            val parts = line.split(":", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim()
                val value = parts[1].trim()
                if (key.isNotEmpty() && value.isNotEmpty()) {
                    data[key] = value
                }
            }
        }

        return WhoisResult(
            query = domain,
            success = true,
            rawData = rawData,
            registrar = data["Registrar"] ?: data["registrar"],
            registrant = data["Registrant Name"] ?: data["Registrant"],
            registrantOrg = data["Registrant Organization"],
            creationDate = data["Creation Date"] ?: data["Created"] ?: data["created"],
            expirationDate = data["Registry Expiry Date"] ?: data["Expiry Date"] ?: data["paid-till"],
            updatedDate = data["Updated Date"] ?: data["Last Updated"],
            nameServers = extractNameServers(rawData),
            status = extractStatus(rawData),
            dnssec = data["DNSSEC"]
        )
    }

    /**
     * Parses WHOIS response for an IP.
     */
    private fun parseIpWhoisResponse(ip: String, rawData: String): WhoisResult {
        val lines = rawData.lines()
        val data = mutableMapOf<String, String>()

        for (line in lines) {
            val parts = line.split(":", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim()
                val value = parts[1].trim()
                if (key.isNotEmpty() && value.isNotEmpty()) {
                    data[key.lowercase()] = value
                }
            }
        }

        return WhoisResult(
            query = ip,
            success = true,
            rawData = rawData,
            ipRange = data["inetnum"] ?: data["netrange"],
            netName = data["netname"],
            orgName = data["org-name"] ?: data["organization"],
            country = data["country"],
            abuseContact = data["abuse-mailbox"] ?: data["orgabuseemail"]
        )
    }

    private fun extractNameServers(rawData: String): List<String> {
        val nameServers = mutableListOf<String>()
        val pattern = """Name Server:\s*(\S+)""".toRegex(RegexOption.IGNORE_CASE)
        pattern.findAll(rawData).forEach { match ->
            nameServers.add(match.groupValues[1].lowercase())
        }
        return nameServers.distinct()
    }

    private fun extractStatus(rawData: String): List<String> {
        val statuses = mutableListOf<String>()
        val pattern = """Domain Status:\s*(\S+)""".toRegex(RegexOption.IGNORE_CASE)
        pattern.findAll(rawData).forEach { match ->
            statuses.add(match.groupValues[1])
        }
        return statuses.distinct()
    }
}

/**
 * Result of a WHOIS lookup.
 */
@Serializable
data class WhoisResult(
    val query: String,
    val success: Boolean,
    val rawData: String? = null,
    val error: String? = null,
    // Domain fields
    val registrar: String? = null,
    val registrant: String? = null,
    val registrantOrg: String? = null,
    val creationDate: String? = null,
    val expirationDate: String? = null,
    val updatedDate: String? = null,
    val nameServers: List<String> = emptyList(),
    val status: List<String> = emptyList(),
    val dnssec: String? = null,
    // IP fields
    val ipRange: String? = null,
    val netName: String? = null,
    val orgName: String? = null,
    val country: String? = null,
    val abuseContact: String? = null
)
