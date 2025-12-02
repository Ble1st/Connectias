package com.ble1st.connectias.feature.security.signature

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import com.ble1st.connectias.feature.security.signature.models.ComparisonResult
import com.ble1st.connectias.feature.security.signature.models.SignatureDetails
import com.ble1st.connectias.feature.security.signature.models.SignatureResult
import com.ble1st.connectias.feature.security.signature.models.SignatureScheme
import com.ble1st.connectias.feature.security.signature.models.SuspiciousApp
import com.ble1st.connectias.feature.security.signature.models.SuspiciousReason
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for app signature verification functionality.
 *
 * Features:
 * - Verify app signatures
 * - Detect repackaged apps
 * - Compare signatures between apps
 * - Check signature scheme versions
 */
@Singleton
class AppSignatureVerifierProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val packageManager = context.packageManager
    private val certificateFactory = CertificateFactory.getInstance("X.509")

    // Known debug signature fingerprints
    private val debugFingerprints = setOf(
        "A40DA80A59D170CAA950CF15C18C454D47A39B26989D8B640ECD745BA71BF5DC", // Android Studio debug
    )

    /**
     * Verifies the signature of an installed app.
     */
    suspend fun verifySignature(packageName: String): SignatureResult = withContext(Dispatchers.IO) {
        try {
            val packageInfo = getPackageInfoWithSignatures(packageName)
            val signatures = getSignatures(packageInfo)
            val signatureDetails = signatures.map { parseSignature(it) }
            val scheme = getSignatureSchemeVersion(packageName)

            val warnings = mutableListOf<String>()
            val errors = mutableListOf<String>()

            // Check for expired certificates
            signatureDetails.filter { it.isExpired }.forEach {
                warnings.add("Certificate expired: ${it.subject}")
            }

            // Check for debug signatures
            signatureDetails.forEach { sig ->
                if (sig.fingerprint.uppercase() in debugFingerprints) {
                    warnings.add("Debug certificate detected")
                }
            }

            // Check for weak signature schemes
            if (scheme == SignatureScheme.V1_JAR) {
                warnings.add("Using older JAR signature (v1) only - consider updating to v2/v3")
            }

            val appName = packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(packageName, 0)
            ).toString()

            SignatureResult(
                packageName = packageName,
                appName = appName,
                isValid = errors.isEmpty(),
                signatureScheme = scheme,
                signatures = signatureDetails,
                warnings = warnings,
                errors = errors
            )
        } catch (e: Exception) {
            Timber.e(e, "Error verifying signature for $packageName")
            SignatureResult(
                packageName = packageName,
                appName = packageName,
                isValid = false,
                signatureScheme = SignatureScheme.UNKNOWN,
                signatures = emptyList(),
                errors = listOf("Verification failed: ${e.message}")
            )
        }
    }

    /**
     * Gets detailed signature information.
     */
    suspend fun getSignatureDetails(packageName: String): List<SignatureDetails> = withContext(Dispatchers.IO) {
        try {
            val packageInfo = getPackageInfoWithSignatures(packageName)
            val signatures = getSignatures(packageInfo)
            signatures.map { parseSignature(it) }
        } catch (e: Exception) {
            Timber.e(e, "Error getting signature details for $packageName")
            emptyList()
        }
    }

    /**
     * Detects potentially repackaged apps.
     */
    suspend fun detectRepackagedApps(): List<SuspiciousApp> = withContext(Dispatchers.IO) {
        val suspicious = mutableListOf<SuspiciousApp>()
        val installedApps = packageManager.getInstalledPackages(0)

        for (packageInfo in installedApps) {
            try {
                val packageName = packageInfo.packageName
                val result = verifySignature(packageName)

                // Check for self-signed certificates
                result.signatures.forEach { sig ->
                    if (sig.issuer == sig.subject && !isWellKnownDeveloper(sig.fingerprint)) {
                        val appName = packageManager.getApplicationLabel(
                            packageManager.getApplicationInfo(packageName, 0)
                        ).toString()

                        suspicious.add(
                            SuspiciousApp(
                                packageName = packageName,
                                appName = appName,
                                reason = SuspiciousReason.SELF_SIGNED,
                                originalDeveloper = null,
                                currentDeveloper = sig.subject,
                                details = "Self-signed certificate detected"
                            )
                        )
                    }
                }

                // Check for debug signatures
                result.signatures.forEach { sig ->
                    if (sig.fingerprint.uppercase() in debugFingerprints) {
                        val appName = packageManager.getApplicationLabel(
                            packageManager.getApplicationInfo(packageName, 0)
                        ).toString()

                        suspicious.add(
                            SuspiciousApp(
                                packageName = packageName,
                                appName = appName,
                                reason = SuspiciousReason.DEBUG_SIGNATURE,
                                originalDeveloper = null,
                                currentDeveloper = sig.subject,
                                details = "Debug signature detected in production app"
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Error checking ${packageInfo.packageName}")
            }
        }

        suspicious
    }

    /**
     * Compares signatures between two packages.
     */
    suspend fun compareSignatures(
        package1: String,
        package2: String
    ): ComparisonResult = withContext(Dispatchers.IO) {
        val sigs1 = getSignatureDetails(package1)
        val sigs2 = getSignatureDetails(package2)

        val fingerprints1 = sigs1.map { it.fingerprint.uppercase() }.toSet()
        val fingerprints2 = sigs2.map { it.fingerprint.uppercase() }.toSet()

        val matching = fingerprints1.intersect(fingerprints2).toList()
        val different = mutableListOf<Pair<String, String>>()

        if (matching.isEmpty()) {
            fingerprints1.zip(fingerprints2).forEach { (f1, f2) ->
                if (f1 != f2) {
                    different.add(f1 to f2)
                }
            }
        }

        val isSameDeveloper = matching.isNotEmpty()

        ComparisonResult(
            package1 = package1,
            package2 = package2,
            isSameDeveloper = isSameDeveloper,
            matchingFingerprints = matching,
            differentFingerprints = different,
            details = if (isSameDeveloper) {
                "Apps share ${matching.size} common certificate(s)"
            } else {
                "Apps have different signing certificates"
            }
        )
    }

    /**
     * Gets the signature scheme version used by an app.
     */
    fun getSignatureSchemeVersion(packageName: String): SignatureScheme {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNATURES
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = packageInfo.signingInfo
                when {
                    signingInfo == null -> SignatureScheme.UNKNOWN
                    signingInfo.hasMultipleSigners() -> SignatureScheme.V1_JAR
                    signingInfo.hasPastSigningCertificates() -> SignatureScheme.V3_APK
                    else -> SignatureScheme.V2_APK
                }
            } else {
                SignatureScheme.V1_JAR
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting signature scheme for $packageName")
            SignatureScheme.UNKNOWN
        }
    }

    // Private helper methods

    private fun getPackageInfoWithSignatures(packageName: String): PackageInfo {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNING_CERTIFICATES
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(
                packageName,
                PackageManager.GET_SIGNATURES
            )
        }
    }

    private fun getSignatures(packageInfo: PackageInfo): Array<Signature> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = packageInfo.signingInfo
            if (signingInfo != null) {
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory ?: arrayOf()
                }
            } else {
                arrayOf()
            }
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures ?: arrayOf()
        }
    }

    private fun parseSignature(signature: Signature): SignatureDetails {
        val cert = certificateFactory.generateCertificate(
            ByteArrayInputStream(signature.toByteArray())
        ) as X509Certificate

        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(cert.encoded)
            .joinToString("") { "%02X".format(it) }

        return SignatureDetails(
            fingerprint = fingerprint,
            algorithm = cert.sigAlgName,
            issuer = cert.issuerX500Principal.name,
            subject = cert.subjectX500Principal.name,
            validFrom = cert.notBefore.time,
            validUntil = cert.notAfter.time,
            publicKeyType = cert.publicKey.algorithm,
            publicKeySize = getKeySize(cert),
            serialNumber = cert.serialNumber.toString(16)
        )
    }

    private fun getKeySize(cert: X509Certificate): Int {
        return try {
            when (cert.publicKey.algorithm) {
                "RSA" -> {
                    val rsaKey = cert.publicKey as java.security.interfaces.RSAPublicKey
                    rsaKey.modulus.bitLength()
                }
                "EC" -> {
                    val ecKey = cert.publicKey as java.security.interfaces.ECPublicKey
                    ecKey.params.order.bitLength()
                }
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
    }

    private fun isWellKnownDeveloper(fingerprint: String): Boolean {
        // In a real implementation, this would check against a database of known developer fingerprints
        return false
    }
}
