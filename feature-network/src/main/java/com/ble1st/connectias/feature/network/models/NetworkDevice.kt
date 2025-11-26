package com.ble1st.connectias.feature.network.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a device discovered on the local network.
 * Validation is performed at the boundary (provider layer), not in the constructor.
 */
@Parcelize
data class NetworkDevice(
    val ipAddress: String,
    val hostname: String,
    val macAddress: String?, // Optional, may not be available without root
    val deviceType: DeviceType,
    val isReachable: Boolean
) : Parcelable {
    companion object {
        /**
         * Factory method that validates IP address before creating NetworkDevice.
         * 
         * Note: Validation uses strict IPv4/IPv6 parsers first to prevent DNS resolution
         * for ambiguous hostnames (e.g., "cafe.babe", "dead:beef"). Only after strict
         * validation passes, InetAddress.getByName() is used to get canonical form.
         * The input is compared with canonical form to detect if DNS resolution occurred.
         * 
         * @return Result.success(NetworkDevice) if valid, Result.failure(Exception) otherwise
         */
        fun create(
            ipAddress: String,
            hostname: String,
            macAddress: String?,
            deviceType: DeviceType,
            isReachable: Boolean
        ): Result<NetworkDevice> {
            return if (isValidIpAddress(ipAddress)) {
                Result.success(
                    NetworkDevice(
                        ipAddress = ipAddress,
                        hostname = hostname,
                        macAddress = macAddress,
                        deviceType = deviceType,
                        isReachable = isReachable
                    )
                )
            } else {
                Result.failure(
                    IllegalArgumentException("Invalid IP address: $ipAddress (must be a valid IPv4 or IPv6 address)")
                )
            }
        }
        
        /**
         * Validates IP address using strict parsing without DNS resolution.
         * 
         * First validates syntax using strict IPv4/IPv6 parsers that do not perform DNS resolution.
         * Only if strict validation passes, uses InetAddress.getByName() to get canonical form.
         * Compares input with canonical form to detect if DNS resolution occurred.
         * 
         * This approach prevents DNS resolution for ambiguous hostnames like "cafe.babe" or "dead:beef"
         * by rejecting them at the strict validation stage.
         */
        private fun isValidIpAddress(ip: String): Boolean {
            return try {
                // Remove zone identifier for validation (IPv6 link-local addresses)
                val ipWithoutZone = ip.split('%').first()
                
                // First, validate using strict parsers that don't perform DNS resolution
                val isStrictIpv4 = isValidIpv4Strict(ipWithoutZone)
                val isStrictIpv6 = isValidIpv6Strict(ipWithoutZone)
                
                if (!isStrictIpv4 && !isStrictIpv6) {
                    return false
                }
                
                // Only parse with InetAddress if strict validation passed
                // This ensures we only parse actual IP literals, not hostnames
                val address = java.net.InetAddress.getByName(ipWithoutZone)
                val canonicalAddress = address.hostAddress ?: return false
                
                // Verify no DNS resolution occurred: for a literal IP, the canonical form
                // should match the input (allowing for IPv6 compression differences)
                // If DNS resolution occurred, input would be a hostname that doesn't match canonical
                if (canonicalAddress.equals(ipWithoutZone, ignoreCase = true)) {
                    return true
                }
                
                // For IPv6, handle compression: compare byte arrays to handle cases like
                // "::1" (input) vs "0:0:0:0:0:0:0:1" (canonical)
                if (isStrictIpv6) {
                    val canonicalParsed = java.net.InetAddress.getByName(canonicalAddress)
                    return address.address.contentEquals(canonicalParsed.address)
                }
                
                false
            } catch (e: Exception) {
                false
            }
        }
        
        /**
         * Strictly validates IPv4 address format without DNS resolution.
         * Requires exactly 4 numeric octets in range 0-255, separated by dots.
         * 
         * @param ip The IP address string to validate
         * @return true if valid IPv4 format, false otherwise
         */
        private fun isValidIpv4Strict(ip: String): Boolean {
            val parts = ip.split('.')
            if (parts.size != 4) {
                return false
            }
            
            return parts.all { part ->
                val num = part.toIntOrNull()
                num != null && num in 0..255 && part == num.toString()
            }
        }
        
        /**
         * Strictly validates IPv6 address format without DNS resolution.
         * Handles compressed format (::), IPv4-mapped addresses, and zone identifiers.
         * 
         * @param ip The IP address string to validate
         * @return true if valid IPv6 format, false otherwise
         */
        private fun isValidIpv6Strict(ip: String): Boolean {
            // Remove zone identifier if present
            val ipWithoutZone = ip.split('%').first()
            
            // Check for IPv4-mapped IPv6 (::ffff:192.168.1.1)
            if (ipWithoutZone.contains('.')) {
                val lastColonIndex = ipWithoutZone.lastIndexOf(':')
                if (lastColonIndex < 0) {
                    return false
                }
                val ipv4Part = ipWithoutZone.substring(lastColonIndex + 1)
                if (!isValidIpv4Strict(ipv4Part)) {
                    return false
                }
                // Validate the IPv6 part before the IPv4
                val ipv6Part = ipWithoutZone.substring(0, lastColonIndex)
                return isValidIpv6HexPart(ipv6Part)
            }
            
            // Pure IPv6 validation
            return isValidIpv6HexPart(ipWithoutZone)
        }
        
        /**
         * Validates the hexadecimal part of an IPv6 address.
         * Handles compressed format (::) and validates hex groups.
         * 
         * @param ipv6Part The IPv6 hexadecimal part (may contain ::)
         * @return true if valid IPv6 hex format, false otherwise
         */
        private fun isValidIpv6HexPart(ipv6Part: String): Boolean {
            if (ipv6Part.isEmpty()) {
                return false
            }
            
            // Count occurrences of "::" - should be at most one
            val compressionCount = ipv6Part.split("::").size - 1
            if (compressionCount > 1) {
                return false
            }
            
            // Split by colons
            val parts = ipv6Part.split(':')
            
            // With compression, we can have fewer than 8 parts
            // Without compression, we need exactly 8 parts
            val hasCompression = compressionCount == 1
            val expectedParts = if (hasCompression) {
                // With ::, we can have 2-7 parts (:: expands to fill missing groups)
                parts.size in 2..7
            } else {
                // Without ::, we need exactly 8 parts
                parts.size == 8
            }
            
            if (!expectedParts) {
                return false
            }
            
            // Validate each hex part (1-4 hex digits, or empty if part of compression)
            return parts.all { part ->
                part.isEmpty() || (part.length <= 4 && part.matches(Regex("^[0-9a-fA-F]+$")))
            }
        }
    }
}

