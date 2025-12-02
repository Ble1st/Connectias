package com.ble1st.connectias.feature.network.snmp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import timber.log.Timber
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for SNMP management functionality.
 *
 * Features:
 * - SNMP v1/v2c queries
 * - MIB browser
 * - SNMP walk
 * - Device discovery
 * - Value monitoring
 */
@Singleton
class SnmpManagerProvider @Inject constructor() {

    private val _devices = MutableStateFlow<List<SnmpDevice>>(emptyList())
    val devices: StateFlow<List<SnmpDevice>> = _devices.asStateFlow()

    private val _monitoredOids = MutableStateFlow<List<MonitoredOid>>(emptyList())
    val monitoredOids: StateFlow<List<MonitoredOid>> = _monitoredOids.asStateFlow()

    companion object {
        const val DEFAULT_PORT = 161
        const val DEFAULT_TIMEOUT = 5000
        const val DEFAULT_COMMUNITY = "public"

        // Common OIDs
        val COMMON_OIDS = mapOf(
            "1.3.6.1.2.1.1.1.0" to "sysDescr",
            "1.3.6.1.2.1.1.2.0" to "sysObjectID",
            "1.3.6.1.2.1.1.3.0" to "sysUpTime",
            "1.3.6.1.2.1.1.4.0" to "sysContact",
            "1.3.6.1.2.1.1.5.0" to "sysName",
            "1.3.6.1.2.1.1.6.0" to "sysLocation",
            "1.3.6.1.2.1.1.7.0" to "sysServices",
            "1.3.6.1.2.1.2.1.0" to "ifNumber",
            "1.3.6.1.2.1.25.1.1.0" to "hrSystemUptime",
            "1.3.6.1.2.1.25.2.2.0" to "hrMemorySize"
        )
    }

    /**
     * Performs SNMP GET request.
     */
    suspend fun snmpGet(
        host: String,
        oid: String,
        community: String = DEFAULT_COMMUNITY,
        port: Int = DEFAULT_PORT,
        timeout: Int = DEFAULT_TIMEOUT,
        version: SnmpVersion = SnmpVersion.V2C
    ): SnmpResult = withContext(Dispatchers.IO) {
        try {
            val request = buildGetRequest(oid, community, version)
            val response = sendRequest(host, port, request, timeout)
            parseResponse(response, oid)
        } catch (e: SocketTimeoutException) {
            SnmpResult.Timeout(host, oid)
        } catch (e: Exception) {
            Timber.e(e, "SNMP GET failed for $host:$oid")
            SnmpResult.Error("SNMP GET failed: ${e.message}", e)
        }
    }

    /**
     * Performs SNMP GET for multiple OIDs.
     */
    suspend fun snmpGetBulk(
        host: String,
        oids: List<String>,
        community: String = DEFAULT_COMMUNITY,
        port: Int = DEFAULT_PORT
    ): List<SnmpResult> = withContext(Dispatchers.IO) {
        oids.map { oid ->
            snmpGet(host, oid, community, port)
        }
    }

    /**
     * Performs SNMP WALK on a subtree.
     */
    fun snmpWalk(
        host: String,
        rootOid: String,
        community: String = DEFAULT_COMMUNITY,
        port: Int = DEFAULT_PORT,
        timeout: Int = DEFAULT_TIMEOUT
    ): Flow<SnmpResult> = flow {
        var currentOid = rootOid
        var count = 0
        val maxIterations = 1000

        while (count < maxIterations) {
            val result = snmpGetNext(host, currentOid, community, port, timeout)

            when (result) {
                is SnmpResult.Success -> {
                    if (!result.oid.startsWith(rootOid)) {
                        break
                    }
                    emit(result)
                    currentOid = result.oid
                    count++
                }
                is SnmpResult.EndOfMib -> break
                else -> {
                    emit(result)
                    break
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Performs SNMP GET-NEXT request.
     */
    suspend fun snmpGetNext(
        host: String,
        oid: String,
        community: String = DEFAULT_COMMUNITY,
        port: Int = DEFAULT_PORT,
        timeout: Int = DEFAULT_TIMEOUT
    ): SnmpResult = withContext(Dispatchers.IO) {
        try {
            val request = buildGetNextRequest(oid, community)
            val response = sendRequest(host, port, request, timeout)
            parseResponse(response, oid)
        } catch (e: SocketTimeoutException) {
            SnmpResult.Timeout(host, oid)
        } catch (e: Exception) {
            Timber.e(e, "SNMP GET-NEXT failed")
            SnmpResult.Error("SNMP GET-NEXT failed: ${e.message}", e)
        }
    }

    /**
     * Gets system information.
     */
    suspend fun getSystemInfo(
        host: String,
        community: String = DEFAULT_COMMUNITY,
        port: Int = DEFAULT_PORT
    ): SystemInfo = withContext(Dispatchers.IO) {
        val sysDescr = (snmpGet(host, "1.3.6.1.2.1.1.1.0", community, port) as? SnmpResult.Success)?.value
        val sysName = (snmpGet(host, "1.3.6.1.2.1.1.5.0", community, port) as? SnmpResult.Success)?.value
        val sysLocation = (snmpGet(host, "1.3.6.1.2.1.1.6.0", community, port) as? SnmpResult.Success)?.value
        val sysContact = (snmpGet(host, "1.3.6.1.2.1.1.4.0", community, port) as? SnmpResult.Success)?.value
        val sysUpTime = (snmpGet(host, "1.3.6.1.2.1.1.3.0", community, port) as? SnmpResult.Success)?.value

        SystemInfo(
            host = host,
            description = sysDescr,
            name = sysName,
            location = sysLocation,
            contact = sysContact,
            uptime = sysUpTime?.let { parseUptime(it) }
        )
    }

    /**
     * Discovers SNMP-enabled devices on network.
     */
    fun discoverDevices(
        subnet: String,
        community: String = DEFAULT_COMMUNITY,
        port: Int = DEFAULT_PORT,
        timeout: Int = 1000
    ): Flow<SnmpDevice> = flow {
        val baseIp = subnet.substringBeforeLast(".")

        for (i in 1..254) {
            val host = "$baseIp.$i"
            try {
                val result = snmpGet(host, "1.3.6.1.2.1.1.1.0", community, port, timeout)
                if (result is SnmpResult.Success) {
                    val device = SnmpDevice(
                        host = host,
                        port = port,
                        community = community,
                        sysDescr = result.value,
                        discoveredAt = System.currentTimeMillis()
                    )
                    emit(device)
                    _devices.update { it + device }
                }
            } catch (e: Exception) {
                // Device not reachable or no SNMP
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Adds a device for monitoring.
     */
    fun addDevice(device: SnmpDevice) {
        _devices.update { it.filter { d -> d.id != device.id } + device }
    }

    /**
     * Removes a device.
     */
    fun removeDevice(deviceId: String) {
        _devices.update { it.filter { d -> d.id != deviceId } }
    }

    /**
     * Monitors an OID value.
     */
    fun monitorOid(
        host: String,
        oid: String,
        community: String = DEFAULT_COMMUNITY,
        intervalMs: Long = 60000
    ): MonitoredOid {
        val monitored = MonitoredOid(
            host = host,
            oid = oid,
            community = community,
            intervalMs = intervalMs
        )
        _monitoredOids.update { it + monitored }
        return monitored
    }

    /**
     * Stops monitoring an OID.
     */
    fun stopMonitoring(monitorId: String) {
        _monitoredOids.update { it.filter { m -> m.id != monitorId } }
    }

    // SNMP packet building

    private fun buildGetRequest(oid: String, community: String, version: SnmpVersion): ByteArray {
        val oidBytes = encodeOid(oid)
        val communityBytes = community.toByteArray()

        val varbind = buildVarbind(oidBytes, byteArrayOf(0x05, 0x00)) // NULL value
        val varbindList = wrapSequence(varbind)
        val pdu = buildPdu(0xA0.toByte(), 1, 0, 0, varbindList) // GetRequest PDU
        val message = buildMessage(version, communityBytes, pdu)

        return wrapSequence(message)
    }

    private fun buildGetNextRequest(oid: String, community: String): ByteArray {
        val oidBytes = encodeOid(oid)
        val communityBytes = community.toByteArray()

        val varbind = buildVarbind(oidBytes, byteArrayOf(0x05, 0x00))
        val varbindList = wrapSequence(varbind)
        val pdu = buildPdu(0xA1.toByte(), 1, 0, 0, varbindList) // GetNextRequest PDU
        val message = buildMessage(SnmpVersion.V2C, communityBytes, pdu)

        return wrapSequence(message)
    }

    private fun encodeOid(oid: String): ByteArray {
        val parts = oid.split(".").filter { it.isNotEmpty() }.map { it.toInt() }
        if (parts.size < 2) return byteArrayOf()

        val result = mutableListOf<Byte>()
        result.add((parts[0] * 40 + parts[1]).toByte())

        for (i in 2 until parts.size) {
            val value = parts[i]
            if (value < 128) {
                result.add(value.toByte())
            } else {
                val encoded = mutableListOf<Byte>()
                var v = value
                encoded.add((v and 0x7F).toByte())
                v = v shr 7
                while (v > 0) {
                    encoded.add(0, ((v and 0x7F) or 0x80).toByte())
                    v = v shr 7
                }
                result.addAll(encoded)
            }
        }

        return byteArrayOf(0x06, result.size.toByte()) + result.toByteArray()
    }

    private fun buildVarbind(oid: ByteArray, value: ByteArray): ByteArray {
        return wrapSequence(oid + value)
    }

    private fun buildPdu(type: Byte, requestId: Int, errorStatus: Int, errorIndex: Int, varbinds: ByteArray): ByteArray {
        val requestIdBytes = encodeInteger(requestId)
        val errorStatusBytes = encodeInteger(errorStatus)
        val errorIndexBytes = encodeInteger(errorIndex)
        val content = requestIdBytes + errorStatusBytes + errorIndexBytes + varbinds
        return byteArrayOf(type, content.size.toByte()) + content
    }

    private fun buildMessage(version: SnmpVersion, community: ByteArray, pdu: ByteArray): ByteArray {
        val versionBytes = encodeInteger(version.value)
        val communityBytes = byteArrayOf(0x04, community.size.toByte()) + community
        return versionBytes + communityBytes + pdu
    }

    private fun encodeInteger(value: Int): ByteArray {
        val bytes = mutableListOf<Byte>()
        var v = value
        do {
            bytes.add(0, (v and 0xFF).toByte())
            v = v shr 8
        } while (v != 0 && v != -1)

        if (value >= 0 && bytes[0].toInt() and 0x80 != 0) {
            bytes.add(0, 0)
        }

        return byteArrayOf(0x02, bytes.size.toByte()) + bytes.toByteArray()
    }

    private fun wrapSequence(data: ByteArray): ByteArray {
        return byteArrayOf(0x30, data.size.toByte()) + data
    }

    // Network communication

    private suspend fun sendRequest(host: String, port: Int, request: ByteArray, timeout: Int): ByteArray {
        val socket = DatagramSocket()
        socket.soTimeout = timeout

        try {
            val address = InetAddress.getByName(host)
            val sendPacket = DatagramPacket(request, request.size, address, port)
            socket.send(sendPacket)

            val buffer = ByteArray(65535)
            val receivePacket = DatagramPacket(buffer, buffer.size)
            socket.receive(receivePacket)

            return buffer.copyOf(receivePacket.length)
        } finally {
            socket.close()
        }
    }

    // Response parsing

    private fun parseResponse(response: ByteArray, requestedOid: String): SnmpResult {
        try {
            if (response.isEmpty() || response[0] != 0x30.toByte()) {
                return SnmpResult.Error("Invalid SNMP response")
            }

            var offset = 2 // Skip sequence header
            offset += skipTlv(response, offset) // Skip version
            offset += skipTlv(response, offset) // Skip community

            if (offset >= response.size) {
                return SnmpResult.Error("Response too short")
            }

            val pduType = response[offset].toInt() and 0xFF
            if (pduType == 0xA2) { // GetResponse
                offset += 2
                val (requestId, nextOffset1) = decodeInteger(response, offset)
                offset = nextOffset1
                val (errorStatus, nextOffset2) = decodeInteger(response, offset)
                offset = nextOffset2
                val (errorIndex, nextOffset3) = decodeInteger(response, offset)
                offset = nextOffset3

                if (errorStatus != 0) {
                    return SnmpResult.Error("SNMP error: status=$errorStatus, index=$errorIndex")
                }

                // Parse varbind list
                if (response[offset] == 0x30.toByte()) {
                    offset += 2 // Skip varbind list sequence
                    if (response[offset] == 0x30.toByte()) {
                        offset += 2 // Skip varbind sequence

                        val (oid, oidEnd) = decodeOid(response, offset)
                        offset = oidEnd

                        val (value, _) = decodeValue(response, offset)

                        if (oid == "1.3.6.1.6.3.15.1.1.2.0") {
                            return SnmpResult.EndOfMib
                        }

                        return SnmpResult.Success(
                            oid = oid,
                            value = value,
                            type = getValueType(response[offset])
                        )
                    }
                }
            }

            return SnmpResult.Error("Failed to parse response")
        } catch (e: Exception) {
            Timber.e(e, "Error parsing SNMP response")
            return SnmpResult.Error("Parse error: ${e.message}")
        }
    }

    private fun skipTlv(data: ByteArray, offset: Int): Int {
        if (offset >= data.size) return 0
        val length = data[offset + 1].toInt() and 0xFF
        return 2 + length
    }

    private fun decodeInteger(data: ByteArray, offset: Int): Pair<Int, Int> {
        if (data[offset] != 0x02.toByte()) return 0 to offset + 2
        val length = data[offset + 1].toInt() and 0xFF
        var value = 0
        for (i in 0 until length) {
            value = (value shl 8) or (data[offset + 2 + i].toInt() and 0xFF)
        }
        return value to (offset + 2 + length)
    }

    private fun decodeOid(data: ByteArray, offset: Int): Pair<String, Int> {
        if (data[offset] != 0x06.toByte()) return "" to offset + 2
        val length = data[offset + 1].toInt() and 0xFF
        val oidBytes = data.copyOfRange(offset + 2, offset + 2 + length)

        val parts = mutableListOf<Int>()
        parts.add(oidBytes[0].toInt() and 0xFF / 40)
        parts.add(oidBytes[0].toInt() and 0xFF % 40)

        var value = 0
        for (i in 1 until oidBytes.size) {
            val byte = oidBytes[i].toInt() and 0xFF
            value = (value shl 7) or (byte and 0x7F)
            if (byte and 0x80 == 0) {
                parts.add(value)
                value = 0
            }
        }

        return parts.joinToString(".") to (offset + 2 + length)
    }

    private fun decodeValue(data: ByteArray, offset: Int): Pair<String, Int> {
        val type = data[offset]
        val length = data[offset + 1].toInt() and 0xFF
        val valueBytes = data.copyOfRange(offset + 2, offset + 2 + length)

        val value = when (type.toInt() and 0xFF) {
            0x02 -> { // Integer
                var v = 0
                for (b in valueBytes) {
                    v = (v shl 8) or (b.toInt() and 0xFF)
                }
                v.toString()
            }
            0x04 -> String(valueBytes) // Octet String
            0x06 -> { // OID
                val (oid, _) = decodeOid(data, offset)
                oid
            }
            0x40 -> { // IpAddress
                valueBytes.joinToString(".") { (it.toInt() and 0xFF).toString() }
            }
            0x41, 0x42, 0x43, 0x46 -> { // Counter, Gauge, TimeTicks, Counter64
                var v = 0L
                for (b in valueBytes) {
                    v = (v shl 8) or (b.toLong() and 0xFF)
                }
                v.toString()
            }
            0x05 -> "NULL"
            else -> valueBytes.joinToString("") { "%02X".format(it) }
        }

        return value to (offset + 2 + length)
    }

    private fun getValueType(typeByte: Byte): String {
        return when (typeByte.toInt() and 0xFF) {
            0x02 -> "Integer"
            0x04 -> "OctetString"
            0x05 -> "Null"
            0x06 -> "ObjectIdentifier"
            0x40 -> "IpAddress"
            0x41 -> "Counter32"
            0x42 -> "Gauge32"
            0x43 -> "TimeTicks"
            0x44 -> "Opaque"
            0x46 -> "Counter64"
            else -> "Unknown"
        }
    }

    private fun parseUptime(ticks: String): Long {
        return try {
            ticks.toLong() / 100 // Convert centiseconds to seconds
        } catch (e: Exception) {
            0L
        }
    }
}

/**
 * SNMP version.
 */
enum class SnmpVersion(val value: Int) {
    V1(0),
    V2C(1)
}

/**
 * SNMP query result.
 */
sealed class SnmpResult {
    data class Success(
        val oid: String,
        val value: String,
        val type: String
    ) : SnmpResult()

    data class Timeout(val host: String, val oid: String) : SnmpResult()
    data object EndOfMib : SnmpResult()
    data class Error(val message: String, val exception: Throwable? = null) : SnmpResult()
}

/**
 * SNMP device.
 */
@Serializable
data class SnmpDevice(
    val id: String = UUID.randomUUID().toString(),
    val host: String,
    val port: Int = 161,
    val community: String = "public",
    val version: SnmpVersion = SnmpVersion.V2C,
    val sysDescr: String? = null,
    val sysName: String? = null,
    val discoveredAt: Long = System.currentTimeMillis()
)

/**
 * System information.
 */
@Serializable
data class SystemInfo(
    val host: String,
    val description: String?,
    val name: String?,
    val location: String?,
    val contact: String?,
    val uptime: Long?
)

/**
 * Monitored OID.
 */
@Serializable
data class MonitoredOid(
    val id: String = UUID.randomUUID().toString(),
    val host: String,
    val oid: String,
    val community: String,
    val intervalMs: Long,
    val lastValue: String? = null,
    val lastUpdate: Long? = null
)
