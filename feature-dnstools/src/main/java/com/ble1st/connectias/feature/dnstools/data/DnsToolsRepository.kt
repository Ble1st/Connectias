package com.ble1st.connectias.feature.dnstools.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.apache.commons.net.whois.WhoisClient
import org.xbill.DNS.Lookup
import org.xbill.DNS.TextParseException
import org.xbill.DNS.Type
import timber.log.Timber
import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.math.max
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DnsToolsRepository @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val dnsHistoryDao: DnsHistoryDao
) {

    val history: Flow<List<DnsHistoryEntity>> = dnsHistoryDao.getAllHistory()

    suspend fun clearHistory() = dnsHistoryDao.clearAll()
    suspend fun deleteHistoryItem(item: DnsHistoryEntity) = dnsHistoryDao.delete(item)

    suspend fun resolveDns(domain: String, type: Int = Type.A): DnsQueryResult = withContext(Dispatchers.IO) {
        val result = try {
            val lookup = Lookup(domain, type)
            val records = lookup.run()?.toList().orEmpty()
            val mapped = records.map { it.rdataToString() }
            DnsQueryResult(domain = domain, type = Type.string(type), records = mapped)
        } catch (t: TextParseException) {
            Timber.e(t, "Invalid domain: %s", domain)
            DnsQueryResult(domain = domain, type = Type.string(type), records = emptyList(), error = "Invalid domain")
        } catch (t: Exception) {
            Timber.e(t, "DNS lookup failed for %s", domain)
            DnsQueryResult(domain = domain, type = Type.string(type), records = emptyList(), error = t.message)
        }
        
        // Save to history
        saveHistory(
            ToolType.DNS, 
            "$domain (${Type.string(type)})", 
            if (result.error != null) "Error: ${result.error}" else "${result.records.size} records found",
            result.records.joinToString("\n")
        )
        
        return@withContext result
    }

    suspend fun fetchDmarc(domain: String): DnsQueryResult = resolveDns("_dmarc.$domain", Type.TXT)

    suspend fun fetchWhois(domain: String): WhoisResult = withContext(Dispatchers.IO) {
        val result = try {
            val client = WhoisClient()
            client.connect(WhoisClient.DEFAULT_HOST)
            val data = client.query(domain)
            client.disconnect()
            WhoisResult(domain = domain, raw = data ?: "", error = null)
        } catch (t: Exception) {
            Timber.e(t, "WHOIS lookup failed for %s", domain)
            WhoisResult(domain = domain, raw = "", error = t.message)
        }
        
        saveHistory(
            ToolType.WHOIS,
            domain,
            if (result.error != null) "Error" else "Success",
            result.raw
        )
        
        return@withContext result
    }

    suspend fun calculateSubnet(cidr: String): SubnetResult = withContext(Dispatchers.Default) {
        val parts = cidr.split("/")
        if (parts.size != 2) {
            return@withContext SubnetResult(error = "CIDR expected (e.g. 192.168.0.10/24)")
        }
        val ip = parts[0]
        val prefix = parts[1].toIntOrNull() ?: return@withContext SubnetResult(error = "Invalid prefix")
        
        val result = try {
            val address = InetAddress.getByName(ip)
            if (address !is Inet4Address) {
                SubnetResult(error = "Only IPv4 supported for subnet calculator")
            } else {
                val mask = (0xffffffffL shl (32 - prefix)) and 0xffffffffL
                val ipLong = address.address.fold(0L) { acc, byte -> (acc shl 8) or (byte.toInt() and 0xff).toLong() }
                val network = ipLong and mask
                val broadcast = network or mask.inv() and 0xffffffffL
                val usable = max(0L, (broadcast - network - 1))
                SubnetResult(
                    input = cidr,
                    networkAddress = longToIp(network),
                    broadcastAddress = longToIp(broadcast),
                    firstHost = if (usable > 0) longToIp(network + 1) else null,
                    lastHost = if (usable > 0) longToIp(broadcast - 1) else null,
                    usableHosts = usable
                )
            }
        } catch (t: Exception) {
            Timber.e(t, "Subnet calculation failed for %s", cidr)
            SubnetResult(error = t.message)
        }
        
        if (result.error == null) {
            saveHistory(
                ToolType.SUBNET,
                cidr,
                "Net: ${result.networkAddress}",
                "Network: ${result.networkAddress}\nBroadcast: ${result.broadcastAddress}\nUsable: ${result.usableHosts}"
            )
        }
        
        return@withContext result
    }

    suspend fun pingHost(
        host: String,
        timeoutMs: Int = 1500
    ): PingResult = withContext(Dispatchers.IO) {
        val start = System.nanoTime()
        val result = try {
            val address = InetAddress.getByName(host)
            val reachable = address.isReachable(timeoutMs)
            val latency = if (reachable) (System.nanoTime() - start) / 1_000_000 else null
            PingResult(host = host, success = reachable, latencyMs = latency, error = null)
        } catch (t: UnknownHostException) {
            PingResult(host = host, success = false, latencyMs = null, error = "Unknown host")
        } catch (t: Exception) {
            Timber.e(t, "Ping failed for %s", host)
            PingResult(host = host, success = false, latencyMs = null, error = t.message)
        }
        
        saveHistory(
            ToolType.PING,
            host,
            if (result.success) "${result.latencyMs}ms" else "Unreachable",
            "Host: $host\nReachable: ${result.success}\nLatency: ${result.latencyMs}ms\nError: ${result.error}"
        )
        
        return@withContext result
    }

    suspend fun detectCaptivePortal(
        url: String = "https://connectivitycheck.gstatic.com/generate_204"
    ): CaptivePortalResult = withContext(Dispatchers.IO) {
        return@withContext try {
            val request = Request.Builder().url(url).get().build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.code == 204) {
                    CaptivePortalResult(status = CaptivePortalStatus.OPEN)
                } else {
                    CaptivePortalResult(status = CaptivePortalStatus.CAPTIVE, httpCode = response.code)
                }
            }
        } catch (t: Exception) {
            Timber.e(t, "Captive portal check failed")
            CaptivePortalResult(status = CaptivePortalStatus.UNKNOWN, error = t.message)
        }
    }

    private suspend fun saveHistory(type: ToolType, query: String, summary: String, full: String) {
        dnsHistoryDao.insert(
            DnsHistoryEntity(
                toolType = type,
                query = query,
                resultSummary = summary,
                fullResult = full,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    private fun longToIp(value: Long): String {
        return listOf(
            (value shr 24 and 0xff).toInt(),
            (value shr 16 and 0xff).toInt(),
            (value shr 8 and 0xff).toInt(),
            (value and 0xff).toInt()
        ).joinToString(".")
    }
}
