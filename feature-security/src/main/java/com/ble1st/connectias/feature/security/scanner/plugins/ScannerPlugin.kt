package com.ble1st.connectias.feature.security.scanner.plugins

import com.ble1st.connectias.feature.security.scanner.models.ScanConfiguration
import com.ble1st.connectias.feature.security.scanner.models.ScanType
import com.ble1st.connectias.feature.security.scanner.models.Vulnerability

/**
 * Common interface for all vulnerability scanner plugins.
 */
interface ScannerPlugin {
    /**
     * The set of scan types this plugin supports.
     */
    val supportedTypes: Set<ScanType>

    /**
     * Executes the scan logic.
     * @param config Configuration for the scan.
     * @return A list of detected vulnerabilities.
     */
    suspend fun scan(config: ScanConfiguration): List<Vulnerability>
}