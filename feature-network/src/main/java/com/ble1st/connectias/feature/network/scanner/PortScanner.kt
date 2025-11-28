package com.ble1st.connectias.feature.network.scanner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import timber.log.Timber
import com.ble1st.connectias.feature.network.models.PortScanResult
import java.net.InetSocketAddress
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scanner for checking open ports on network devices.
 * Performs parallel port scanning with configurable timeout.
 */
@Singleton
class PortScanner @Inject constructor() {

    /**
     * Common ports to scan.
     */
    val COMMON_PORTS = listOf(
        21, 22, 23, 25, 53, 80, 110, 143, 443, 445, 993, 995, 1433, 3306, 3389, 5432, 5900, 8080, 8443
    )

    /**
     * Scans a single port on a host.
     * 
     * @param host The host IP address
     * @param port The port number to scan
     * @param timeoutMs Timeout in milliseconds
     * @return PortScanResult with port status
     */
    suspend fun scanPort(
        host: String,
        port: Int,
        timeoutMs: Int = 1000
    ): PortScanResult = withContext(Dispatchers.IO) {
        try {
            val socket = Socket()
            val socketAddress = InetSocketAddress(host, port)
            
            socket.connect(socketAddress, timeoutMs)
            socket.close()
            
            val service = detectService(port)
            PortScanResult(
                host = host,
                port = port,
                isOpen = true,
                service = service
            )
        } catch (e: Exception) {
            PortScanResult(
                host = host,
                port = port,
                isOpen = false,
                service = null
            )
        }
    }

    /**
     * Scans multiple ports on a host in parallel.
     * 
     * @param host The host IP address
     * @param ports List of ports to scan
     * @param timeoutMs Timeout per port in milliseconds
     * @param maxConcurrent Maximum concurrent scans
     * @return List of PortScanResult
     */
    suspend fun scanPorts(
        host: String,
        ports: List<Int>,
        timeoutMs: Int = 1000,
        maxConcurrent: Int = 50
    ): List<PortScanResult> = withContext(Dispatchers.IO) {
        ports.chunked(maxConcurrent).flatMap { chunk ->
            chunk.map { port ->
                async { scanPort(host, port, timeoutMs) }
            }.awaitAll()
        }
    }

    /**
     * Scans common ports on a host.
     */
    suspend fun scanCommonPorts(
        host: String,
        timeoutMs: Int = 1000
    ): List<PortScanResult> {
        return scanPorts(host, COMMON_PORTS, timeoutMs)
    }

    /**
     * Detects service name based on port number.
     */
    private fun detectService(port: Int): String? {
        return when (port) {
            21 -> "FTP"
            22 -> "SSH"
            23 -> "Telnet"
            25 -> "SMTP"
            53 -> "DNS"
            80 -> "HTTP"
            110 -> "POP3"
            143 -> "IMAP"
            443 -> "HTTPS"
            445 -> "SMB"
            993 -> "IMAPS"
            995 -> "POP3S"
            1433 -> "MSSQL"
            3306 -> "MySQL"
            3389 -> "RDP"
            5432 -> "PostgreSQL"
            5900 -> "VNC"
            8080 -> "HTTP-Proxy"
            8443 -> "HTTPS-Alt"
            else -> null
        }
    }
}


