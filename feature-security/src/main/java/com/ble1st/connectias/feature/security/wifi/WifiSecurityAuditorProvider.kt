package com.ble1st.connectias.feature.security.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.ble1st.connectias.feature.security.wifi.models.DeauthEvent
import com.ble1st.connectias.feature.security.wifi.models.EncryptionAssessment
import com.ble1st.connectias.feature.security.wifi.models.EncryptionStrength
import com.ble1st.connectias.feature.security.wifi.models.EvilTwinResult
import com.ble1st.connectias.feature.security.wifi.models.RiskLevel
import com.ble1st.connectias.feature.security.wifi.models.SuspiciousAP
import com.ble1st.connectias.feature.security.wifi.models.SuspiciousReason
import com.ble1st.connectias.feature.security.wifi.models.VulnerabilityType
import com.ble1st.connectias.feature.security.wifi.models.WifiSecurityReport
import com.ble1st.connectias.feature.security.wifi.models.WifiSecurityType
import com.ble1st.connectias.feature.security.wifi.models.WifiVulnerability
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for WiFi security auditing functionality.
 *
 * Features:
 * - Current network security assessment
 * - Rogue access point detection
 * - Evil twin detection
 * - Encryption strength analysis
 * - Vulnerability detection
 */
@Singleton
class WifiSecurityAuditorProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _auditHistory = MutableStateFlow<List<WifiSecurityReport>>(emptyList())
    val auditHistory: StateFlow<List<WifiSecurityReport>> = _auditHistory.asStateFlow()

    /**
     * Audits the current WiFi network.
     */
    suspend fun auditCurrentNetwork(): WifiSecurityReport? = withContext(Dispatchers.IO) {
        try {
            val wifiInfo = wifiManager.connectionInfo ?: return@withContext null
            val ssid = wifiInfo.ssid?.removeSurrounding("\"") ?: return@withContext null
            val bssid = wifiInfo.bssid ?: return@withContext null

            if (ssid == "<unknown ssid>") {
                return@withContext null
            }

            // Get scan results for more info
            val scanResults = wifiManager.scanResults
            val currentNetwork = scanResults.find { it.BSSID == bssid }

            val securityType = determineSecurityType(currentNetwork)
            val encryptionAssessment = analyzeEncryption(currentNetwork, securityType)
            val vulnerabilities = detectVulnerabilities(currentNetwork, securityType, encryptionAssessment)
            val recommendations = generateRecommendations(securityType, vulnerabilities)

            val report = WifiSecurityReport(
                ssid = ssid,
                bssid = bssid,
                securityType = securityType,
                encryptionStrength = encryptionAssessment.strength,
                signalStrength = wifiInfo.rssi,
                frequency = currentNetwork?.frequency ?: 0,
                channel = frequencyToChannel(currentNetwork?.frequency ?: 0),
                vulnerabilities = vulnerabilities,
                recommendations = recommendations,
                overallRisk = calculateOverallRisk(vulnerabilities),
                isHidden = currentNetwork?.SSID.isNullOrEmpty(),
                isOpenNetwork = securityType == WifiSecurityType.OPEN
            )

            // Save to history
            _auditHistory.value = listOf(report) + _auditHistory.value.take(49)

            report
        } catch (e: Exception) {
            Timber.e(e, "Error auditing current network")
            null
        }
    }

    /**
     * Detects potentially rogue access points.
     */
    suspend fun detectRogueAPs(
        knownBSSIDs: List<String> = emptyList()
    ): List<SuspiciousAP> = withContext(Dispatchers.IO) {
        val suspicious = mutableListOf<SuspiciousAP>()
        val scanResults = wifiManager.scanResults

        // Group by SSID
        val bySSID = scanResults.groupBy { it.SSID }

        for ((ssid, aps) in bySSID) {
            if (ssid.isNullOrEmpty()) continue

            // Multiple APs with same SSID but different security
            val securityTypes = aps.map { determineSecurityType(it) }.distinct()
            if (securityTypes.size > 1 && securityTypes.contains(WifiSecurityType.OPEN)) {
                aps.filter { determineSecurityType(it) == WifiSecurityType.OPEN }.forEach { ap ->
                    suspicious.add(
                        SuspiciousAP(
                            ssid = ssid,
                            bssid = ap.BSSID,
                            signalStrength = ap.level,
                            reason = SuspiciousReason.DUPLICATE_SSID,
                            confidence = 0.8f
                        )
                    )
                }
            }

            // Check for BSSIDs not in known list
            if (knownBSSIDs.isNotEmpty()) {
                aps.filter { it.BSSID !in knownBSSIDs }.forEach { ap ->
                    suspicious.add(
                        SuspiciousAP(
                            ssid = ssid,
                            bssid = ap.BSSID,
                            signalStrength = ap.level,
                            reason = SuspiciousReason.MAC_SPOOFING_DETECTED,
                            confidence = 0.6f
                        )
                    )
                }
            }

            // Unusually strong signal (potential honeypot)
            aps.filter { it.level > -30 }.forEach { ap ->
                suspicious.add(
                    SuspiciousAP(
                        ssid = ssid,
                        bssid = ap.BSSID,
                        signalStrength = ap.level,
                        reason = SuspiciousReason.UNUSUAL_SIGNAL_STRENGTH,
                        confidence = 0.5f
                    )
                )
            }
        }

        suspicious.distinctBy { it.bssid }
    }

    /**
     * Checks encryption strength of current network.
     */
    suspend fun checkEncryptionStrength(): EncryptionAssessment = withContext(Dispatchers.IO) {
        val wifiInfo = wifiManager.connectionInfo
        val scanResults = wifiManager.scanResults
        val currentNetwork = scanResults.find { it.BSSID == wifiInfo?.bssid }

        val securityType = determineSecurityType(currentNetwork)
        analyzeEncryption(currentNetwork, securityType)
    }

    /**
     * Detects potential evil twin attacks.
     */
    suspend fun detectEvilTwin(ssid: String): EvilTwinResult = withContext(Dispatchers.IO) {
        val scanResults = wifiManager.scanResults
        val matchingAPs = scanResults.filter { it.SSID == ssid }

        if (matchingAPs.size <= 1) {
            return@withContext EvilTwinResult(
                isDetected = false,
                legitimateAP = matchingAPs.firstOrNull()?.BSSID,
                suspectedEvilTwin = null
            )
        }

        // Look for suspicious patterns
        val evidence = mutableListOf<String>()
        var suspectedEvilTwin: SuspiciousAP? = null

        // Check for open network among secured ones
        val securityTypes = matchingAPs.map { determineSecurityType(it) }
        if (securityTypes.contains(WifiSecurityType.OPEN) && securityTypes.any { it != WifiSecurityType.OPEN }) {
            evidence.add("Open network found among secured networks with same SSID")
            val openAP = matchingAPs.find { determineSecurityType(it) == WifiSecurityType.OPEN }
            openAP?.let {
                suspectedEvilTwin = SuspiciousAP(
                    ssid = ssid,
                    bssid = it.BSSID,
                    signalStrength = it.level,
                    reason = SuspiciousReason.DUPLICATE_SSID,
                    confidence = 0.9f
                )
            }
        }

        // Check for similar BSSIDs (MAC spoofing)
        for (i in matchingAPs.indices) {
            for (j in i + 1 until matchingAPs.size) {
                val bssid1 = matchingAPs[i].BSSID.uppercase()
                val bssid2 = matchingAPs[j].BSSID.uppercase()
                
                // Check if only last byte differs
                if (bssid1.dropLast(2) == bssid2.dropLast(2)) {
                    evidence.add("Similar MAC addresses detected: $bssid1 and $bssid2")
                }
            }
        }

        EvilTwinResult(
            isDetected = evidence.isNotEmpty(),
            legitimateAP = matchingAPs.maxByOrNull { it.level }?.BSSID,
            suspectedEvilTwin = suspectedEvilTwin,
            evidence = evidence
        )
    }

    /**
     * Monitors for deauthentication attacks.
     */
    fun monitorDeauthAttacks(): Flow<DeauthEvent> = flow<DeauthEvent> {
        // Note: Real deauth monitoring would require monitor mode support
        // which is not available on standard Android devices.
        // This is a placeholder for potential future implementation
        // with root access or specialized hardware.
        Timber.w("Deauth monitoring not available on standard Android devices")
    }.flowOn(Dispatchers.IO)

    // Private helper methods

    private fun determineSecurityType(scanResult: ScanResult?): WifiSecurityType {
        if (scanResult == null) return WifiSecurityType.UNKNOWN

        val capabilities = scanResult.capabilities.uppercase()
        
        return when {
            capabilities.contains("WPA3") -> {
                if (capabilities.contains("EAP")) WifiSecurityType.WPA3_ENTERPRISE
                else WifiSecurityType.WPA3_PSK
            }
            capabilities.contains("WPA2") -> {
                if (capabilities.contains("EAP")) WifiSecurityType.WPA2_ENTERPRISE
                else WifiSecurityType.WPA2_PSK
            }
            capabilities.contains("WPA") -> WifiSecurityType.WPA
            capabilities.contains("WEP") -> WifiSecurityType.WEP
            capabilities.contains("ESS") && !capabilities.contains("WPA") && !capabilities.contains("WEP") -> {
                WifiSecurityType.OPEN
            }
            else -> WifiSecurityType.UNKNOWN
        }
    }

    private fun analyzeEncryption(
        scanResult: ScanResult?,
        securityType: WifiSecurityType
    ): EncryptionAssessment {
        val capabilities = scanResult?.capabilities?.uppercase() ?: ""
        
        val cipherSuites = mutableListOf<String>()
        if (capabilities.contains("CCMP")) cipherSuites.add("AES-CCMP")
        if (capabilities.contains("TKIP")) cipherSuites.add("TKIP")
        if (capabilities.contains("GCMP")) cipherSuites.add("AES-GCMP")

        val keyManagement = when {
            capabilities.contains("SAE") -> "SAE (WPA3)"
            capabilities.contains("EAP") -> "802.1X/EAP"
            capabilities.contains("PSK") -> "PSK"
            else -> "None"
        }

        val pmfEnabled = capabilities.contains("MFPR") || capabilities.contains("MFPC")
        val isWpa3Capable = capabilities.contains("WPA3") || capabilities.contains("SAE")

        val strength = when (securityType) {
            WifiSecurityType.OPEN -> EncryptionStrength.NONE
            WifiSecurityType.WEP -> EncryptionStrength.WEAK
            WifiSecurityType.WPA -> EncryptionStrength.MODERATE
            WifiSecurityType.WPA2_PSK -> if (cipherSuites.contains("AES-CCMP")) 
                EncryptionStrength.STRONG else EncryptionStrength.MODERATE
            WifiSecurityType.WPA2_ENTERPRISE -> EncryptionStrength.STRONG
            WifiSecurityType.WPA3_PSK, WifiSecurityType.WPA3_ENTERPRISE -> 
                EncryptionStrength.VERY_STRONG
            else -> EncryptionStrength.WEAK
        }

        val weaknesses = mutableListOf<String>()
        if (cipherSuites.contains("TKIP") && !cipherSuites.contains("AES-CCMP")) {
            weaknesses.add("Using only TKIP cipher (vulnerable to attacks)")
        }
        if (!pmfEnabled && securityType != WifiSecurityType.WPA3_PSK && 
            securityType != WifiSecurityType.WPA3_ENTERPRISE) {
            weaknesses.add("Protected Management Frames (PMF) not enabled")
        }

        return EncryptionAssessment(
            securityType = securityType,
            cipherSuites = cipherSuites,
            keyManagement = keyManagement,
            pmfEnabled = pmfEnabled,
            isWpa3Capable = isWpa3Capable,
            strength = strength,
            weaknesses = weaknesses
        )
    }

    private fun detectVulnerabilities(
        scanResult: ScanResult?,
        securityType: WifiSecurityType,
        encryptionAssessment: EncryptionAssessment
    ): List<WifiVulnerability> {
        val vulnerabilities = mutableListOf<WifiVulnerability>()
        val capabilities = scanResult?.capabilities?.uppercase() ?: ""

        // Open network
        if (securityType == WifiSecurityType.OPEN) {
            vulnerabilities.add(
                WifiVulnerability(
                    type = VulnerabilityType.OPEN_NETWORK,
                    name = "Open Network",
                    description = "Network has no encryption - all traffic is transmitted in clear text",
                    severity = RiskLevel.CRITICAL,
                    mitigation = "Avoid using this network or use a VPN"
                )
            )
        }

        // WEP
        if (securityType == WifiSecurityType.WEP) {
            vulnerabilities.add(
                WifiVulnerability(
                    type = VulnerabilityType.WEAK_ENCRYPTION,
                    name = "WEP Encryption",
                    description = "WEP encryption can be cracked in minutes using freely available tools",
                    severity = RiskLevel.CRITICAL,
                    cveId = "CVE-2001-1489",
                    mitigation = "Upgrade to WPA3 or at minimum WPA2"
                )
            )
        }

        // WPA TKIP only
        if (encryptionAssessment.cipherSuites.contains("TKIP") && 
            !encryptionAssessment.cipherSuites.contains("AES-CCMP")) {
            vulnerabilities.add(
                WifiVulnerability(
                    type = VulnerabilityType.WEAK_ENCRYPTION,
                    name = "TKIP Only",
                    description = "TKIP cipher is vulnerable to packet injection attacks",
                    severity = RiskLevel.HIGH,
                    mitigation = "Enable AES-CCMP cipher on the router"
                )
            )
        }

        // WPS enabled
        if (capabilities.contains("WPS")) {
            vulnerabilities.add(
                WifiVulnerability(
                    type = VulnerabilityType.WPS_ENABLED,
                    name = "WPS Enabled",
                    description = "WiFi Protected Setup is vulnerable to brute force attacks",
                    severity = RiskLevel.MEDIUM,
                    cveId = "CVE-2011-5053",
                    mitigation = "Disable WPS on the router"
                )
            )
        }

        // No PMF on WPA2
        if (!encryptionAssessment.pmfEnabled && 
            (securityType == WifiSecurityType.WPA2_PSK || securityType == WifiSecurityType.WPA2_ENTERPRISE)) {
            vulnerabilities.add(
                WifiVulnerability(
                    type = VulnerabilityType.DEAUTH_ATTACK,
                    name = "PMF Not Enabled",
                    description = "Network is vulnerable to deauthentication attacks",
                    severity = RiskLevel.MEDIUM,
                    mitigation = "Enable Protected Management Frames (802.11w) on the router"
                )
            )
        }

        // Hidden SSID
        if (scanResult?.SSID.isNullOrEmpty()) {
            vulnerabilities.add(
                WifiVulnerability(
                    type = VulnerabilityType.HIDDEN_SSID,
                    name = "Hidden SSID",
                    description = "Hidden SSIDs provide no security benefit and can reveal client devices",
                    severity = RiskLevel.LOW,
                    mitigation = "Consider making the SSID visible"
                )
            )
        }

        return vulnerabilities
    }

    private fun generateRecommendations(
        securityType: WifiSecurityType,
        vulnerabilities: List<WifiVulnerability>
    ): List<String> {
        val recommendations = mutableListOf<String>()

        if (securityType != WifiSecurityType.WPA3_PSK && securityType != WifiSecurityType.WPA3_ENTERPRISE) {
            recommendations.add("Upgrade to WPA3 if your devices support it")
        }

        if (vulnerabilities.any { it.type == VulnerabilityType.OPEN_NETWORK }) {
            recommendations.add("Use a trusted VPN when connected to this network")
            recommendations.add("Avoid accessing sensitive information on this network")
        }

        if (vulnerabilities.any { it.type == VulnerabilityType.WPS_ENABLED }) {
            recommendations.add("Disable WPS on your router through the admin panel")
        }

        if (vulnerabilities.isEmpty()) {
            recommendations.add("Your network security is good! Keep your router firmware updated.")
        }

        return recommendations
    }

    private fun calculateOverallRisk(vulnerabilities: List<WifiVulnerability>): RiskLevel {
        if (vulnerabilities.any { it.severity == RiskLevel.CRITICAL }) return RiskLevel.CRITICAL
        if (vulnerabilities.any { it.severity == RiskLevel.HIGH }) return RiskLevel.HIGH
        if (vulnerabilities.any { it.severity == RiskLevel.MEDIUM }) return RiskLevel.MEDIUM
        if (vulnerabilities.any { it.severity == RiskLevel.LOW }) return RiskLevel.LOW
        return RiskLevel.LOW
    }

    private fun frequencyToChannel(frequency: Int): Int {
        return when {
            frequency in 2412..2472 -> (frequency - 2412) / 5 + 1
            frequency == 2484 -> 14
            frequency in 5170..5825 -> (frequency - 5170) / 5 + 34
            frequency in 5925..7125 -> (frequency - 5950) / 5 + 1 // WiFi 6E
            else -> 0
        }
    }
}
