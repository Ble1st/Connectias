package com.ble1st.connectias.feature.network.wol.models

import kotlinx.serialization.Serializable

/**
 * Represents a device that can be woken via Wake-on-LAN.
 */
@Serializable
data class WolDevice(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val macAddress: String,
    val ipAddress: String? = null,
    val broadcastAddress: String = "255.255.255.255",
    val port: Int = 9,
    val secureOnPassword: String? = null,
    val groupId: String? = null,
    val lastWakeAttempt: Long? = null,
    val lastSuccessfulWake: Long? = null,
    val isOnline: Boolean = false
) {
    /**
     * Validates the MAC address format.
     */
    fun isValidMacAddress(): Boolean {
        val macPattern = Regex("^([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})$")
        return macAddress.matches(macPattern)
    }

    /**
     * Returns the MAC address in normalized format (XX:XX:XX:XX:XX:XX).
     */
    fun normalizedMacAddress(): String {
        return macAddress
            .replace("-", ":")
            .uppercase()
    }
}

/**
 * Result of a Wake-on-LAN operation.
 */
sealed class WolResult {
    /**
     * Magic packet sent successfully.
     */
    data class Success(
        val device: WolDevice,
        val timestamp: Long = System.currentTimeMillis()
    ) : WolResult()

    /**
     * Device responded to ping after wake.
     */
    data class DeviceAwake(
        val device: WolDevice,
        val responseTime: Long,
        val timestamp: Long = System.currentTimeMillis()
    ) : WolResult()

    /**
     * Device did not respond after wake attempt.
     */
    data class DeviceNotResponding(
        val device: WolDevice,
        val timeout: Long
    ) : WolResult()

    /**
     * Error occurred during wake operation.
     */
    data class Error(
        val device: WolDevice?,
        val message: String,
        val exception: Throwable? = null
    ) : WolResult()

    /**
     * Invalid MAC address format.
     */
    data class InvalidMacAddress(
        val macAddress: String
    ) : WolResult()
}

/**
 * Group of devices that can be woken together.
 */
@Serializable
data class WolDeviceGroup(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val devices: List<String> = emptyList(), // Device IDs
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Scheduled wake configuration.
 */
@Serializable
data class WolSchedule(
    val id: String = java.util.UUID.randomUUID().toString(),
    val deviceId: String? = null,
    val groupId: String? = null,
    val cronExpression: String? = null,
    val daysOfWeek: List<Int> = emptyList(), // 1 = Monday, 7 = Sunday
    val hour: Int,
    val minute: Int,
    val isEnabled: Boolean = true,
    val lastExecution: Long? = null
)

/**
 * Wake-on-LAN history entry.
 */
@Serializable
data class WolHistoryEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val deviceId: String,
    val deviceName: String,
    val macAddress: String,
    val timestamp: Long = System.currentTimeMillis(),
    val success: Boolean,
    val deviceResponded: Boolean = false,
    val responseTime: Long? = null,
    val errorMessage: String? = null
)
