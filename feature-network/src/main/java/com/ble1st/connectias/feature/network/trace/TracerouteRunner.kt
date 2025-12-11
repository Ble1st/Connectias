package com.ble1st.connectias.feature.network.trace

import com.ble1st.connectias.feature.network.model.TraceHop
import com.ble1st.connectias.feature.network.model.TraceStatus
import java.io.BufferedReader
import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

class TracerouteRunner {

    suspend fun traceroute(
        target: String,
        maxHops: Int = 20,
        timeoutSec: Int = 2
    ): List<TraceHop> = withContext(Dispatchers.IO) {
        val resolved = runCatching { InetAddress.getByName(target) }
            .getOrElse { throw IllegalArgumentException("Host nicht erreichbar: ${it.message}") }
        val destinationIp = resolved.hostAddress ?: target
        val binary = availablePingBinary()
            ?: throw IllegalStateException("ping Binary nicht gefunden")

        val hops = mutableListOf<TraceHop>()
        for (ttl in 1..maxHops) {
            val command = listOf(
                binary,
                "-c",
                "1",
                "-t",
                ttl.toString(),
                "-W",
                timeoutSec.toString(),
                destinationIp
            )
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().use(BufferedReader::readText)
            val exitCode = process.waitFor()
            val hop = parseHop(ttl, output, exitCode, destinationIp)
            hops.add(hop)
            if (hop.status == TraceStatus.SUCCESS && hop.ip == destinationIp) {
                break
            }
        }
        hops
    }

    private fun availablePingBinary(): String? {
        val candidates = listOf("/system/bin/ping", "/system/bin/ping6", "ping")
        return candidates.firstOrNull { path ->
            runCatching { java.io.File(path).canExecute() }.getOrDefault(false)
        }
    }

    private fun parseHop(
        ttl: Int,
        output: String,
        exitCode: Int,
        destinationIp: String
    ): TraceHop {
        val timeRegex = Regex("time[=<]?\\s*([0-9.]+)\\s*ms")
        val fromRegex = Regex("from\\s+([^\\s]+)")
        val ipRegex = Regex("\\b\\d{1,3}(?:\\.\\d{1,3}){3}\\b")

        val timeMs = timeRegex.find(output)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.toLong()
        val from = fromRegex.find(output)?.groupValues?.getOrNull(1)
        val ip = ipRegex.find(from ?: output)?.value

        return when {
            exitCode == 0 && ip != null -> TraceHop(
                hop = ttl,
                host = from,
                ip = ip,
                rttMs = timeMs,
                status = TraceStatus.SUCCESS
            )

            output.contains("Time to live exceeded", ignoreCase = true) ||
                output.contains("ttl expired", ignoreCase = true) -> TraceHop(
                hop = ttl,
                host = from,
                ip = ip,
                rttMs = timeMs,
                status = TraceStatus.TIMEOUT
            )

            else -> TraceHop(
                hop = ttl,
                host = from,
                ip = ip,
                rttMs = timeMs,
                status = TraceStatus.ERROR,
                error = "Keine Antwort"
            )
        }.also {
            Timber.d("Traceroute hop $ttl -> ${it.ip ?: "?"} (${it.status})")
        }
    }
}
