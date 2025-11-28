package com.ble1st.connectias.feature.network.scanner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xbill.DNS.*
import timber.log.Timber
import java.net.InetAddress
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for DNS lookup and diagnostics operations.
 * Uses dnsjava library for DNS operations.
 */
@Singleton
class DnsLookupProvider @Inject constructor() {

    /**
     * DNS record types.
     */
    enum class RecordType(val dnsType: Int) {
        A(Type.A),
        AAAA(Type.AAAA),
        MX(Type.MX),
        TXT(Type.TXT),
        CNAME(Type.CNAME),
        NS(Type.NS),
        PTR(Type.PTR)
    }

    /**
     * Performs DNS lookup for a domain.
     * 
     * @param domain The domain name to lookup
     * @param recordType The type of DNS record to query
     * @param dnsServer Custom DNS server (null for system default)
     * @return List of DNS records as strings
     */
    suspend fun lookup(
        domain: String,
        recordType: RecordType,
        dnsServer: String? = null
    ): List<String> = withContext(Dispatchers.IO) {
        try {
            val resolver = if (dnsServer != null) {
                val server = InetAddress.getByName(dnsServer)
                SimpleResolver(server.hostAddress).apply {
                    timeout = Duration.ofSeconds(5)
                }
            } else {
                SimpleResolver().apply {
                    timeout = Duration.ofSeconds(5)
                }
            }

            val name = Name.fromString(domain)
            val lookup = Lookup(name, recordType.dnsType)
            lookup.setResolver(resolver)
            
            val records = lookup.run()
            if (records != null) {
                records.map { record ->
                    when (record) {
                        is ARecord -> record.address.hostAddress
                        is AAAARecord -> record.address.hostAddress
                        is MXRecord -> "${record.target} (priority: ${record.priority})"
                        is TXTRecord -> record.strings.joinToString(" ")
                        is CNAMERecord -> record.target.toString()
                        is NSRecord -> record.target.toString()
                        is PTRRecord -> record.target.toString()
                        else -> record.rdataToString()
                    }
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Timber.e(e, "DNS lookup failed for $domain (${recordType.name})")
            emptyList()
        }
    }

    /**
     * Performs reverse DNS lookup (PTR record).
     * 
     * @param ipAddress The IP address to reverse lookup
     * @param dnsServer Custom DNS server (null for system default)
     * @return Hostname or null if not found
     */
    suspend fun reverseLookup(
        ipAddress: String,
        dnsServer: String? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            val resolver = if (dnsServer != null) {
                val server = InetAddress.getByName(dnsServer)
                SimpleResolver(server.hostAddress).apply {
                    timeout = Duration.ofSeconds(5)
                }
            } else {
                SimpleResolver().apply {
                    timeout = Duration.ofSeconds(5)
                }
            }

            val address = Address.getByAddress(ipAddress)
            val name = ReverseMap.fromAddress(address)
            val lookup = Lookup(name, Type.PTR)
            lookup.setResolver(resolver)
            
            val records = lookup.run()
            records?.firstOrNull()?.let {
                if (it is PTRRecord) {
                    it.target.toString().removeSuffix(".")
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Reverse DNS lookup failed for $ipAddress")
            null
        }
    }

    /**
     * Tests DNS server response time.
     * 
     * @param dnsServer The DNS server IP address
     * @return Response time in milliseconds, or null if failed
     */
    suspend fun testDnsServer(dnsServer: String): Long? = withContext(Dispatchers.IO) {
        try {
            val startTime = System.currentTimeMillis()
            val resolver = SimpleResolver(dnsServer).apply {
                timeout = Duration.ofSeconds(5)
            }
            
            val lookup = Lookup("google.com", Type.A)
            lookup.setResolver(resolver)
            lookup.run()
            
            val endTime = System.currentTimeMillis()
            endTime - startTime
        } catch (e: Exception) {
            Timber.e(e, "DNS server test failed for $dnsServer")
            null
        }
    }
}

