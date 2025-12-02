package com.ble1st.connectias.feature.security.signature.models

import kotlinx.serialization.Serializable

/**
 * Result of signature verification.
 */
@Serializable
data class SignatureResult(
    val packageName: String,
    val appName: String,
    val isValid: Boolean,
    val signatureScheme: SignatureScheme,
    val signatures: List<SignatureDetails>,
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
    val verificationTimestamp: Long = System.currentTimeMillis()
)

/**
 * Details about a single signature.
 */
@Serializable
data class SignatureDetails(
    val fingerprint: String, // SHA-256
    val algorithm: String,
    val issuer: String,
    val subject: String,
    val validFrom: Long,
    val validUntil: Long,
    val publicKeyType: String,
    val publicKeySize: Int,
    val serialNumber: String
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() > validUntil

    val isNotYetValid: Boolean
        get() = System.currentTimeMillis() < validFrom

    val daysUntilExpiry: Long
        get() = (validUntil - System.currentTimeMillis()) / (24 * 60 * 60 * 1000)
}

/**
 * APK signature scheme version.
 */
enum class SignatureScheme {
    V1_JAR,      // JAR Signature
    V2_APK,      // APK Signature Scheme v2
    V3_APK,      // APK Signature Scheme v3
    V4_APK,      // APK Signature Scheme v4
    UNKNOWN
}

/**
 * Suspicious app detection result.
 */
@Serializable
data class SuspiciousApp(
    val packageName: String,
    val appName: String,
    val reason: SuspiciousReason,
    val originalDeveloper: String?,
    val currentDeveloper: String?,
    val details: String
)

/**
 * Reason for suspecting an app.
 */
enum class SuspiciousReason {
    DIFFERENT_SIGNATURE,
    SELF_SIGNED,
    EXPIRED_CERTIFICATE,
    UNKNOWN_DEVELOPER,
    REPACKAGED,
    DEBUG_SIGNATURE
}

/**
 * Comparison result between two signatures.
 */
@Serializable
data class ComparisonResult(
    val package1: String,
    val package2: String,
    val isSameDeveloper: Boolean,
    val matchingFingerprints: List<String>,
    val differentFingerprints: List<Pair<String, String>>,
    val details: String
)

/**
 * Known developer signature for verification.
 */
@Serializable
data class KnownDeveloper(
    val name: String,
    val fingerprints: List<String>,
    val packages: List<String>
)
