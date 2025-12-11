package com.ble1st.connectias.feature.network.model

object PortServiceRegistry {
    private val commonPorts = mapOf(
        20 to "FTP Data",
        21 to "FTP",
        22 to "SSH",
        23 to "Telnet",
        25 to "SMTP",
        53 to "DNS",
        67 to "DHCP Server",
        68 to "DHCP Client",
        80 to "HTTP",
        110 to "POP3",
        123 to "NTP",
        135 to "RPC",
        137 to "NetBIOS Name",
        138 to "NetBIOS Datagram",
        139 to "NetBIOS Session",
        143 to "IMAP",
        161 to "SNMP",
        389 to "LDAP",
        443 to "HTTPS",
        445 to "SMB",
        465 to "SMTPS",
        514 to "Syslog",
        587 to "Submission",
        631 to "IPP/Printing",
        993 to "IMAPS",
        995 to "POP3S",
        1723 to "PPTP",
        1883 to "MQTT",
        1900 to "SSDP/UPnP",
        3306 to "MySQL",
        3389 to "RDP",
        4433 to "HTTPS Alt",
        5432 to "PostgreSQL",
        5671 to "AMQP TLS",
        5672 to "AMQP",
        5900 to "VNC",
        5984 to "CouchDB",
        5985 to "WinRM",
        6379 to "Redis",
        8000 to "HTTP Alt",
        8008 to "HTTP Alt",
        8080 to "HTTP Proxy",
        8081 to "HTTP Alt",
        8443 to "HTTPS Alt",
        9000 to "Debug/Custom",
        9200 to "Elasticsearch",
        10000 to "Backup/Custom"
    )

    fun serviceFor(port: Int): String? = commonPorts[port]
}

fun securityTypeFromCapabilities(capabilities: String): SecurityType {
    val caps = capabilities.uppercase()
    return when {
        "WPA3" in caps -> SecurityType.WPA3
        "WPA2" in caps -> SecurityType.WPA2
        "WPA" in caps -> SecurityType.WPA
        "WEP" in caps -> SecurityType.WEP
        "ESS" in caps || "OPEN" in caps -> SecurityType.OPEN
        else -> SecurityType.UNKNOWN
    }
}

fun wifiChannelFromFrequency(frequency: Int): Int? = when (frequency) {
    in 2412..2484 -> ((frequency - 2407) / 5).takeIf { it in 1..14 }
    in 5000..5900 -> ((frequency - 5000) / 5).takeIf { it in 1..200 }
    else -> null
}

fun deviceTypeFromHostname(hostname: String?): DeviceType {
    val host = hostname?.lowercase()?.trim().orEmpty()
    return when {
        host.contains("router") || host.contains("gateway") -> DeviceType.ROUTER
        host.contains("phone") || host.contains("android") || host.contains("iphone") -> DeviceType.PHONE
        host.contains("tv") || host.contains("chromecast") || host.contains("roku") -> DeviceType.IOT
        host.contains("printer") || host.contains("print") -> DeviceType.PRINTER
        host.contains("nas") || host.contains("storage") -> DeviceType.SERVER
        host.contains("pc") || host.contains("desktop") || host.contains("laptop") -> DeviceType.COMPUTER
        else -> DeviceType.UNKNOWN
    }
}
