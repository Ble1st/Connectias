package com.ble1st.connectias.feature.security.scanner.data

import com.ble1st.connectias.feature.security.scanner.models.SecurityRecommendation
import com.ble1st.connectias.feature.security.scanner.models.Severity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Knowledge base for service security information.
 * Contains service definitions, known vulnerabilities, and best practices.
 */
@Singleton
class ServiceKnowledgeBase @Inject constructor() {

    /**
     * Service definition with security information.
     */
    data class ServiceDefinition(
        val name: String,
        val defaultPorts: List<Int>,
        val secureConfigurations: List<String>,
        val insecureConfigurations: List<String>,
        val knownVulnerabilities: Map<String, List<String>>, // version -> CVE list
        val bestPractices: List<String>
    )

    private val serviceDefinitions = mutableMapOf<String, ServiceDefinition>()

    init {
        initializeServiceDefinitions()
    }

    /**
     * Gets service definition by name.
     */
    fun getServiceDefinition(serviceName: String): ServiceDefinition? {
        return serviceDefinitions[serviceName.lowercase()]
    }

    /**
     * Gets security recommendations for a service.
     */
    fun getRecommendations(serviceName: String, version: String?): List<SecurityRecommendation> {
        val definition = getServiceDefinition(serviceName) ?: return emptyList()
        val recommendations = mutableListOf<SecurityRecommendation>()

        // Check for version-specific vulnerabilities
        if (version != null) {
            val cves = definition.knownVulnerabilities[version] ?: emptyList()
            if (cves.isNotEmpty()) {
                recommendations.add(
                    SecurityRecommendation(
                        title = "Update $serviceName to latest version",
                        description = "Version $version has known vulnerabilities: ${cves.joinToString(", ")}",
                        priority = Severity.HIGH,
                        implementationSteps = listOf(
                            "Check for available updates",
                            "Review changelog for security fixes",
                            "Apply updates in a test environment first",
                            "Deploy updates during maintenance window"
                        ),
                        affectedService = serviceName
                    )
                )
            }
        }

        // Add best practices as recommendations
        definition.bestPractices.forEach { practice ->
            recommendations.add(
                SecurityRecommendation(
                    title = practice,
                    description = "Follow this best practice for $serviceName",
                    priority = Severity.MEDIUM,
                    affectedService = serviceName
                )
            )
        }

        return recommendations
    }

    /**
     * Checks for known vulnerabilities in a service version.
     */
    fun checkVersionVulnerabilities(serviceName: String, version: String): List<String> {
        val definition = getServiceDefinition(serviceName) ?: return emptyList()
        return definition.knownVulnerabilities[version] ?: emptyList()
    }

    /**
     * Initializes service definitions with common services.
     */
    private fun initializeServiceDefinitions() {
        // SSH
        serviceDefinitions["ssh"] = ServiceDefinition(
            name = "SSH",
            defaultPorts = listOf(22),
            secureConfigurations = listOf(
                "Disable root login",
                "Use key-based authentication",
                "Disable password authentication",
                "Use strong cipher suites",
                "Limit login attempts"
            ),
            insecureConfigurations = listOf(
                "Root login enabled",
                "Password authentication only",
                "Weak cipher suites",
                "No login attempt limits"
            ),
            knownVulnerabilities = mapOf(
                "7.4" to listOf("CVE-2016-10009"),
                "8.0" to listOf("CVE-2019-6111", "CVE-2019-6110")
            ),
            bestPractices = listOf(
                "Disable root login (PermitRootLogin no)",
                "Use key-based authentication",
                "Change default port from 22",
                "Use fail2ban for brute force protection",
                "Keep SSH server updated"
            )
        )

        // FTP
        serviceDefinitions["ftp"] = ServiceDefinition(
            name = "FTP",
            defaultPorts = listOf(21),
            secureConfigurations = listOf(
                "Use FTPS (FTP over SSL/TLS)",
                "Disable anonymous access",
                "Use strong passwords",
                "Enable logging"
            ),
            insecureConfigurations = listOf(
                "Plain FTP (no encryption)",
                "Anonymous access enabled",
                "Weak passwords",
                "No logging"
            ),
            knownVulnerabilities = emptyMap(),
            bestPractices = listOf(
                "Use FTPS or SFTP instead of plain FTP",
                "Disable anonymous access",
                "Use strong authentication",
                "Enable connection logging",
                "Consider migrating to SFTP"
            )
        )

        // HTTP/HTTPS
        serviceDefinitions["http"] = ServiceDefinition(
            name = "HTTP",
            defaultPorts = listOf(80, 8080),
            secureConfigurations = listOf(
                "Redirect to HTTPS",
                "Use HSTS header",
                "Implement CSP",
                "Use secure cookies"
            ),
            insecureConfigurations = listOf(
                "No HTTPS redirect",
                "Missing security headers",
                "HTTP only (no HTTPS)"
            ),
            knownVulnerabilities = emptyMap(),
            bestPractices = listOf(
                "Always redirect HTTP to HTTPS",
                "Implement HSTS header",
                "Use Content-Security-Policy",
                "Set secure cookie flags",
                "Keep web server updated"
            )
        )

        serviceDefinitions["https"] = ServiceDefinition(
            name = "HTTPS",
            defaultPorts = listOf(443, 8443),
            secureConfigurations = listOf(
                "TLS 1.2 or higher",
                "Strong cipher suites",
                "Valid SSL certificate",
                "HSTS header enabled"
            ),
            insecureConfigurations = listOf(
                "TLS 1.0 or 1.1",
                "Weak cipher suites",
                "Expired certificate",
                "Self-signed certificate"
            ),
            knownVulnerabilities = emptyMap(),
            bestPractices = listOf(
                "Use TLS 1.2 or 1.3 only",
                "Disable weak cipher suites",
                "Use valid SSL certificate from trusted CA",
                "Enable HSTS header",
                "Monitor certificate expiration"
            )
        )

        // MySQL
        serviceDefinitions["mysql"] = ServiceDefinition(
            name = "MySQL",
            defaultPorts = listOf(3306),
            secureConfigurations = listOf(
                "Strong root password",
                "Remove default users",
                "Limit network access",
                "Enable SSL connections"
            ),
            insecureConfigurations = listOf(
                "Default/weak password",
                "Default users present",
                "Accessible from internet",
                "No SSL encryption"
            ),
            knownVulnerabilities = mapOf(
                "5.7" to listOf("CVE-2020-14812", "CVE-2020-14765"),
                "8.0" to listOf("CVE-2021-3711")
            ),
            bestPractices = listOf(
                "Change default root password",
                "Remove anonymous users",
                "Restrict network access (bind to localhost if possible)",
                "Enable SSL for remote connections",
                "Keep MySQL updated",
                "Use least privilege principle for database users"
            )
        )

        // PostgreSQL
        serviceDefinitions["postgresql"] = ServiceDefinition(
            name = "PostgreSQL",
            defaultPorts = listOf(5432),
            secureConfigurations = listOf(
                "Strong passwords",
                "SSL connections enabled",
                "Network access restricted",
                "Regular backups"
            ),
            insecureConfigurations = listOf(
                "Weak passwords",
                "No SSL encryption",
                "Accessible from internet",
                "No authentication"
            ),
            knownVulnerabilities = mapOf(
                "12" to listOf("CVE-2020-14349"),
                "13" to listOf("CVE-2021-3393")
            ),
            bestPractices = listOf(
                "Use strong passwords",
                "Enable SSL for remote connections",
                "Restrict network access",
                "Keep PostgreSQL updated",
                "Use pg_hba.conf for access control",
                "Regular security updates"
            )
        )

        // RDP
        serviceDefinitions["rdp"] = ServiceDefinition(
            name = "RDP",
            defaultPorts = listOf(3389),
            secureConfigurations = listOf(
                "Network Level Authentication (NLA)",
                "Strong passwords",
                "Change default port",
                "Use VPN instead"
            ),
            insecureConfigurations = listOf(
                "NLA disabled",
                "Weak passwords",
                "Default port 3389",
                "Exposed to internet"
            ),
            knownVulnerabilities = mapOf(
                "Windows" to listOf("CVE-2019-0708", "CVE-2021-34527")
            ),
            bestPractices = listOf(
                "Enable Network Level Authentication",
                "Use strong passwords or certificate authentication",
                "Change default port from 3389",
                "Use VPN instead of exposing RDP to internet",
                "Enable RDP logging",
                "Keep Windows updated"
            )
        )

        // VNC
        serviceDefinitions["vnc"] = ServiceDefinition(
            name = "VNC",
            defaultPorts = listOf(5900),
            secureConfigurations = listOf(
                "Use VNC over SSH tunnel",
                "Strong passwords",
                "Change default port",
                "Use encryption"
            ),
            insecureConfigurations = listOf(
                "Plain VNC (no encryption)",
                "Weak passwords",
                "Default port 5900",
                "Exposed to internet"
            ),
            knownVulnerabilities = emptyMap(),
            bestPractices = listOf(
                "Use VNC over SSH tunnel",
                "Use strong passwords",
                "Change default port",
                "Never expose VNC directly to internet",
                "Consider using alternatives like RDP with VPN"
            )
        )

        // SMTP
        serviceDefinitions["smtp"] = ServiceDefinition(
            name = "SMTP",
            defaultPorts = listOf(25, 587, 465),
            secureConfigurations = listOf(
                "Use SMTP over TLS (port 587)",
                "Require authentication",
                "Enable SPF/DKIM/DMARC",
                "Rate limiting"
            ),
            insecureConfigurations = listOf(
                "Plain SMTP (no encryption)",
                "No authentication",
                "Open relay",
                "No rate limiting"
            ),
            knownVulnerabilities = emptyMap(),
            bestPractices = listOf(
                "Use SMTP over TLS (port 587) or SMTPS (port 465)",
                "Require authentication for sending",
                "Configure SPF, DKIM, and DMARC records",
                "Implement rate limiting",
                "Disable open relay",
                "Keep mail server updated"
            )
        )
    }
}

