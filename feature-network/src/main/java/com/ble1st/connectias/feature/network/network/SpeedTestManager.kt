package com.ble1st.connectias.feature.network.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

data class SpeedTestResult(
    val downloadSpeedMbps: Float = 0f,
    val progress: Float = 0f, // 0..1
    val isFinished: Boolean = false,
    val error: String? = null
)

@Singleton
class SpeedTestManager @Inject constructor() {

    private val client = OkHttpClient.Builder().build()
    
    // Using a reliable large file source (e.g., Cloudflare or generic speedtest file)
    // Cloudflare 100MB test file
    private val testUrl = "https://speed.cloudflare.com/__down?bytes=25000000" 

    fun startDownloadTest(): Flow<SpeedTestResult> = flow {
        emit(SpeedTestResult(progress = 0f))
        
        try {
            val request = Request.Builder().url(testUrl).build()
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                emit(SpeedTestResult(error = "HTTP Error: ${response.code}"))
                return@flow
            }

            val body = response.body ?: run {
                emit(SpeedTestResult(error = "No response body"))
                return@flow
            }

            val contentLength = body.contentLength()
            val source = body.source()
            
            val startTime = System.currentTimeMillis()
            var bytesRead: Long = 0
            val buffer = ByteArray(8192)
            var lastUpdate = 0L
            
            try {
                while (true) {
                    val read = source.read(buffer)
                    if (read == -1) break
                    
                    bytesRead += read
                    val now = System.currentTimeMillis()
                    
                    // Update every 100ms
                    if (now - lastUpdate > 100) {
                        val durationSeconds = (now - startTime) / 1000.0
                        if (durationSeconds > 0) {
                            val bitsLoaded = bytesRead * 8
                            val speedBps = bitsLoaded / durationSeconds
                            val speedMbps = (speedBps / 1_000_000.0).toFloat()
                            
                            val progress = if (contentLength > 0) {
                                (bytesRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                            } else {
                                // Calculate dynamic progress based on bytes read
                                // Use logarithmic scale to show progress that approaches but never reaches 1.0
                                // until download is actually complete
                                // Formula: 1 - 1 / (1 + bytesRead / estimatedChunkSize)
                                // estimatedChunkSize of 1MB provides reasonable progress curve
                                val estimatedChunkSize = 1_000_000.0 // 1MB
                                val dynamicProgress = 1.0 - (1.0 / (1.0 + bytesRead / estimatedChunkSize))
                                dynamicProgress.coerceIn(0.0, 0.95).toFloat() // Cap at 0.95 until complete
                            }
                            
                            emit(SpeedTestResult(downloadSpeedMbps = speedMbps, progress = progress))
                        }
                        lastUpdate = now
                    }
                }
                
                // Final result
                val totalDuration = (System.currentTimeMillis() - startTime) / 1000.0
                val totalSpeedMbps = ((bytesRead * 8) / totalDuration / 1_000_000.0).toFloat()
                emit(SpeedTestResult(downloadSpeedMbps = totalSpeedMbps, progress = 1f, isFinished = true))
                
            } finally {
                source.close()
                response.close()
            }

        } catch (e: Exception) {
            emit(SpeedTestResult(error = e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)
}
