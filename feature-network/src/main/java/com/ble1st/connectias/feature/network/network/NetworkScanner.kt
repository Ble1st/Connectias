package com.ble1st.connectias.feature.network.network

import android.net.ConnectivityManager
import android.net.LinkProperties
import com.ble1st.connectias.feature.network.model.HostInfo
import com.ble1st.connectias.feature.network.model.NetworkEnvironment
import com.ble1st.connectias.feature.network.model.deviceTypeFromHostname
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber

class NetworkScanner(
    private val connectivityManager: ConnectivityManager
) {

    suspend fun detectEnvironment(): NetworkEnvironment? = withContext(Dispatchers.IO) {
        val active = connectivityManager.activeNetwork
        val linkProps = connectivityManager.getLinkProperties(active)
        val envFromLinks = linkProps?.let { fromLinkProperties(it) }
        if (envFromLinks != null) return@withContext envFromLinks

        val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
        val candidate = interfaces
            .filter { it.isUp && !it.isLoopback }
            .flatMap { iface ->
                iface.interfaceAddresses.mapNotNull { ia ->
                    val address = ia.address
                    if (address is Inet4Address && address.isSiteLocalAddress) {
                        val prefix = ia.networkPrefixLength.toInt()
                        val cidr = "${address.hostAddress}/$prefix"
                        NetworkEnvironment(
                            cidr = cidr,
                            interfaceName = iface.name,
                            gateway = null,
                            prefixLength = prefix
                        )
                    } else null
                }
            }
            .firstOrNull()

        candidate
    }

    private fun fromLinkProperties(linkProperties: LinkProperties): NetworkEnvironment? {
        val ipv4 = linkProperties.linkAddresses.firstOrNull { it.address is Inet4Address } ?: return null
        val address = ipv4.address as Inet4Address
        val prefix = ipv4.prefixLength
        val cidr = "${address.hostAddress}/$prefix"
        val gateway = linkProperties.routes.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress
        return NetworkEnvironment(
            cidr = cidr,
            interfaceName = linkProperties.interfaceName,
            gateway = gateway,
            prefixLength = prefix
        )
    }

    suspend fun scanHosts(
        environment: NetworkEnvironment,
        timeoutMs: Int = 400,
        maxConcurrency: Int = 64,
        maxHosts: Int = 512,
        onProgress: (Float) -> Unit = {}
    ): List<HostInfo> = coroutineScope {
        val (baseIp, prefix) = parseCidr(environment.cidr)
            ?: return@coroutineScope emptyList()

        val hostIps = buildHostRange(baseIp, prefix, maxHosts)
        if (hostIps.isEmpty()) return@coroutineScope emptyList()

        val results = Collections.synchronizedList(mutableListOf<HostInfo>())
        val semaphore = Semaphore(maxConcurrency)
        val counter = AtomicInteger(0)

        hostIps.map { ip ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val info = probeHost(ip, timeoutMs)
                    results.add(info)
                    val progress = counter.incrementAndGet().toFloat() / hostIps.size
                    onProgress(progress.coerceIn(0f, 1f))
                }
            }
        }.awaitAll()

        results
            .filter { it.isReachable }
            .sortedBy { it.ip }
    }

    private fun probeHost(ip: String, timeoutMs: Int): HostInfo {
        val start = System.currentTimeMillis()
        val address = InetAddress.getByName(ip)
        
        // 1. Try ICMP (isReachable)
        var reachable = runCatching { address.isReachable(timeoutMs / 2) }.getOrDefault(false)
        
        // 2. Fallback: Try TCP Connect on common ports if ICMP failed
        // We split the timeout to try multiple ports fast
        if (!reachable) {
             val tcpTimeout = timeoutMs / 2
             reachable = isTcpReachable(ip, 80, tcpTimeout) || 
                         isTcpReachable(ip, 443, tcpTimeout) || 
                         isTcpReachable(ip, 53, tcpTimeout) || // DNS
                         isTcpReachable(ip, 445, tcpTimeout) || // SMB
                         isTcpReachable(ip, 22, tcpTimeout)     // SSH
        }

        val elapsed = if (reachable) System.currentTimeMillis() - start else null
        
        // Only try to resolve hostname if reachable to save time
        val hostname = if (reachable) {
            runCatching { address.canonicalHostName }.getOrNull()?.takeIf { it != ip }
        } else null

        val mac = if (reachable) readArpEntry(ip) else null
        val deviceType = deviceTypeFromHostname(hostname)
        
        return HostInfo(
            ip = ip,
            hostname = hostname,
            mac = mac,
            deviceType = deviceType,
            isReachable = reachable,
            pingMs = elapsed
        )
    }

    private fun isTcpReachable(ip: String, port: Int, timeout: Int): Boolean {
        return try {
            java.net.Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress(ip, port), timeout)
                true
            }
        } catch (e: java.net.ConnectException) {
            // Connection refused means the host is UP but rejected the connection -> So it IS reachable!
            // This indicates the IP stack responded, so the host is online.
            true
        } catch (e: Exception) {
            // All other exceptions (SocketTimeoutException, NoRouteToHostException, IOException, etc.)
            // indicate the host is unreachable or the connection failed for other reasons.
            false
        }
    }

    private fun parseCidr(cidr: String): Pair<Int, Int>? {
        val parts = cidr.split("/")
        if (parts.size != 2) return null
        val address = runCatching { InetAddress.getByName(parts[0]) as Inet4Address }.getOrNull() ?: return null
        val prefix = parts[1].toIntOrNull() ?: return null
        val intAddr = ByteBuffer.wrap(address.address).order(ByteOrder.BIG_ENDIAN).int
        return intAddr to prefix
    }

    private fun buildHostRange(baseIp: Int, prefix: Int, maxHosts: Int): List<String> {
        val mask = if (prefix == 0) 0 else (-1 shl (32 - prefix))
        val network = baseIp and mask
        val broadcast = network or mask.inv()
        val firstHost = network + 1
        val lastHost = broadcast - 1
        val totalHosts = (lastHost - firstHost + 1).coerceAtLeast(0)
        if (totalHosts <= 0) return emptyList()

        val ips = mutableListOf<Int>()
        if (totalHosts <= maxHosts) {
            for (i in firstHost..lastHost) ips.add(i)
        } else {
            val step = (totalHosts / maxHosts).coerceAtLeast(1)
            var current = firstHost
            while (current <= lastHost && ips.size < maxHosts) {
                ips.add(current)
                current += step
            }
        }
        return ips.map { it.toIpv4() }
    }

    private fun Int.toIpv4(): String {
        val bytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(this).array()
        return "${bytes[0].toInt() and 0xFF}.${bytes[1].toInt() and 0xFF}.${bytes[2].toInt() and 0xFF}.${bytes[3].toInt() and 0xFF}"
    }

    private fun readArpEntry(ip: String): String? = runCatching {
        val arp = File("/proc/net/arp")
        if (!arp.exists()) return@runCatching null
        arp.readLines()
            .drop(1)
            .mapNotNull { line ->
                val parts = line.split(Regex("\\s+"))
                if (parts.size >= 4) parts[0] to parts[3] else null
            }
            .firstOrNull { it.first == ip }
            ?.second
            ?.takeIf { it != "00:00:00:00:00:00" }
    }.getOrNull().also {
        if (it == null) {
            Timber.d("No ARP entry for $ip")
        }
    }
}
