@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package com.ble1st.connectias.core.security.ssl

import kotlinx.serialization.Serializable

/**
 * Represents a certificate pin for SSL/TLS verification.
 *
 * @property hostname The hostname to pin (supports wildcards like *.example.com)
 * @property pins List of SHA-256 pin hashes in base64 format
 * @property includeSubdomains Whether to include subdomains in pinning
 * @property expirationDate Optional expiration date for the pin (epoch milliseconds)
 */
@Serializable
data class CertificatePin(
    val hostname: String,
    val pins: List<String>,
    val includeSubdomains: Boolean = true,
    val expirationDate: Long? = null
) {
    /**
     * Checks if this pin has expired.
     */
    fun isExpired(): Boolean {
        return expirationDate?.let { System.currentTimeMillis() > it } ?: false
    }

    /**
     * Checks if the given hostname matches this pin.
     */
    fun matchesHostname(host: String): Boolean {
        if (hostname == host) return true
        
        if (includeSubdomains && host.endsWith(".$hostname")) return true
        
        // Wildcard matching (*.example.com)
        if (hostname.startsWith("*.")) {
            val baseDomain = hostname.substring(2)
            if (host == baseDomain || host.endsWith(".$baseDomain")) return true
        }
        
        return false
    }

    companion object {
        /**
         * Creates a pin with SHA-256 hash.
         */
        fun sha256(hostname: String, vararg pins: String): CertificatePin {
            return CertificatePin(
                hostname = hostname,
                pins = pins.map { "sha256/$it" }
            )
        }
    }
}

/**
 * Result of a certificate pin validation.
 */
sealed class PinValidationResult {
    /**
     * The certificate chain is valid and matches a pin.
     */
    data object Valid : PinValidationResult()

    /**
     * The certificate chain does not match any pins.
     */
    data object Invalid : PinValidationResult()

    /**
     * No pins are configured for the hostname.
     */
    data object NoPinsConfigured : PinValidationResult()

    /**
     * The configured pin has expired.
     */
    data object PinExpired : PinValidationResult()

    /**
     * An error occurred during validation.
     */
    data object Error : PinValidationResult()

}
