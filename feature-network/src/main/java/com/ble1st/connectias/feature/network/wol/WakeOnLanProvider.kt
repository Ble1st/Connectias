package com.ble1st.connectias.feature.network.wol

import android.content.Context
import com.ble1st.connectias.feature.network.wol.models.WolDevice
import com.ble1st.connectias.feature.network.wol.models.WolDeviceGroup
import com.ble1st.connectias.feature.network.wol.models.WolHistoryEntry
import com.ble1st.connectias.feature.network.wol.models.WolResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for Wake-on-LAN functionality.
 *
 * Supports:
 * - Sending magic packets to wake devices
 * - Secure Wake-on-LAN with password
 * - Device verification via ping
 * - Device and group management
 * - Wake history tracking
 */
@Singleton
class WakeOnLanProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val MAGIC_PACKET_SIZE = 102
        private const val SECURE_MAGIC_PACKET_SIZE = 108
        private const val MAC_REPETITIONS = 16
        private const val HEADER_SIZE = 6
        private const val MAC_BYTES = 6
        private const val DEFAULT_PORT = 9
        private const val PING_TIMEOUT = 5000L
        private const val PING_RETRY_DELAY = 1000L
        private const val MAX_PING_RETRIES = 10
    }

    private val _devices = MutableStateFlow<List<WolDevice>>(emptyList())
    val devices: StateFlow<List<WolDevice>> = _devices.asStateFlow()

    private val _groups = MutableStateFlow<List<WolDeviceGroup>>(emptyList())
    val groups: StateFlow<List<WolDeviceGroup>> = _groups.asStateFlow()

    private val _history = MutableStateFlow<List<WolHistoryEntry>>(emptyList())
    val history: StateFlow<List<WolHistoryEntry>> = _history.asStateFlow()

    /**
     * Sends a Wake-on-LAN magic packet to wake a device.
     *
     * @param macAddress MAC address of the device (formats: XX:XX:XX:XX:XX:XX or XX-XX-XX-XX-XX-XX)
     * @param broadcastAddress Broadcast IP address (default: 255.255.255.255)
     * @param port UDP port (default: 9, alternative: 7)
     * @return WolResult indicating success or failure
     */
    suspend fun sendMagicPacket(
        macAddress: String,
        broadcastAddress: String = "255.255.255.255",
        port: Int = DEFAULT_PORT
    ): WolResult = withContext(Dispatchers.IO) {
        try {
            val macBytes = parseMacAddress(macAddress)
                ?: return@withContext WolResult.InvalidMacAddress(macAddress)

            val magicPacket = createMagicPacket(macBytes)
            sendPacket(magicPacket, broadcastAddress, port)

            val device = WolDevice(
                name = "Unknown",
                macAddress = macAddress,
                broadcastAddress = broadcastAddress,
                port = port
            )

            WolResult.Success(device)
        } catch (e: Exception) {
            Timber.e(e, "Failed to send magic packet to $macAddress")
            WolResult.Error(null, "Failed to send magic packet: ${e.message}", e)
        }
    }

    /**
     * Sends a Secure Wake-on-LAN magic packet with password.
     *
     * @param macAddress MAC address of the device
     * @param password 6-byte password (as hex string or 6 characters)
     * @param broadcastAddress Broadcast IP address
     * @param port UDP port
     * @return WolResult indicating success or failure
     */
    suspend fun sendSecureMagicPacket(
        macAddress: String,
        password: String,
        broadcastAddress: String = "255.255.255.255",
        port: Int = DEFAULT_PORT
    ): WolResult = withContext(Dispatchers.IO) {
        try {
            val macBytes = parseMacAddress(macAddress)
                ?: return@withContext WolResult.InvalidMacAddress(macAddress)

            val passwordBytes = parsePassword(password)
                ?: return@withContext WolResult.Error(
                    null,
                    "Invalid password format. Must be 6 bytes (hex or ASCII)"
                )

            val magicPacket = createSecureMagicPacket(macBytes, passwordBytes)
            sendPacket(magicPacket, broadcastAddress, port)

            val device = WolDevice(
                name = "Unknown",
                macAddress = macAddress,
                broadcastAddress = broadcastAddress,
                port = port,
                secureOnPassword = password
            )

            WolResult.Success(device)
        } catch (e: Exception) {
            Timber.e(e, "Failed to send secure magic packet to $macAddress")
            WolResult.Error(null, "Failed to send secure magic packet: ${e.message}", e)
        }
    }

    /**
     * Wakes a device and optionally verifies it came online.
     *
     * @param device The device to wake
     * @param verifyOnline Whether to verify the device came online via ping
     * @param timeout Timeout for verification in milliseconds
     * @return WolResult indicating success or failure
     */
    suspend fun wakeDevice(
        device: WolDevice,
        verifyOnline: Boolean = true,
        timeout: Long = 30000
    ): WolResult = withContext(Dispatchers.IO) {
        try {
            if (!device.isValidMacAddress()) {
                return@withContext WolResult.InvalidMacAddress(device.macAddress)
            }

            val macBytes = parseMacAddress(device.macAddress)!!

            val magicPacket = if (device.secureOnPassword != null) {
                val passwordBytes = parsePassword(device.secureOnPassword)
                    ?: return@withContext WolResult.Error(device, "Invalid password")
                createSecureMagicPacket(macBytes, passwordBytes)
            } else {
                createMagicPacket(macBytes)
            }

            sendPacket(magicPacket, device.broadcastAddress, device.port)
            Timber.d("Magic packet sent to ${device.name} (${device.macAddress})")

            // Update last wake attempt
            updateDevice(device.copy(lastWakeAttempt = System.currentTimeMillis()))

            if (verifyOnline && device.ipAddress != null) {
                val awakeResult = verifyDeviceAwake(device, timeout)
                addHistoryEntry(device, awakeResult)
                return@withContext awakeResult
            }

            val result = WolResult.Success(device)
            addHistoryEntry(device, result)
            result
        } catch (e: Exception) {
            Timber.e(e, "Failed to wake device ${device.name}")
            val result = WolResult.Error(device, "Failed to wake device: ${e.message}", e)
            addHistoryEntry(device, result)
            result
        }
    }

    /**
     * Wakes all devices in a group.
     */
    suspend fun wakeGroup(group: WolDeviceGroup): List<WolResult> = coroutineScope {
        val deviceList = _devices.value.filter { it.id in group.devices }
        
        deviceList.map { device ->
            async { wakeDevice(device, verifyOnline = false) }
        }.awaitAll()
    }

    /**
     * Verifies if a device came online after wake attempt.
     */
    suspend fun verifyDeviceAwake(
        device: WolDevice,
        timeout: Long = 30000
    ): WolResult = withContext(Dispatchers.IO) {
        val ipAddress = device.ipAddress
            ?: return@withContext WolResult.Error(device, "No IP address configured")

        val startTime = System.currentTimeMillis()
        var retries = 0

        while (System.currentTimeMillis() - startTime < timeout && retries < MAX_PING_RETRIES) {
            val isReachable = pingHost(ipAddress, PING_TIMEOUT.toInt())
            
            if (isReachable) {
                val responseTime = System.currentTimeMillis() - startTime
                updateDevice(device.copy(
                    isOnline = true,
                    lastSuccessfulWake = System.currentTimeMillis()
                ))
                return@withContext WolResult.DeviceAwake(device, responseTime)
            }

            delay(PING_RETRY_DELAY)
            retries++
        }

        WolResult.DeviceNotResponding(device, timeout)
    }

    /**
     * Checks if a device is currently online.
     */
    suspend fun isDeviceOnline(device: WolDevice): Boolean = withContext(Dispatchers.IO) {
        val ipAddress = device.ipAddress ?: return@withContext false
        pingHost(ipAddress, PING_TIMEOUT.toInt())
    }

    /**
     * Checks online status for all devices.
     */
    suspend fun refreshDeviceStatuses() = withContext(Dispatchers.IO) {
        coroutineScope {
            _devices.value.filter { it.ipAddress != null }.map { device ->
                async {
                    val isOnline = isDeviceOnline(device)
                    if (device.isOnline != isOnline) {
                        updateDevice(device.copy(isOnline = isOnline))
                    }
                }
            }.awaitAll()
        }
    }

    // Device management

    /**
     * Adds a new device.
     */
    fun addDevice(device: WolDevice) {
        _devices.update { it + device }
    }

    /**
     * Updates an existing device.
     */
    fun updateDevice(device: WolDevice) {
        _devices.update { devices ->
            devices.map { if (it.id == device.id) device else it }
        }
    }

    /**
     * Removes a device.
     */
    fun removeDevice(deviceId: String) {
        _devices.update { it.filter { device -> device.id != deviceId } }
    }

    /**
     * Gets a device by ID.
     */
    fun getDevice(deviceId: String): WolDevice? {
        return _devices.value.find { it.id == deviceId }
    }

    // Group management

    /**
     * Adds a new device group.
     */
    fun addGroup(group: WolDeviceGroup) {
        _groups.update { it + group }
    }

    /**
     * Updates an existing group.
     */
    fun updateGroup(group: WolDeviceGroup) {
        _groups.update { groups ->
            groups.map { if (it.id == group.id) group else it }
        }
    }

    /**
     * Removes a group.
     */
    fun removeGroup(groupId: String) {
        _groups.update { it.filter { group -> group.id != groupId } }
    }

    // History management

    private fun addHistoryEntry(device: WolDevice, result: WolResult) {
        val entry = WolHistoryEntry(
            deviceId = device.id,
            deviceName = device.name,
            macAddress = device.macAddress,
            success = result is WolResult.Success || result is WolResult.DeviceAwake,
            deviceResponded = result is WolResult.DeviceAwake,
            responseTime = (result as? WolResult.DeviceAwake)?.responseTime,
            errorMessage = (result as? WolResult.Error)?.message
        )
        _history.update { listOf(entry) + it.take(99) }
    }

    /**
     * Clears the wake history.
     */
    fun clearHistory() {
        _history.update { emptyList() }
    }

    // Private helper methods

    private fun parseMacAddress(mac: String): ByteArray? {
        return try {
            val normalized = mac.replace("-", ":").uppercase()
            val parts = normalized.split(":")
            
            if (parts.size != 6) return null
            
            parts.map { 
                it.toInt(16).toByte() 
            }.toByteArray()
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse MAC address: $mac")
            null
        }
    }

    private fun parsePassword(password: String): ByteArray? {
        return try {
            when {
                // Hex format (12 characters = 6 bytes)
                password.length == 12 && password.all { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' } -> {
                    password.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                }
                // ASCII format (6 characters)
                password.length == 6 -> {
                    password.toByteArray(Charsets.US_ASCII)
                }
                else -> null
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse password")
            null
        }
    }

    private fun createMagicPacket(macBytes: ByteArray): ByteArray {
        val packet = ByteArray(MAGIC_PACKET_SIZE)
        
        // First 6 bytes: 0xFF
        for (i in 0 until HEADER_SIZE) {
            packet[i] = 0xFF.toByte()
        }
        
        // Next 96 bytes: MAC address repeated 16 times
        for (i in 0 until MAC_REPETITIONS) {
            System.arraycopy(macBytes, 0, packet, HEADER_SIZE + i * MAC_BYTES, MAC_BYTES)
        }
        
        return packet
    }

    private fun createSecureMagicPacket(macBytes: ByteArray, password: ByteArray): ByteArray {
        val packet = ByteArray(SECURE_MAGIC_PACKET_SIZE)
        
        // Create standard magic packet
        val standardPacket = createMagicPacket(macBytes)
        System.arraycopy(standardPacket, 0, packet, 0, MAGIC_PACKET_SIZE)
        
        // Append password (6 bytes)
        System.arraycopy(password, 0, packet, MAGIC_PACKET_SIZE, 6)
        
        return packet
    }

    private suspend fun sendPacket(
        packet: ByteArray,
        broadcastAddress: String,
        port: Int
    ) = withContext(Dispatchers.IO) {
        DatagramSocket().use { socket ->
            socket.broadcast = true
            
            val address = InetAddress.getByName(broadcastAddress)
            val datagramPacket = DatagramPacket(packet, packet.size, address, port)
            
            socket.send(datagramPacket)
            Timber.d("Magic packet sent to $broadcastAddress:$port")
        }
    }

    private suspend fun pingHost(host: String, timeout: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val address = InetAddress.getByName(host)
            address.isReachable(timeout)
        } catch (e: Exception) {
            Timber.e(e, "Ping failed for $host")
            false
        }
    }
}
