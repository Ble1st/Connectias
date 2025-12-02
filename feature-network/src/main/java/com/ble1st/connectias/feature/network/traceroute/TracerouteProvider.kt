package com.ble1st.connectias.feature.network.traceroute

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for traceroute functionality.
 */
@Singleton
class TracerouteProvider @Inject constructor() {

    /**
     * Runs a traceroute to the target host.
     */
    fun runTraceroute(
        host: String,
        maxHops: Int = 30,
        timeout: Int = 3000
    ): Flow<TracerouteHop> = flow {
        emit(TracerouteHop(hop = 0, status = HopStatus.STARTED))

        for (ttl in 1..maxHops) {
            val hop = traceHop(host, ttl, timeout)
            emit(hop)

            if (hop.status == HopStatus.DESTINATION_REACHED) {
                break
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Traces a single hop.
     */
    private suspend fun traceHop(
        host: String,
        ttl: Int,
        timeout: Int
    ): TracerouteHop = withContext(Dispatchers.IO) {
        try {
            val startTime = System.currentTimeMillis()

            // Use ping with TTL to simulate traceroute
            val process = ProcessBuilder(
                "ping",
                "-c", "1",
                "-t", ttl.toString(),
                "-W", (timeout / 1000).toString(),
                host
            ).redirectErrorStream(true).start()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            val exitCode = process.waitFor()

            val endTime = System.currentTimeMillis()
            val latency = (endTime - startTime).toInt()

            parseHopResult(output, ttl, latency, host)
        } catch (e: Exception) {
            Timber.e(e, "Error tracing hop $ttl")
            TracerouteHop(
                hop = ttl,
                status = HopStatus.ERROR,
                errorMessage = e.message
            )
        }
    }

    private fun parseHopResult(output: String, ttl: Int, latency: Int, targetHost: String): TracerouteHop {
        // Parse ping output
        val ipPattern = """from\s+(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})""".toRegex()
        val ipMatch = ipPattern.find(output)

        val timePattern = """time[=<]\s*(\d+(?:\.\d+)?)""".toRegex()
        val timeMatch = timePattern.find(output)

        val hopIp = ipMatch?.groupValues?.get(1)
        val rtt = timeMatch?.groupValues?.get(1)?.toFloatOrNull()?.toInt() ?: latency

        return when {
            output.contains("Time to live exceeded") -> {
                val hostname = hopIp?.let { resolveHostname(it) }
                TracerouteHop(
                    hop = ttl,
                    ipAddress = hopIp,
                    hostname = hostname,
                    latency = rtt,
                    status = HopStatus.HOP_REACHED
                )
            }
            output.contains("bytes from") -> {
                val hostname = hopIp?.let { resolveHostname(it) }
                TracerouteHop(
                    hop = ttl,
                    ipAddress = hopIp,
                    hostname = hostname,
                    latency = rtt,
                    status = HopStatus.DESTINATION_REACHED
                )
            }
            output.contains("100% packet loss") || output.contains("Request timeout") -> {
                TracerouteHop(
                    hop = ttl,
                    status = HopStatus.TIMEOUT
                )
            }
            else -> {
                TracerouteHop(
                    hop = ttl,
                    status = HopStatus.UNKNOWN
                )
            }
        }
    }

    private fun resolveHostname(ip: String): String? {
        return try {
            InetAddress.getByName(ip).hostName
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Runs MTR (My TraceRoute) style analysis.
     */
    suspend fun runMtr(
        host: String,
        probeCount: Int = 10
    ): List<MtrHop> = withContext(Dispatchers.IO) {
        val hops = mutableMapOf<Int, MutableList<Int>>()
        val hopInfo = mutableMapOf<Int, Pair<String?, String?>>()

        repeat(probeCount) {
            runTraceroute(host, 30, 3000).collect { hop ->
                if (hop.status == HopStatus.HOP_REACHED || hop.status == HopStatus.DESTINATION_REACHED) {
                    hop.latency?.let { latency ->
                        hops.getOrPut(hop.hop) { mutableListOf() }.add(latency)
                        hopInfo[hop.hop] = hop.ipAddress to hop.hostname
                    }
                }
            }
        }

        hops.map { (hopNum, latencies) ->
            val (ip, hostname) = hopInfo[hopNum] ?: (null to null)
            MtrHop(
                hop = hopNum,
                ipAddress = ip,
                hostname = hostname,
                loss = ((probeCount - latencies.size) * 100.0 / probeCount).toFloat(),
                sent = probeCount,
                received = latencies.size,
                avgLatency = latencies.average().toFloat(),
                minLatency = latencies.minOrNull()?.toFloat() ?: 0f,
                maxLatency = latencies.maxOrNull()?.toFloat() ?: 0f,
                standardDeviation = calculateStdDev(latencies)
            )
        }.sortedBy { it.hop }
    }

    private fun calculateStdDev(values: List<Int>): Float {
        if (values.size < 2) return 0f
        val mean = values.average()
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return kotlin.math.sqrt(variance).toFloat()
    }
}

/**
 * A single hop in the traceroute.
 */
@Serializable
data class TracerouteHop(
    val hop: Int,
    val ipAddress: String? = null,
    val hostname: String? = null,
    val latency: Int? = null,
    val status: HopStatus,
    val errorMessage: String? = null
)

/**
 * Status of a hop.
 */
enum class HopStatus {
    STARTED,
    HOP_REACHED,
    DESTINATION_REACHED,
    TIMEOUT,
    ERROR,
    UNKNOWN
}

/**
 * MTR-style hop with statistics.
 */
@Serializable
data class MtrHop(
    val hop: Int,
    val ipAddress: String?,
    val hostname: String?,
    val loss: Float,
    val sent: Int,
    val received: Int,
    val avgLatency: Float,
    val minLatency: Float,
    val maxLatency: Float,
    val standardDeviation: Float
)
