package com.ble1st.connectias.feature.network.latency

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import timber.log.Timber
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for network latency monitoring functionality.
 */
@Singleton
class NetworkLatencyProvider @Inject constructor() {

    private val _monitoringTargets = MutableStateFlow<List<MonitoringTarget>>(emptyList())
    val monitoringTargets: StateFlow<List<MonitoringTarget>> = _monitoringTargets.asStateFlow()

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    /**
     * Pings a host and returns latency.
     */
    suspend fun ping(host: String, timeout: Int = 3000): PingResult = withContext(Dispatchers.IO) {
        try {
            val startTime = System.currentTimeMillis()
            val address = InetAddress.getByName(host)
            val reachable = address.isReachable(timeout)
            val endTime = System.currentTimeMillis()

            if (reachable) {
                PingResult(
                    host = host,
                    success = true,
                    latency = (endTime - startTime).toInt(),
                    resolvedIp = address.hostAddress
                )
            } else {
                // Try TCP connect as fallback
                tcpPing(host, 80, timeout)
            }
        } catch (e: Exception) {
            Timber.w(e, "Ping failed for $host")
            PingResult(
                host = host,
                success = false,
                error = e.message
            )
        }
    }

    /**
     * Performs TCP ping to a host:port.
     */
    suspend fun tcpPing(
        host: String,
        port: Int,
        timeout: Int = 3000
    ): PingResult = withContext(Dispatchers.IO) {
        try {
            val startTime = System.currentTimeMillis()
            val socket = Socket()
            socket.connect(InetSocketAddress(host, port), timeout)
            val endTime = System.currentTimeMillis()
            socket.close()

            PingResult(
                host = host,
                success = true,
                latency = (endTime - startTime).toInt(),
                port = port
            )
        } catch (e: IOException) {
            PingResult(
                host = host,
                success = false,
                port = port,
                error = e.message
            )
        }
    }

    /**
     * Runs multiple pings and calculates statistics.
     */
    suspend fun pingWithStats(
        host: String,
        count: Int = 10,
        interval: Long = 1000
    ): PingStatistics = withContext(Dispatchers.IO) {
        val results = mutableListOf<PingResult>()

        repeat(count) {
            val result = ping(host)
            results.add(result)
            if (it < count - 1) {
                delay(interval)
            }
        }

        val successful = results.filter { it.success }
        val latencies = successful.mapNotNull { it.latency }

        PingStatistics(
            host = host,
            sent = count,
            received = successful.size,
            lost = count - successful.size,
            lossPercentage = ((count - successful.size) * 100.0 / count).toFloat(),
            minLatency = latencies.minOrNull() ?: 0,
            maxLatency = latencies.maxOrNull() ?: 0,
            avgLatency = if (latencies.isNotEmpty()) latencies.average().toFloat() else 0f,
            jitter = calculateJitter(latencies),
            results = results
        )
    }

    /**
     * Monitors latency continuously.
     */
    fun monitorLatency(
        host: String,
        interval: Long = 5000
    ): Flow<LatencyMeasurement> = flow {
        _isMonitoring.value = true

        while (_isMonitoring.value) {
            val result = ping(host)
            emit(
                LatencyMeasurement(
                    host = host,
                    timestamp = System.currentTimeMillis(),
                    latency = result.latency,
                    success = result.success,
                    error = result.error
                )
            )
            delay(interval)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Stops monitoring.
     */
    fun stopMonitoring() {
        _isMonitoring.value = false
    }

    /**
     * Adds a target to monitor.
     */
    fun addMonitoringTarget(name: String, host: String, port: Int? = null) {
        val target = MonitoringTarget(
            name = name,
            host = host,
            port = port
        )
        _monitoringTargets.update { it + target }
    }

    /**
     * Removes a monitoring target.
     */
    fun removeMonitoringTarget(host: String) {
        _monitoringTargets.update { it.filter { t -> t.host != host } }
    }

    /**
     * Measures latency to multiple hosts.
     */
    suspend fun measureMultiple(hosts: List<String>): List<PingResult> = withContext(Dispatchers.IO) {
        hosts.map { host -> ping(host) }
    }

    /**
     * Finds the fastest host from a list.
     */
    suspend fun findFastestHost(hosts: List<String>): PingResult? = withContext(Dispatchers.IO) {
        val results = measureMultiple(hosts)
        results.filter { it.success }.minByOrNull { it.latency ?: Int.MAX_VALUE }
    }

    private fun calculateJitter(latencies: List<Int>): Float {
        if (latencies.size < 2) return 0f

        var totalDiff = 0.0
        for (i in 1 until latencies.size) {
            totalDiff += kotlin.math.abs(latencies[i] - latencies[i - 1])
        }

        return (totalDiff / (latencies.size - 1)).toFloat()
    }
}

/**
 * Result of a ping.
 */
@Serializable
data class PingResult(
    val host: String,
    val success: Boolean,
    val latency: Int? = null,
    val resolvedIp: String? = null,
    val port: Int? = null,
    val error: String? = null
)

/**
 * Ping statistics.
 */
@Serializable
data class PingStatistics(
    val host: String,
    val sent: Int,
    val received: Int,
    val lost: Int,
    val lossPercentage: Float,
    val minLatency: Int,
    val maxLatency: Int,
    val avgLatency: Float,
    val jitter: Float,
    val results: List<PingResult>
)

/**
 * Single latency measurement.
 */
@Serializable
data class LatencyMeasurement(
    val host: String,
    val timestamp: Long,
    val latency: Int?,
    val success: Boolean,
    val error: String?
)

/**
 * Monitoring target.
 */
@Serializable
data class MonitoringTarget(
    val name: String,
    val host: String,
    val port: Int? = null,
    val lastLatency: Int? = null,
    val lastChecked: Long? = null,
    val isOnline: Boolean = false
)
