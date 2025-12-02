package com.ble1st.connectias.feature.privacy.fingerprint

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.provider.Settings
import android.webkit.WebSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import timber.log.Timber
import java.security.MessageDigest
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for device fingerprint analysis.
 *
 * Features:
 * - Device fingerprint generation
 * - Uniqueness analysis
 * - Privacy risk assessment
 * - Fingerprint component breakdown
 */
@Singleton
class DeviceFingerprintProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Generates a device fingerprint.
     */
    suspend fun generateFingerprint(): DeviceFingerprint = withContext(Dispatchers.Default) {
        val components = collectFingerprintComponents()
        val hash = generateHash(components)
        val riskScore = calculatePrivacyRisk(components)

        DeviceFingerprint(
            hash = hash,
            components = components,
            riskScore = riskScore,
            uniquenessEstimate = estimateUniqueness(components),
            generatedAt = System.currentTimeMillis()
        )
    }

    /**
     * Collects all fingerprint components.
     */
    private fun collectFingerprintComponents(): List<FingerprintComponent> {
        val components = mutableListOf<FingerprintComponent>()

        // Device info
        components.add(
            FingerprintComponent(
                name = "Device Model",
                value = "${Build.MANUFACTURER} ${Build.MODEL}",
                category = ComponentCategory.HARDWARE,
                uniqueness = Uniqueness.MEDIUM,
                description = "Device manufacturer and model"
            )
        )

        components.add(
            FingerprintComponent(
                name = "Android Version",
                value = "Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
                category = ComponentCategory.SOFTWARE,
                uniqueness = Uniqueness.LOW,
                description = "Android OS version"
            )
        )

        components.add(
            FingerprintComponent(
                name = "Build ID",
                value = Build.ID,
                category = ComponentCategory.SOFTWARE,
                uniqueness = Uniqueness.MEDIUM,
                description = "Build identifier"
            )
        )

        // Screen info
        val displayMetrics = context.resources.displayMetrics
        components.add(
            FingerprintComponent(
                name = "Screen Resolution",
                value = "${displayMetrics.widthPixels}x${displayMetrics.heightPixels}",
                category = ComponentCategory.HARDWARE,
                uniqueness = Uniqueness.MEDIUM,
                description = "Screen resolution in pixels"
            )
        )

        components.add(
            FingerprintComponent(
                name = "Screen Density",
                value = "${displayMetrics.densityDpi} dpi (${displayMetrics.density}x)",
                category = ComponentCategory.HARDWARE,
                uniqueness = Uniqueness.LOW,
                description = "Screen density"
            )
        )

        // Locale and timezone
        components.add(
            FingerprintComponent(
                name = "Locale",
                value = Locale.getDefault().toString(),
                category = ComponentCategory.CONFIGURATION,
                uniqueness = Uniqueness.LOW,
                description = "Device locale setting"
            )
        )

        components.add(
            FingerprintComponent(
                name = "Timezone",
                value = TimeZone.getDefault().id,
                category = ComponentCategory.CONFIGURATION,
                uniqueness = Uniqueness.LOW,
                description = "Device timezone"
            )
        )

        // Hardware features
        val packageManager = context.packageManager

        val sensors = listOf(
            "android.hardware.sensor.accelerometer",
            "android.hardware.sensor.gyroscope",
            "android.hardware.sensor.compass",
            "android.hardware.sensor.barometer",
            "android.hardware.sensor.proximity",
            "android.hardware.sensor.light"
        )

        val availableSensors = sensors.filter { packageManager.hasSystemFeature(it) }
        components.add(
            FingerprintComponent(
                name = "Sensors",
                value = "${availableSensors.size} sensors",
                category = ComponentCategory.HARDWARE,
                uniqueness = Uniqueness.LOW,
                description = "Available hardware sensors"
            )
        )

        // Camera info
        val hasCamera = packageManager.hasSystemFeature("android.hardware.camera.any")
        val hasFrontCamera = packageManager.hasSystemFeature("android.hardware.camera.front")
        components.add(
            FingerprintComponent(
                name = "Camera",
                value = when {
                    hasCamera && hasFrontCamera -> "Rear + Front"
                    hasCamera -> "Rear only"
                    else -> "None"
                },
                category = ComponentCategory.HARDWARE,
                uniqueness = Uniqueness.LOW,
                description = "Camera availability"
            )
        )

        // Connectivity
        val hasNfc = packageManager.hasSystemFeature("android.hardware.nfc")
        val hasBluetooth = packageManager.hasSystemFeature("android.hardware.bluetooth")
        val hasBluetoothLe = packageManager.hasSystemFeature("android.hardware.bluetooth_le")
        components.add(
            FingerprintComponent(
                name = "Connectivity",
                value = buildString {
                    if (hasBluetooth) append("BT ")
                    if (hasBluetoothLe) append("BLE ")
                    if (hasNfc) append("NFC")
                }.trim().ifEmpty { "Basic" },
                category = ComponentCategory.HARDWARE,
                uniqueness = Uniqueness.LOW,
                description = "Connectivity features"
            )
        )

        // CPU info
        val cpuAbi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Build.SUPPORTED_ABIS.joinToString(", ")
        } else {
            @Suppress("DEPRECATION")
            "${Build.CPU_ABI}, ${Build.CPU_ABI2}"
        }
        components.add(
            FingerprintComponent(
                name = "CPU Architecture",
                value = cpuAbi,
                category = ComponentCategory.HARDWARE,
                uniqueness = Uniqueness.LOW,
                description = "Supported CPU architectures"
            )
        )

        // Memory
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / (1024 * 1024)
        components.add(
            FingerprintComponent(
                name = "Max Heap Size",
                value = "$maxMemory MB",
                category = ComponentCategory.HARDWARE,
                uniqueness = Uniqueness.LOW,
                description = "Maximum heap memory"
            )
        )

        // User agent
        try {
            val userAgent = WebSettings.getDefaultUserAgent(context)
            components.add(
                FingerprintComponent(
                    name = "User Agent",
                    value = userAgent.take(100) + if (userAgent.length > 100) "..." else "",
                    category = ComponentCategory.SOFTWARE,
                    uniqueness = Uniqueness.MEDIUM,
                    description = "WebView user agent string"
                )
            )
        } catch (e: Exception) {
            Timber.w(e, "Could not get user agent")
        }

        // Installed input methods
        val inputMethods = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        ) ?: ""
        components.add(
            FingerprintComponent(
                name = "Input Method",
                value = inputMethods.substringAfterLast("/"),
                category = ComponentCategory.SOFTWARE,
                uniqueness = Uniqueness.MEDIUM,
                description = "Default keyboard"
            )
        )

        // Font scale
        components.add(
            FingerprintComponent(
                name = "Font Scale",
                value = context.resources.configuration.fontScale.toString(),
                category = ComponentCategory.CONFIGURATION,
                uniqueness = Uniqueness.LOW,
                description = "System font scale"
            )
        )

        // Night mode
        val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        components.add(
            FingerprintComponent(
                name = "Night Mode",
                value = when (nightMode) {
                    Configuration.UI_MODE_NIGHT_YES -> "Enabled"
                    Configuration.UI_MODE_NIGHT_NO -> "Disabled"
                    else -> "Auto"
                },
                category = ComponentCategory.CONFIGURATION,
                uniqueness = Uniqueness.LOW,
                description = "Dark mode setting"
            )
        )

        return components
    }

    /**
     * Generates hash from components.
     */
    private fun generateHash(components: List<FingerprintComponent>): String {
        val data = components.sortedBy { it.name }
            .joinToString("|") { "${it.name}=${it.value}" }

        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(data.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Calculates privacy risk score.
     */
    private fun calculatePrivacyRisk(components: List<FingerprintComponent>): Int {
        var score = 0

        for (component in components) {
            score += when (component.uniqueness) {
                Uniqueness.HIGH -> 15
                Uniqueness.MEDIUM -> 10
                Uniqueness.LOW -> 5
                Uniqueness.VERY_LOW -> 2
            }
        }

        return minOf(score, 100)
    }

    /**
     * Estimates uniqueness percentage.
     */
    private fun estimateUniqueness(components: List<FingerprintComponent>): Double {
        val highCount = components.count { it.uniqueness == Uniqueness.HIGH }
        val mediumCount = components.count { it.uniqueness == Uniqueness.MEDIUM }

        // Rough estimate based on component uniqueness
        val baseUniqueness = 10.0 // Base percentage
        val highFactor = highCount * 15.0
        val mediumFactor = mediumCount * 5.0

        return minOf(baseUniqueness + highFactor + mediumFactor, 99.9)
    }

    /**
     * Gets privacy recommendations.
     */
    fun getPrivacyRecommendations(fingerprint: DeviceFingerprint): List<PrivacyRecommendation> {
        val recommendations = mutableListOf<PrivacyRecommendation>()

        if (fingerprint.riskScore > 70) {
            recommendations.add(
                PrivacyRecommendation(
                    title = "High Fingerprint Risk",
                    description = "Your device has many unique identifiers that can be used for tracking",
                    priority = Priority.HIGH
                )
            )
        }

        val highComponents = fingerprint.components.filter { it.uniqueness == Uniqueness.HIGH }
        if (highComponents.isNotEmpty()) {
            recommendations.add(
                PrivacyRecommendation(
                    title = "Unique Components",
                    description = "${highComponents.size} components have high uniqueness",
                    priority = Priority.MEDIUM
                )
            )
        }

        recommendations.add(
            PrivacyRecommendation(
                title = "Use Privacy Browser",
                description = "Consider using a privacy-focused browser with fingerprint protection",
                priority = Priority.LOW
            )
        )

        return recommendations
    }

    /**
     * Compares two fingerprints.
     */
    fun compareFingerprints(fp1: DeviceFingerprint, fp2: DeviceFingerprint): FingerprintComparison {
        val matching = mutableListOf<String>()
        val different = mutableListOf<String>()

        for (comp1 in fp1.components) {
            val comp2 = fp2.components.find { it.name == comp1.name }
            if (comp2 != null) {
                if (comp1.value == comp2.value) {
                    matching.add(comp1.name)
                } else {
                    different.add(comp1.name)
                }
            } else {
                different.add(comp1.name)
            }
        }

        val similarity = if (matching.size + different.size > 0) {
            matching.size.toDouble() / (matching.size + different.size) * 100
        } else {
            0.0
        }

        return FingerprintComparison(
            matchingComponents = matching,
            differentComponents = different,
            similarityPercentage = similarity
        )
    }
}

/**
 * Device fingerprint data.
 */
@Serializable
data class DeviceFingerprint(
    val hash: String,
    val components: List<FingerprintComponent>,
    val riskScore: Int,
    val uniquenessEstimate: Double,
    val generatedAt: Long
)

/**
 * Fingerprint component.
 */
@Serializable
data class FingerprintComponent(
    val name: String,
    val value: String,
    val category: ComponentCategory,
    val uniqueness: Uniqueness,
    val description: String
)

/**
 * Component categories.
 */
enum class ComponentCategory {
    HARDWARE,
    SOFTWARE,
    CONFIGURATION,
    NETWORK
}

/**
 * Uniqueness levels.
 */
enum class Uniqueness {
    HIGH,
    MEDIUM,
    LOW,
    VERY_LOW
}

/**
 * Privacy recommendation.
 */
@Serializable
data class PrivacyRecommendation(
    val title: String,
    val description: String,
    val priority: Priority
)

/**
 * Recommendation priority.
 */
enum class Priority {
    HIGH,
    MEDIUM,
    LOW
}

/**
 * Fingerprint comparison result.
 */
@Serializable
data class FingerprintComparison(
    val matchingComponents: List<String>,
    val differentComponents: List<String>,
    val similarityPercentage: Double
)
