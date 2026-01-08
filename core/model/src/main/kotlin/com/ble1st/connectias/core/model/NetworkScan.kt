package com.ble1st.connectias.core.model

/**
 * Represents a network scan result.
 */
data class NetworkScan(
    val id: String,
    val timestamp: Long,
    val targetHost: String,
    val targetPort: Int?,
    val scanType: ScanType,
    val result: ScanResult,
    val duration: Long
)

enum class ScanType {
    PORT_SCAN,
    PING,
    DNS_LOOKUP,
    TRACEROUTE
}

enum class ScanResult {
    SUCCESS,
    FAILED,
    TIMEOUT,
    ERROR
}
