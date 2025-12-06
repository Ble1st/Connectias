package com.ble1st.connectias.feature.security.scanner.plugins

import com.ble1st.connectias.feature.security.scanner.models.ScanConfiguration
import com.ble1st.connectias.feature.security.scanner.models.ScanType
import com.ble1st.connectias.feature.security.scanner.models.Vulnerability
import com.ble1st.connectias.feature.security.scanner.scanner.PortScanner
import com.ble1st.connectias.feature.security.scanner.scanner.ServiceDetector
import com.ble1st.connectias.feature.security.scanner.scanner.ServiceSecurityAnalyzer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import timber.log.Timber
import javax.inject.Inject

/**
 * Plugin for port scanning with service detection and security assessment.
 */
class PortScanPlugin @Inject constructor(
    private val portScanner: PortScanner,
    private val serviceDetector: ServiceDetector,
    private val securityAnalyzer: ServiceSecurityAnalyzer
) : ScannerPlugin {

    override val supportedTypes = setOf(ScanType.PORT_SCAN)

    override suspend fun scan(config: ScanConfiguration): List<Vulnerability> {
        val vulnerabilities = mutableListOf<Vulnerability>()
        
        try {
            Timber.d("Starting port scan for ${config.target}")
            
            // 1. Determine ports to scan
            val portsToScan = portScanner.getPortsToScan(
                config.portRange,
                config.portRangeCustom
            )
            
            Timber.d("Scanning ${portsToScan.size} ports")
            
            // 2. Perform port scanning
            val portScanResults = portScanner.scanPorts(
                config.target,
                portsToScan,
                config.intensity
            ).toList()
            
            val openPorts = portScanResults.filter { it.isOpen }
            Timber.d("Found ${openPorts.size} open ports")
            
            // 3. Service detection for open ports
            if (config.enableServiceDetection) {
                openPorts.forEach { portResult ->
                    try {
                        val serviceInfo = serviceDetector.detectService(
                            config.target,
                            portResult.port,
                            portResult.banner
                        )
                        
                        Timber.d("Detected service: ${serviceInfo.name} on port ${portResult.port}")
                        
                        // 4. Security assessment
                        if (config.enableSecurityAssessment) {
                            val assessment = securityAnalyzer.assessService(serviceInfo)
                            
                            // Convert assessment issues to vulnerabilities
                            assessment.issues.forEach { issue ->
                                vulnerabilities.add(
                                    issue.copy(
                                        affectedComponent = "${serviceInfo.name} on port ${portResult.port}"
                                    )
                                )
                            }
                            
                            // Add recommendations as informational vulnerabilities
                            assessment.recommendations.forEach { recommendation ->
                                vulnerabilities.add(
                                    Vulnerability(
                                        category = com.ble1st.connectias.feature.vulnerability.scanner.models.VulnerabilityCategory.CONFIGURATION,
                                        severity = recommendation.priority,
                                        name = "Recommendation: ${recommendation.title}",
                                        description = recommendation.description,
                                        impact = "Following this recommendation will improve the security of ${serviceInfo.name} on port ${portResult.port}",
                                        affectedComponent = "${serviceInfo.name} on port ${portResult.port}",
                                        remediation = recommendation.implementationSteps.joinToString("\n") { "• $it" }
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error detecting service on port ${portResult.port}")
                    }
                }
            } else {
                // If service detection is disabled, just report open ports as potential issues
                openPorts.forEach { portResult ->
                    vulnerabilities.add(
                        Vulnerability(
                            category = com.ble1st.connectias.feature.vulnerability.scanner.models.VulnerabilityCategory.NETWORK,
                            severity = com.ble1st.connectias.feature.vulnerability.scanner.models.Severity.INFO,
                            name = "Open Port: ${portResult.port}",
                            description = "Port ${portResult.port} is open on ${config.target}",
                            impact = "Open ports may expose services that need to be secured. Enable service detection for detailed analysis.",
                            affectedComponent = "Port ${portResult.port}",
                            remediation = "Review if this port should be open. If yes, ensure the service is properly secured."
                        )
                    )
                }
            }
            
            Timber.d("Port scan completed. Found ${vulnerabilities.size} vulnerabilities")
        } catch (e: Exception) {
            Timber.e(e, "Error during port scan")
            vulnerabilities.add(
                Vulnerability(
                    category = com.ble1st.connectias.feature.vulnerability.scanner.models.VulnerabilityCategory.NETWORK,
                    severity = com.ble1st.connectias.feature.vulnerability.scanner.models.Severity.HIGH,
                    name = "Port Scan Error",
                    description = "Failed to complete port scan: ${e.message}",
                    impact = "Unable to assess port security",
                    affectedComponent = config.target,
                    remediation = "Check network connectivity and target availability"
                )
            )
        }
        
        return vulnerabilities
    }
}

