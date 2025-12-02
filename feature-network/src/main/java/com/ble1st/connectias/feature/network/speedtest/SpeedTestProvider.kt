package com.ble1st.connectias.feature.network.speedtest

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.ble1st.connectias.feature.network.speedtest.models.LatencyResult
import com.ble1st.connectias.feature.network.speedtest.models.SpeedTestConfig
import com.ble1st.connectias.feature.network.speedtest.models.SpeedTestProgress
import com.ble1st.connectias.feature.network.speedtest.models.SpeedTestResult
import com.ble1st.connectias.feature.network.speedtest.models.SpeedTestServer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Provider for network speed testing functionality.
 *
 * Features:
 * - Download speed measurement
 * - Upload speed measurement  
 * - Latency/ping measurement
 * - Jitter calculation
 * - Multiple server support
 * - Historical results
 */
@Singleton
class SpeedTestProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val DEFAULT_SERVERS = listOf(
            SpeedTestServer(
                id = "cloudflare",
                name = "Cloudflare",
                url = "https://speed.cloudflare.com/__down?bytes=",
                uploadUrl = "https://speed.cloudflare.com/__up",
                location = "Global CDN",
                provider = "Cloudflare"
            ),
            SpeedTestServer(
                id = "hetzner",
                name = "Hetzner",
                url = "https://speed.hetzner.de/100MB.bin",
                location = "Germany",
                provider = "Hetzner"
            )
        )
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _history = MutableStateFlow<List<SpeedTestResult>>(emptyList())
    val history: StateFlow<List<SpeedTestResult>> = _history.asStateFlow()

    /**
     * Gets available test servers.
     */
    fun getServers(): List<SpeedTestServer> = DEFAULT_SERVERS

    /**
     * Runs a complete speed test.
     */
    fun runFullTest(
        server: SpeedTestServer = DEFAULT_SERVERS.first(),
        config: SpeedTestConfig = SpeedTestConfig()
    ): Flow<SpeedTestProgress> = flow {
        try {
            emit(SpeedTestProgress.Connecting(server))
            
            // Measure latency
            val latencyResult = measureLatency(server.url, config.latencySamples)
            emit(SpeedTestProgress.MeasuringLatency(
                currentLatency = latencyResult.avgLatency.toLong(),
                samples = latencyResult.samples
            ))

            // Measure download speed
            val downloadSpeed = measureDownload(server, config) { progress, speed, bytes ->
                emit(SpeedTestProgress.DownloadProgress(bytes, speed, progress))
            }

            // Measure upload speed
            val uploadSpeed = measureUpload(server, config) { progress, speed, bytes ->
                emit(SpeedTestProgress.UploadProgress(bytes, speed, progress))
            }

            val result = SpeedTestResult(
                server = server.copy(latency = latencyResult.avgLatency.toLong()),
                downloadSpeed = downloadSpeed,
                uploadSpeed = uploadSpeed,
                latency = latencyResult.avgLatency.toLong(),
                jitter = latencyResult.jitter,
                packetLoss = latencyResult.packetLoss,
                connectionType = getConnectionType()
            )

            // Save to history
            _history.update { listOf(result) + it.take(49) }

            emit(SpeedTestProgress.Completed(result))
        } catch (e: Exception) {
            Timber.e(e, "Speed test failed")
            emit(SpeedTestProgress.Error("Speed test failed: ${e.message}", e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Measures download speed.
     */
    private suspend fun measureDownload(
        server: SpeedTestServer,
        config: SpeedTestConfig,
        onProgress: suspend (Float, Double, Long) -> Unit
    ): Double = withContext(Dispatchers.IO) {
        val totalBytes = AtomicLong(0)
        val startTime = System.currentTimeMillis()
        val endTime = startTime + config.downloadDuration

        // Download progressively larger chunks
        val chunkSizes = listOf(
            100_000,      // 100 KB
            1_000_000,    // 1 MB
            10_000_000,   // 10 MB
            25_000_000    // 25 MB
        )

        var lastProgressUpdate = startTime

        for (chunkSize in chunkSizes) {
            if (System.currentTimeMillis() >= endTime) break

            try {
                val url = "${server.url}$chunkSize"
                val request = Request.Builder()
                    .url(url)
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use

                    response.body?.let { body ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        val stream = body.byteStream()

                        while (stream.read(buffer).also { bytesRead = it } != -1) {
                            if (System.currentTimeMillis() >= endTime) break
                            
                            totalBytes.addAndGet(bytesRead.toLong())

                            // Update progress every 100ms
                            if (System.currentTimeMillis() - lastProgressUpdate >= 100) {
                                val elapsed = System.currentTimeMillis() - startTime
                                val progress = elapsed.toFloat() / config.downloadDuration
                                val currentSpeed = calculateSpeed(totalBytes.get(), elapsed)
                                onProgress(progress.coerceAtMost(1f), currentSpeed, totalBytes.get())
                                lastProgressUpdate = System.currentTimeMillis()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Download chunk failed, continuing...")
            }
        }

        val totalTime = System.currentTimeMillis() - startTime
        calculateSpeed(totalBytes.get(), totalTime)
    }

    /**
     * Measures upload speed.
     */
    private suspend fun measureUpload(
        server: SpeedTestServer,
        config: SpeedTestConfig,
        onProgress: suspend (Float, Double, Long) -> Unit
    ): Double = withContext(Dispatchers.IO) {
        val uploadUrl = server.uploadUrl ?: return@withContext 0.0

        val totalBytes = AtomicLong(0)
        val startTime = System.currentTimeMillis()
        val endTime = startTime + config.uploadDuration

        // Generate random data for upload
        val uploadData = ByteArray(config.uploadChunkSize) { Random.nextInt(256).toByte() }
        var lastProgressUpdate = startTime

        while (System.currentTimeMillis() < endTime) {
            try {
                val requestBody = uploadData.toRequestBody("application/octet-stream".toMediaType())
                val request = Request.Builder()
                    .url(uploadUrl)
                    .post(requestBody)
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        totalBytes.addAndGet(uploadData.size.toLong())
                    }
                }

                // Update progress
                if (System.currentTimeMillis() - lastProgressUpdate >= 100) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val progress = elapsed.toFloat() / config.uploadDuration
                    val currentSpeed = calculateSpeed(totalBytes.get(), elapsed)
                    onProgress(progress.coerceAtMost(1f), currentSpeed, totalBytes.get())
                    lastProgressUpdate = System.currentTimeMillis()
                }
            } catch (e: Exception) {
                Timber.w(e, "Upload chunk failed, continuing...")
            }
        }

        val totalTime = System.currentTimeMillis() - startTime
        calculateSpeed(totalBytes.get(), totalTime)
    }

    /**
     * Measures latency to a host.
     */
    suspend fun measureLatency(
        urlOrHost: String,
        samples: Int = 10
    ): LatencyResult = withContext(Dispatchers.IO) {
        val host = extractHost(urlOrHost)
        val latencies = mutableListOf<Long>()
        var failedPings = 0

        repeat(samples) {
            try {
                val startTime = System.nanoTime()
                val reachable = InetAddress.getByName(host).isReachable(5000)
                val endTime = System.nanoTime()

                if (reachable) {
                    latencies.add((endTime - startTime) / 1_000_000) // Convert to ms
                } else {
                    failedPings++
                }
            } catch (e: Exception) {
                failedPings++
            }
            delay(100) // Small delay between pings
        }

        if (latencies.isEmpty()) {
            return@withContext LatencyResult(
                host = host,
                minLatency = 0,
                maxLatency = 0,
                avgLatency = 0.0,
                jitter = 0.0,
                packetLoss = 100f,
                samples = samples
            )
        }

        val avgLatency = latencies.average()
        val jitter = calculateJitter(latencies)
        val packetLoss = (failedPings.toFloat() / samples) * 100

        LatencyResult(
            host = host,
            minLatency = latencies.minOrNull() ?: 0,
            maxLatency = latencies.maxOrNull() ?: 0,
            avgLatency = avgLatency,
            jitter = jitter,
            packetLoss = packetLoss,
            samples = samples
        )
    }

    /**
     * Measures latency using HTTP instead of ICMP.
     */
    suspend fun measureHttpLatency(
        url: String,
        samples: Int = 10
    ): LatencyResult = withContext(Dispatchers.IO) {
        val latencies = mutableListOf<Long>()
        var failedPings = 0

        repeat(samples) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .head()
                    .build()

                val startTime = System.nanoTime()
                okHttpClient.newCall(request).execute().use { response ->
                    val endTime = System.nanoTime()
                    if (response.isSuccessful) {
                        latencies.add((endTime - startTime) / 1_000_000)
                    } else {
                        failedPings++
                    }
                }
            } catch (e: Exception) {
                failedPings++
            }
            delay(100)
        }

        val host = extractHost(url)

        if (latencies.isEmpty()) {
            return@withContext LatencyResult(
                host = host,
                minLatency = 0,
                maxLatency = 0,
                avgLatency = 0.0,
                jitter = 0.0,
                packetLoss = 100f,
                samples = samples
            )
        }

        LatencyResult(
            host = host,
            minLatency = latencies.minOrNull() ?: 0,
            maxLatency = latencies.maxOrNull() ?: 0,
            avgLatency = latencies.average(),
            jitter = calculateJitter(latencies),
            packetLoss = (failedPings.toFloat() / samples) * 100,
            samples = samples
        )
    }

    /**
     * Gets historical results.
     */
    fun getHistory(): List<SpeedTestResult> = _history.value

    /**
     * Clears history.
     */
    fun clearHistory() {
        _history.update { emptyList() }
    }

    /**
     * Gets the current connection type.
     */
    fun getConnectionType(): String {
        val network = connectivityManager.activeNetwork ?: return "Unknown"
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return "Unknown"

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                when {
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> "5G"
                    else -> "Mobile"
                }
            }
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Unknown"
        }
    }

    // Helper functions

    private fun calculateSpeed(bytes: Long, timeMs: Long): Double {
        if (timeMs <= 0) return 0.0
        val bitsPerSecond = (bytes * 8.0 * 1000) / timeMs
        return bitsPerSecond / 1_000_000 // Convert to Mbps
    }

    private fun calculateJitter(latencies: List<Long>): Double {
        if (latencies.size < 2) return 0.0

        val differences = latencies.zipWithNext { a, b -> kotlin.math.abs(b - a).toDouble() }
        return differences.average()
    }

    private fun extractHost(url: String): String {
        return try {
            java.net.URL(url).host
        } catch (e: Exception) {
            url.removePrefix("https://").removePrefix("http://").split("/").first()
        }
    }
}
