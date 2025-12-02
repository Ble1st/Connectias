package com.ble1st.connectias.feature.security.wifi.models

import kotlinx.serialization.Serializable

/**
 * WiFi security audit report.
 */
@Serializable
data class WifiSecurityReport(
    val id: String = java.util.UUID.randomUUID().toString(),
    val ssid: String,
    val bssid: String,
    val timestamp: Long = System.currentTimeMillis(),
    val securityType: WifiSecurityType,
    val encryptionStrength: EncryptionStrength,
    val signalStrength: Int, // dBm
    val frequency: Int, // MHz
    val channel: Int,
    val vulnerabilities: List<WifiVulnerability> = emptyList(),
    val recommendations: List<String> = emptyList(),
    val overallRisk: RiskLevel,
    val isHidden: Boolean = false,
    val isOpenNetwork: Boolean = false
)

/**
 * WiFi security type.
 */
enum class WifiSecurityType {
    OPEN,
    WEP,
    WPA,
    WPA2_PSK,
    WPA2_ENTERPRISE,
    WPA3_PSK,
    WPA3_ENTERPRISE,
    UNKNOWN
}

/**
 * Encryption strength assessment.
 */
enum class EncryptionStrength {
    NONE,
    WEAK,
    MODERATE,
    STRONG,
    VERY_STRONG
}

/**
 * Risk level assessment.
 */
enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * WiFi vulnerability detected.
 */
@Serializable
data class WifiVulnerability(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: VulnerabilityType,
    val name: String,
    val description: String,
    val severity: RiskLevel,
    val cveId: String? = null,
    val mitigation: String? = null
)

/**
 * Types of WiFi vulnerabilities.
 */
enum class VulnerabilityType {
    WEAK_ENCRYPTION,
    OUTDATED_PROTOCOL,
    EVIL_TWIN_DETECTED,
    ROGUE_AP_DETECTED,
    DEAUTH_ATTACK,
    WPS_ENABLED,
    HIDDEN_SSID,
    OPEN_NETWORK,
    WEAK_PASSWORD,
    PMKID_VULNERABLE,
    KRACK_VULNERABLE,
    DRAGONBLOOD_VULNERABLE
}

/**
 * Suspicious access point detection.
 */
@Serializable
data class SuspiciousAP(
    val ssid: String,
    val bssid: String,
    val signalStrength: Int,
    val reason: SuspiciousReason,
    val confidence: Float, // 0-1
    val detectedAt: Long = System.currentTimeMillis()
)

/**
 * Reasons for suspecting an AP.
 */
enum class SuspiciousReason {
    DUPLICATE_SSID,
    SIMILAR_BSSID,
    UNUSUAL_SIGNAL_STRENGTH,
    MAC_SPOOFING_DETECTED,
    DEAUTH_FLOOD,
    BEACON_ANOMALY
}

/**
 * Evil twin detection result.
 */
@Serializable
data class EvilTwinResult(
    val isDetected: Boolean,
    val legitimateAP: String?,
    val suspectedEvilTwin: SuspiciousAP?,
    val evidence: List<String> = emptyList()
)

/**
 * Deauthentication attack event.
 */
@Serializable
data class DeauthEvent(
    val timestamp: Long,
    val sourceMAC: String,
    val targetMAC: String,
    val reason: Int,
    val count: Int = 1
)

/**
 * Encryption assessment details.
 */
@Serializable
data class EncryptionAssessment(
    val securityType: WifiSecurityType,
    val cipherSuites: List<String>,
    val keyManagement: String,
    val pmfEnabled: Boolean, // Protected Management Frames
    val isWpa3Capable: Boolean,
    val strength: EncryptionStrength,
    val weaknesses: List<String>
)
