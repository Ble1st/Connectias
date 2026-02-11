package com.ble1st.connectias.common.ui.strings

/**
 * String mappings for theme-dependent terminology.
 * Maps standard strings to Adeptus Mechanicus variants.
 * Based on design guidelines from ADEPTUS_MECHANICUS_THEME.md
 */
object StringMappings {
    /**
     * Mapping table: Standard String -> Adeptus Mechanicus String
     */
    private val mappings = mapOf(
        // General Terms
        "App" to "Machine Spirit Interface",
        "System" to "Machine Spirit",
        "Device" to "Machine Spirit",
        "Function" to "Rite",
        "Scan" to "Rite of Scanning",
        "Start" to "INITIATE",
        "Stop" to "TERMINATE",
        "Status" to "Machine Spirit Status",
        "Success" to "Machine Spirit Pleased",
        "Error" to "Heresy Detected",
        "Warning" to "Attention Required",
        "Settings" to "Rites of Configuration",
        "Dashboard" to "Communion Interface",
        
        // Security
        "Security" to "Purity Seals",
        "Security Check" to "Purity Seal Verification",
        "Root Detection" to "Root Detection Seal",
        "Debugger Protection" to "Debugger Protection Seal",
        "Tamper Detection" to "Tamper Protection Seal",
        "Threat" to "Heresy",
        "Secure" to "Verified",
        "Vulnerable" to "Corrupted",
        
        // Network
        "Network" to "Network Communion",
        "Network Scan" to "Rite of Network Scanning",
        "Connection" to "Communion",
        "Disconnected" to "Communion Lost",
        "Online" to "Online",
        "Offline" to "Offline",
        "IP Address" to "Sacred Address",
        
        // Hardware
        "Battery" to "Power Cell",
        "Battery Status" to "Machine Spirit Vitality",
        "Charging" to "Rite of Recharging",
        "Voltage" to "Sacred Current",
        "Temperature" to "Thermal Rite Status",
        "Capacity" to "Power Cell Capacity",
        "Current" to "Energy Flow Rate",
        "Health" to "Spirit Integrity",
        "USB Device" to "USB Machine Spirit",
        "Storage" to "Sacred Storage",
        
        // Authentication
        "Login" to "Rite of Authentication",
        "Password" to "Sacred Passphrase",
        "Biometric" to "Biometric Verification Rite",
        "Unlock" to "Initiate Access Protocol",
        "Access Granted" to "Machine Spirit Pleased",
        "Access Denied" to "Machine Spirit Refuses",
        "Authentication" to "Authentication Rite",
        
        // Privacy
        "Privacy" to "Privacy Seals",
        "Privacy Score" to "Privacy Sanctity Score",
        "Data" to "Sacred Data",
        "Encrypted" to "Sanctified",
        "Leak" to "Corruption",
        
        // Actions
        "Refresh" to "REFRESH COMMUNION",
        "Analyze" to "ANALYZE",
        "View" to "VIEW",
        "Export" to "EXPORT MANIFEST",
        "Save" to "SAVE CONFIGURATION RITES",
        "Delete" to "PURGE",
        "Cancel" to "ABORT RITE",
        
        // Status Messages
        "Loading..." to "Communing with Machine Spirit...",
        "Scanning..." to "Rite of Scanning in progress...",
        "Complete" to "Rite Completed",
        "No data" to "No communion established",
        "Ready" to "Ready for communion",
        
        // Common UI Elements
        "Close" to "Close",
        "OK" to "OK",
        "Yes" to "Yes",
        "No" to "No",
        "Confirm" to "CONFIRM",
        "Reset" to "RESET",
        "Reset All Settings" to "PURGE ALL CONFIGURATION RITES",
        "Reset Settings?" to "PURGE CONFIGURATION RITES?",
        "This will reset all settings to their default values. This action cannot be undone." to "This will purge all configuration rites to their default state. This action cannot be undone.",
        
        // Settings
        "Theme" to "Theme",
        "Light" to "Light",
        "Dark" to "Dark",
        "System Default" to "System Default",
        "Standard" to "Standard",
        "Adeptus Mechanicus" to "Adeptus Mechanicus",
        "Dynamic Color" to "Dynamic Color",
        "Use Material You dynamic colors (Android 12+)" to "Use Material You dynamic colors (Android 12+)",
        "Auto Lock" to "Auto Lock",
        "Automatically lock the app after inactivity" to "Automatically lock the app after inactivity",
        "RASP Logging" to "RASP Logging",
        "Enable logging for Runtime Application Self-Protection" to "Enable logging for Runtime Application Self-Protection",
        "DNS Server" to "DNS Server",
        "Scan Timeout" to "Scan Timeout",
        "Clipboard Auto-Clear" to "Clipboard Auto-Clear",
        "Automatically clear clipboard after a delay" to "Automatically clear clipboard after a delay",
        "Logging Level" to "Logging Level",
        "Advanced" to "Advanced",
        "Appearance" to "Appearance",
        
        // Network Scanner
        "Enter a host and optional ports to start scanning." to "Enter a host and optional ports to initiate scanning.",
        "Port Scan" to "Rite of Port Scanning",
        "Web Security" to "Web Security Rites",
        "Configure" to "CONFIGURE",
        "Start Scan" to "INITIATE SCAN",
        "Scanning..." to "Rite of Scanning in progress...",
        
        // USB
        "USB Devices" to "USB Machine Spirits",
        "No USB devices found" to "No USB machine spirits found",
        "Connect a USB device to see it here" to "Connect a USB machine spirit to see it here",
        "USB Permission Required" to "USB Permission Required",
        "The app needs permission to access this USB device:" to "The app needs permission to access this USB machine spirit:",
        "Grant" to "GRANT",
        "Deny" to "DENY",
        "Unknown Device" to "Unknown Machine Spirit",
        "Unknown Manufacturer" to "Unknown Manufacturer",
        "View Details" to "VIEW DETAILS",
        "USB Device Connected" to "USB Machine Spirit Connected",
        "Available Actions:" to "Available Rites:",
        "View Device Details" to "View Machine Spirit Details",

        // Security Dashboard
        "Security Dashboard" to "PURITY SEALS",
        "Security Check Passed" to "PURITY SEAL VERIFIED - MACHINE SPIRIT PLEASED",
        "Verified" to "VERIFIED",
        "Monitoring" to "MONITORING",
        "Heresy Detected" to "HERESY DETECTED",
        
        // Vulnerability Scanner
        "Fast" to "Fast",
        "Balanced" to "Balanced",
        "Deep" to "Deep",
        "Common" to "Common",
        "Standard" to "Standard",
        "Full" to "Full",
        "Custom Range (e.g., 1-1024)" to "Custom Range (e.g., 1-1024)",
        "Optional: Specify custom port range" to "Optional: Specify custom port range",
        
        // Device Info
        "Device Info" to "Machine Spirit Information",
        
        // Privacy
        "Device Fingerprint" to "Machine Spirit Fingerprint",
        "Generate Fingerprint" to "GENERATE FINGERPRINT",
        "All" to "All",
        "Private DNS" to "Private DNS",
        "Domain" to "Domain",
        "Query" to "QUERY",
        
        // Certificate
        "Signature Verifier" to "Signature Verifier",
        "Package Name" to "Package Name",
        "Verify" to "VERIFY",
        "This App" to "This App",
        "Expired" to "Expired",
        "Certificate Generator" to "Certificate Generator",
        "Common Name (CN)" to "Common Name (CN)"
    )
    
    /**
     * Gets the Adeptus Mechanicus variant of a standard string.
     * @param standard The standard string
     * @return The Adeptus Mechanicus variant, or the original string if no mapping exists
     */
    fun getAdeptusMechanicusVariant(standard: String): String {
        return mappings[standard] ?: standard
    }

}

