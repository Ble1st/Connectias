package com.ble1st.connectias.feature.network.speedtest.models

import kotlinx.serialization.Serializable

/**
 * Speed test server configuration.
 */
@Serializable
data class SpeedTestServer(
    val id: String,
    val name: String,
    val url: String,
    val uploadUrl: String? = null,
    val location: String? = null,
    val provider: String? = null,
    val latency: Long? = null
)

/**
 * Result of a complete speed test.
 */
@Serializable
data class SpeedTestResult(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val server: SpeedTestServer,
    val downloadSpeed: Double, // Mbps
    val uploadSpeed: Double, // Mbps
    val latency: Long, // ms
    val jitter: Double, // ms
    val packetLoss: Float, // percentage
    val connectionType: String, // WiFi, Mobile, etc.
    val isp: String? = null
) {
    val downloadSpeedFormatted: String
        get() = formatSpeed(downloadSpeed)

    val uploadSpeedFormatted: String
        get() = formatSpeed(uploadSpeed)

    private fun formatSpeed(speed: Double): String {
        return when {
            speed >= 1000 -> String.format("%.2f Gbps", speed / 1000)
            speed >= 1 -> String.format("%.2f Mbps", speed)
            else -> String.format("%.2f Kbps", speed * 1000)
        }
    }
}

/**
 * Progress update during speed test.
 */
sealed class SpeedTestProgress {
    data class Connecting(val server: SpeedTestServer) : SpeedTestProgress()
    data class MeasuringLatency(val currentLatency: Long, val samples: Int) : SpeedTestProgress()
    data class DownloadProgress(
        val bytesTransferred: Long,
        val currentSpeed: Double, // Mbps
        val progress: Float // 0-1
    ) : SpeedTestProgress()
    data class UploadProgress(
        val bytesTransferred: Long,
        val currentSpeed: Double, // Mbps
        val progress: Float // 0-1
    ) : SpeedTestProgress()
    data class Completed(val result: SpeedTestResult) : SpeedTestProgress()
    data class Error(val message: String, val exception: Throwable? = null) : SpeedTestProgress()
}

/**
 * Latency measurement result.
 */
@Serializable
data class LatencyResult(
    val host: String,
    val minLatency: Long,
    val maxLatency: Long,
    val avgLatency: Double,
    val jitter: Double,
    val packetLoss: Float,
    val samples: Int
)

/**
 * Speed test configuration.
 */
data class SpeedTestConfig(
    val downloadDuration: Long = 10_000, // 10 seconds
    val uploadDuration: Long = 10_000, // 10 seconds
    val latencySamples: Int = 10,
    val parallelConnections: Int = 4,
    val downloadChunkSize: Int = 1024 * 1024, // 1 MB
    val uploadChunkSize: Int = 256 * 1024 // 256 KB
)

/**
 * Test phase during speed test.
 */
enum class SpeedTestPhase {
    IDLE,
    CONNECTING,
    MEASURING_LATENCY,
    DOWNLOADING,
    UPLOADING,
    COMPLETED,
    ERROR
}
