package com.ble1st.connectias.common.ui.strings

import androidx.compose.runtime.compositionLocalOf

// Interface defining all translatable strings in the app
interface AppStringDictionary {
    val appName: String
    val dashboardTitle: String
    val networkTitle: String
    val securityTitle: String
    val settingsTitle: String
    val actionScan: String
    val actionCancel: String
    val statusOnline: String
    val statusOffline: String
    val alertError: String
    val alertWarning: String
    
    // Network Dashboard
    val navNetworkDashboard: String
    val retryAll: String
    val networkAnalysisTitle: String
    val wifiNetworksTitle: String
    val lanDevicesTitle: String
    val connectionType: String
    val connected: String
    val yes: String
    val no: String
    val gateway: String
    val dnsServers: String
    val notAvailable: String
    val noWifiNetworks: String
    val noLanDevices: String
    
    // Topology
    val networkTopologyTitle: String
    val actionRefresh: String
    val actionReset: String
    val actionRetry: String
    val actionBuildTopology: String
    val topologyStatsTitle: String
    val statsNodes: String
    val statsEdges: String
    val topologyHint: String
    val topologyEmptyStateTitle: String
    val topologyEmptyStateDesc: String
    
    // Security
    val securityOverviewTitle: String
    val securityScore: String
    val threatLevel: String
    val raspStatus: String
    val vulnStatus: String
    val privacyStatus: String
    val threatDetected: String
    val secure: String
    val systemIntegrity: String
    val appAnalysis: String

    // Hardware
    val hardwareMonitorTitle: String
    val batteryStatus: String
    val charging: String
    val discharging: String
    val voltage: String
    val temperature: String
    val capacity: String
    val health: String
    val connectivityTitle: String
    val bluetoothStatus: String
    val nfcStatus: String
    val enabled: String
    val disabled: String

    // System Tools
    val logViewerTitle: String

    // Data Leakage Screen
    val dataLeakageCheckTitle: String
    val clipboardMonitorTitle: String
    val monitoringActive: String
    val stopMonitoring: String
    val startMonitoring: String
    val sensitiveDataAnalysisTitle: String
    val enterTextToAnalyze: String
    val analyzeAction: String
    val checkAppsClipboardAccess: String
    val appsWithClipboardAccess: String
    val latestClipboardEntry: String
    val criticalSensitivity: String
    val highSensitivity: String
    val mediumSensitivity: String
    val lowSensitivity: String
    val noSensitiveDataDetected: String
    val systemAppLabel: String
}

// Default implementation for Standard Theme
object StandardDictionary : AppStringDictionary {
    override val appName = "Connectias"
    override val dashboardTitle = "Dashboard"
    override val networkTitle = "Network Manager"
    override val securityTitle = "Security Center"
    override val settingsTitle = "Settings"
    override val actionScan = "Start Scan"
    override val actionCancel = "Cancel"
    override val statusOnline = "Connected"
    override val statusOffline = "Disconnected"
    override val alertError = "Error"
    override val alertWarning = "Warning"
    
    // Network Dashboard
    override val navNetworkDashboard = "Network Dashboard"
    override val retryAll = "Retry All"
    override val networkAnalysisTitle = "Network Analysis"
    override val wifiNetworksTitle = "Wi-Fi Networks"
    override val lanDevicesTitle = "LAN Devices"
    override val connectionType = "Connection Type"
    override val connected = "Connected"
    override val yes = "Yes"
    override val no = "No"
    override val gateway = "Gateway"
    override val dnsServers = "DNS Servers"
    override val notAvailable = "N/A"
    override val noWifiNetworks = "No Wi-Fi networks found"
    override val noLanDevices = "No devices found"

    // Topology
    override val networkTopologyTitle = "Network Topology"
    override val actionRefresh = "Refresh"
    override val actionReset = "Reset"
    override val actionRetry = "Retry"
    override val actionBuildTopology = "Build Topology"
    override val topologyStatsTitle = "Topology Statistics"
    override val statsNodes = "Nodes"
    override val statsEdges = "Edges"
    override val topologyHint = "Tap a node to see details"
    override val topologyEmptyStateTitle = "Network topology requires discovered devices."
    override val topologyEmptyStateDesc = "Use the Network Dashboard to discover devices first."

    // Security
    override val securityOverviewTitle = "Security Overview"
    override val securityScore = "Security Score"
    override val threatLevel = "Threat Level"
    override val raspStatus = "RASP Protection"
    override val vulnStatus = "Vulnerability Scan"
    override val privacyStatus = "Privacy Analysis"
    override val threatDetected = "Threat Detected"
    override val secure = "Secure"
    override val systemIntegrity = "System Integrity"
    override val appAnalysis = "App Analysis"

    // Hardware
    override val hardwareMonitorTitle = "Device Monitor"
    override val batteryStatus = "Battery Status"
    override val charging = "Charging"
    override val discharging = "Discharging"
    override val voltage = "Voltage"
    override val temperature = "Temperature"
    override val capacity = "Capacity"
    override val health = "Health"
    override val connectivityTitle = "Connectivity"
    override val bluetoothStatus = "Bluetooth"
    override val nfcStatus = "NFC"
    override val enabled = "Enabled"
    override val disabled = "Disabled"

    // System Tools
    override val logViewerTitle = "Log Viewer"

    // Data Leakage Screen
    override val dataLeakageCheckTitle = "Data Leakage Check"
    override val clipboardMonitorTitle = "Clipboard Monitor"
    override val monitoringActive = "Monitoring Active"
    override val stopMonitoring = "Stop Monitoring"
    override val startMonitoring = "Start Monitoring"
    override val sensitiveDataAnalysisTitle = "Sensitive Data Analysis"
    override val enterTextToAnalyze = "Enter text to analyze"
    override val analyzeAction = "Analyze"
    override val checkAppsClipboardAccess = "Check Apps with Clipboard Access"
    override val appsWithClipboardAccess = "Apps with Clipboard Access: "
    override val latestClipboardEntry = "Latest Clipboard Entry"
    override val criticalSensitivity = "Critical Sensitivity"
    override val highSensitivity = "High Sensitivity"
    override val mediumSensitivity = "Medium Sensitivity"
    override val lowSensitivity = "Low Sensitivity"
    override val noSensitiveDataDetected = "No sensitive data detected"
    override val systemAppLabel = "System App"
}

// Implementation for Adeptus Mechanicus Theme
object AdeptusDictionary : AppStringDictionary {
    override val appName = "Machine Spirit Interface"
    override val dashboardTitle = "Communion Interface"
    override val networkTitle = "Noosphere Communion"
    override val securityTitle = "Purity Seals"
    override val settingsTitle = "Rites of Configuration"
    override val actionScan = "Initiate Rite of Scanning"
    override val actionCancel = "Abort Rite"
    override val statusOnline = "Communion Established"
    override val statusOffline = "Communion Lost"
    override val alertError = "Heresy Detected"
    override val alertWarning = "Spirit Displeased"
    
    // Network Dashboard
    override val navNetworkDashboard = "Noosphere Dashboard"
    override val retryAll = "Re-Attempt Rites"
    override val networkAnalysisTitle = "Noosphere Analysis"
    override val wifiNetworksTitle = "Etheric Networks"
    override val lanDevicesTitle = "Local Machine Spirits"
    override val connectionType = "Communion Type"
    override val connected = "Communng"
    override val yes = "Affirmative"
    override val no = "Negative"
    override val gateway = "Portal"
    override val dnsServers = "Knowledge Spirits"
    override val notAvailable = "Unknown"
    override val noWifiNetworks = "No etheric signals detected"
    override val noLanDevices = "No spirits in proximity"

    // Topology
    override val networkTopologyTitle = "Noosphere Topology"
    override val actionRefresh = "Refresh Communion"
    override val actionReset = "Purge Cache"
    override val actionRetry = "Re-Attempt"
    override val actionBuildTopology = "Construct Map"
    override val topologyStatsTitle = "Map Statistics"
    override val statsNodes = "Spirits"
    override val statsEdges = "Connections"
    override val topologyHint = "Commune with a spirit to see details"
    override val topologyEmptyStateTitle = "Topology requires prior communion."
    override val topologyEmptyStateDesc = "Perform the Rite of Discovery first."

    // Security
    override val securityOverviewTitle = "Purity Status"
    override val securityScore = "Purity Quotient"
    override val threatLevel = "Corruption Level"
    override val raspStatus = "Active Purity Seals"
    override val vulnStatus = "Heretic Scanning"
    override val privacyStatus = "Sanctity Analysis"
    override val threatDetected = "Heresy Detected"
    override val secure = "Sanctified"
    override val systemIntegrity = "Machine Spirit Integrity"
    override val appAnalysis = "Litany of Apps"

    // Hardware
    override val hardwareMonitorTitle = "Vital Signs Monitor"
    override val batteryStatus = "Machine Spirit Vitality"
    override val charging = "Rite of Recharging"
    override val discharging = "Energy Consumption"
    override val voltage = "Sacred Current"
    override val temperature = "Thermal Rite Status"
    override val capacity = "Power Cell Capacity"
    override val health = "Spirit Integrity"
    override val connectivityTitle = "Noosphere Uplinks"
    override val bluetoothStatus = "Short-Range Vox"
    override val nfcStatus = "Touch Communion"
    override val enabled = "Active"
    override val disabled = "Dormant"

    // System Tools
    override val logViewerTitle = "Log Compendium"

    // Data Leakage Screen
    override val dataLeakageCheckTitle = "Data Corruption Probe"
    override val clipboardMonitorTitle = "Cogitator Buffer Monitor"
    override val monitoringActive = "Auspex Active"
    override val stopMonitoring = "Cease Auspex"
    override val startMonitoring = "Activate Auspex"
    override val sensitiveDataAnalysisTitle = "Prohibited Data Analysis"
    override val enterTextToAnalyze = "Input Data for Analysis"
    override val analyzeAction = "Analyze Data-Stream"
    override val checkAppsClipboardAccess = "Audit Cogitator Access Protocols"
    override val appsWithClipboardAccess = "Cogitators with Protocol Access: "
    override val latestClipboardEntry = "Last Cogitator Buffer Record"
    override val criticalSensitivity = "Critical Data Hazard"
    override val highSensitivity = "High Data Sensitivity"
    override val mediumSensitivity = "Medium Data Sensitivity"
    override val lowSensitivity = "Low Data Sensitivity"
    override val noSensitiveDataDetected = "No Prohibited Data Detected"
    override val systemAppLabel = "System Cogitator"
}

// CompositionLocal to provide strings to the UI tree
val LocalAppStrings = compositionLocalOf<AppStringDictionary> { StandardDictionary }