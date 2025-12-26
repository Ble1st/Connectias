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

    suspend fun scan(
        host: String,
        startPort: Int,
        endPort: Int,
        timeoutMs: Int = 200,
        maxConcurrency: Int = 128,
        onProgress: (Float) -> Unit = {}
    ): List<PortResult> = withContext(Dispatchers.IO) {
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

            results.filter { it.isOpen }.sortedBy { it.port }
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
            Timber.v(e, "Port $port closed or filtered")
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
