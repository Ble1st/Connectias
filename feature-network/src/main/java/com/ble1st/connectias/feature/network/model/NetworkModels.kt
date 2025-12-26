package com.ble1st.connectias.feature.network.model

import java.time.Instant

enum class SecurityType {
    OPEN,
    WEP,
    WPA,
    WPA2,
    WPA3,
    UNKNOWN
}

enum class DeviceType {
    ROUTER,
    COMPUTER,
    PHONE,
    PRINTER,
    IOT,
    SERVER,
    UNKNOWN
}

data class WifiNetwork(
    val ssid: String?,
    val bssid: String?,
    val rssi: Int,
    val frequency: Int,
    val channel: Int?,
    val security: SecurityType,
    val capabilities: String
)

data class NetworkEnvironment(
    val cidr: String,
    val interfaceName: String?,
    val gateway: String?,
    val prefixLength: Int
)

data class HostInfo(
    val ip: String,
    val hostname: String?,
    val mac: String?,
    val deviceType: DeviceType,
    val isReachable: Boolean,
    val pingMs: Long?
)

data class PortRangePreset(
    val label: String,
    val start: Int,
    val end: Int
)

data class PortResult(
    val port: Int,
    val isOpen: Boolean,
    val service: String?,
    val banner: String?
)

data class SslReport(
    val subject: String,
    val issuer: String,
    val validFrom: Instant,
    val validTo: Instant,
    val daysRemaining: Long,
    val isValidNow: Boolean,
    val keyAlgorithm: String?,
    val keySize: Int?,
    val signatureAlgorithm: String?,
    val problems: List<String>
)
