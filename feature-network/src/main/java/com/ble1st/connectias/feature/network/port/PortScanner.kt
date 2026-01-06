package com.ble1st.connectias.feature.network.port

import com.ble1st.connectias.feature.network.model.PortResult
import com.ble1st.connectias.feature.network.model.PortServiceRegistry
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException
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

class PortScanner {
    
    private val rustScanner = try {
        RustPortScanner()
    } catch (e: Exception) {
        null // Fallback to Kotlin implementation if Rust not available
    }

    suspend fun scan(
        host: String,
        startPort: Int,
        endPort: Int,
        timeoutMs: Int = 200,
        maxConcurrency: Int = 128,
        onProgress: (Float) -> Unit = {}
    ): List<PortResult> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val portRange = endPort - startPort + 1
        
        // Try Rust implementation first (much faster)
        if (rustScanner != null) {
            try {
                Timber.i("🔴 [PortScanner] Using RUST implementation - Range: $startPort-$endPort ($portRange ports)")
                val rustStartTime = System.currentTimeMillis()
                
                val results = rustScanner.scan(host, startPort, endPort, timeoutMs, maxConcurrency)
                
                val rustDuration = System.currentTimeMillis() - rustStartTime
                val totalDuration = System.currentTimeMillis() - startTime
                val portsPerSecond = if (rustDuration > 0) (portRange * 1000.0 / rustDuration).toInt() else 0
                
                Timber.i("✅ [PortScanner] RUST scan completed - Found: ${results.size} open ports | Duration: ${rustDuration}ms | Speed: ~$portsPerSecond ports/sec")
                Timber.d("📊 [PortScanner] Total time (including overhead): ${totalDuration}ms")
                
                // Call progress callback for compatibility
                onProgress(1.0f)
                return@withContext results
            } catch (e: Exception) {
                val rustDuration = System.currentTimeMillis() - startTime
                Timber.w(e, "❌ [PortScanner] RUST scan failed after ${rustDuration}ms, falling back to Kotlin implementation")
                // Fall through to Kotlin implementation
            }
        } else {
            Timber.w("⚠️ [PortScanner] Rust scanner not available, using Kotlin implementation")
        }
        
        // Fallback to Kotlin implementation
        Timber.i("🟢 [PortScanner] Using KOTLIN implementation - Range: $startPort-$endPort ($portRange ports)")
        val kotlinStartTime = System.currentTimeMillis()
        
        coroutineScope {
            if (startPort < 1 || endPort > 65535 || endPort < startPort) {
                throw IllegalArgumentException("Invalid port range $startPort-$endPort")
            }

            // Validate host early
            try {
                java.net.InetAddress.getByName(host)
            } catch (e: UnknownHostException) {
                throw IllegalArgumentException("Host nicht erreichbar: ${e.message}")
            }

            val ports = (startPort..endPort).toList()
            val results = Collections.synchronizedList(mutableListOf<PortResult>())
            val semaphore = Semaphore(maxConcurrency)
            val counter = AtomicInteger(0)

            ports.map { port ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val result = probePort(host, port, timeoutMs)
                        results.add(result)
                        val progress = counter.incrementAndGet().toFloat() / ports.size
                        onProgress(progress.coerceIn(0f, 1f))
                    }
                }
            }.awaitAll()

            val kotlinDuration = System.currentTimeMillis() - kotlinStartTime
            val totalDuration = System.currentTimeMillis() - startTime
            val openPorts = results.filter { it.isOpen }.sortedBy { it.port }
            val portsPerSecond = if (kotlinDuration > 0) (portRange * 1000.0 / kotlinDuration).toInt() else 0
            
            Timber.i("✅ [PortScanner] KOTLIN scan completed - Found: ${openPorts.size} open ports | Duration: ${kotlinDuration}ms | Speed: ~$portsPerSecond ports/sec")
            Timber.d("📊 [PortScanner] Total time (including overhead): ${totalDuration}ms")
            
            openPorts
        }
    }

    private fun probePort(host: String, port: Int, timeoutMs: Int): PortResult {
        var isOpen = false
        var banner: String? = null
        try {
            Socket().use { socket ->
                socket.soTimeout = timeoutMs
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                isOpen = true
                banner = tryReadBanner(socket)
            }
        } catch (e: Exception) {
            // Verbose logging disabled for performance - only log in debug mode
            if (Timber.forest().any { it is timber.log.Timber.DebugTree }) {
                Timber.v(e, "🟢 [PortScanner] Port $port closed or filtered")
            }
        }
        val service = PortServiceRegistry.serviceFor(port)
        return PortResult(port = port, isOpen = isOpen, service = service, banner = banner)
    }

    private fun tryReadBanner(socket: Socket): String? = runCatching {
        val input = socket.getInputStream()
        if (input.available() == 0) return@runCatching null
        val buffer = ByteArray(256)
        val read = input.read(buffer, 0, buffer.size)
        if (read > 0) {
            String(buffer, 0, read).trim()
        } else null
    }.getOrNull()
}
