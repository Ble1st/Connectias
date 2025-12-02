package com.ble1st.connectias.feature.hardware.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import com.ble1st.connectias.feature.hardware.models.BeaconInfo
import com.ble1st.connectias.feature.hardware.models.BeaconType
import com.ble1st.connectias.feature.hardware.models.BluetoothCharacteristicInfo
import com.ble1st.connectias.feature.hardware.models.BluetoothConnectionResult
import com.ble1st.connectias.feature.hardware.models.BluetoothDeviceInfo
import com.ble1st.connectias.feature.hardware.models.BluetoothDeviceType
import com.ble1st.connectias.feature.hardware.models.BluetoothScanResult
import com.ble1st.connectias.feature.hardware.models.BluetoothServiceInfo
import com.ble1st.connectias.feature.hardware.models.BondState
import com.ble1st.connectias.feature.hardware.models.ScanMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

/**
 * Provider for Bluetooth analysis functionality.
 *
 * Features:
 * - BLE device scanning
 * - Classic Bluetooth scanning
 * - GATT service discovery
 * - Beacon detection (iBeacon, Eddystone)
 * - Distance estimation
 */
@Singleton
class BluetoothAnalyzerProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bleScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDeviceInfo>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDeviceInfo>> = _discoveredDevices.asStateFlow()

    private val _connectedDevice = MutableStateFlow<BluetoothDeviceInfo?>(null)
    val connectedDevice: StateFlow<BluetoothDeviceInfo?> = _connectedDevice.asStateFlow()

    private var currentGatt: BluetoothGatt? = null

    companion object {
        // iBeacon constants
        private const val IBEACON_PREFIX = 0x0215
        private val IBEACON_COMPANY_ID = byteArrayOf(0x4C, 0x00) // Apple

        // Eddystone constants
        private val EDDYSTONE_SERVICE_UUID = UUID.fromString("0000FEAA-0000-1000-8000-00805F9B34FB")
        private const val EDDYSTONE_UID_FRAME = 0x00.toByte()
        private const val EDDYSTONE_URL_FRAME = 0x10.toByte()
        private const val EDDYSTONE_TLM_FRAME = 0x20.toByte()
    }

    /**
     * Checks if Bluetooth is available.
     */
    fun isBluetoothAvailable(): Boolean = bluetoothAdapter != null

    /**
     * Checks if Bluetooth is enabled.
     */
    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled ?: false

    /**
     * Checks if BLE is supported.
     */
    fun isBleSupported(): Boolean = 
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_BLUETOOTH_LE)

    /**
     * Scans for BLE devices.
     */
    @SuppressLint("MissingPermission")
    fun scanBleDevices(
        mode: ScanMode = ScanMode.BALANCED,
        filterUuids: List<UUID>? = null
    ): Flow<BluetoothScanResult> = callbackFlow {
        val scanner = bleScanner ?: run {
            trySend(BluetoothScanResult.Error("BLE scanner not available"))
            close()
            return@callbackFlow
        }

        _isScanning.value = true
        _discoveredDevices.value = emptyList()

        val scanSettings = ScanSettings.Builder()
            .setScanMode(when (mode) {
                ScanMode.LOW_POWER -> ScanSettings.SCAN_MODE_LOW_POWER
                ScanMode.BALANCED -> ScanSettings.SCAN_MODE_BALANCED
                ScanMode.LOW_LATENCY -> ScanSettings.SCAN_MODE_LOW_LATENCY
            })
            .setReportDelay(0)
            .build()

        val scanFilters = filterUuids?.map { uuid ->
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(uuid))
                .build()
        } ?: emptyList()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val deviceInfo = parseBleScanResult(result)
                updateDiscoveredDevice(deviceInfo)

                trySend(BluetoothScanResult.DeviceFound(deviceInfo))

                // Check for beacons
                parseBeacon(result)?.let { beacon ->
                    trySend(BluetoothScanResult.BeaconFound(beacon))
                }
            }

            override fun onBatchScanResults(results: List<ScanResult>) {
                results.forEach { result ->
                    val deviceInfo = parseBleScanResult(result)
                    updateDiscoveredDevice(deviceInfo)
                    trySend(BluetoothScanResult.DeviceFound(deviceInfo))
                }
            }

            override fun onScanFailed(errorCode: Int) {
                val errorMessage = when (errorCode) {
                    SCAN_FAILED_ALREADY_STARTED -> "Scan already started"
                    SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "App registration failed"
                    SCAN_FAILED_INTERNAL_ERROR -> "Internal error"
                    SCAN_FAILED_FEATURE_UNSUPPORTED -> "Feature unsupported"
                    else -> "Unknown error: $errorCode"
                }
                Timber.e("BLE scan failed: $errorMessage")
                trySend(BluetoothScanResult.Error(errorMessage))
            }
        }

        trySend(BluetoothScanResult.ScanStarted(mode))

        try {
            if (scanFilters.isEmpty()) {
                scanner.startScan(null, scanSettings, callback)
            } else {
                scanner.startScan(scanFilters, scanSettings, callback)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error starting BLE scan")
            trySend(BluetoothScanResult.Error(e.message ?: "Unknown error"))
        }

        awaitClose {
            try {
                scanner.stopScan(callback)
            } catch (e: Exception) {
                Timber.w(e, "Error stopping BLE scan")
            }
            _isScanning.value = false
            trySend(BluetoothScanResult.ScanStopped)
        }
    }

    /**
     * Connects to a BLE device and discovers services.
     */
    @SuppressLint("MissingPermission")
    fun connectAndDiscoverServices(address: String): Flow<BluetoothConnectionResult> = callbackFlow {
        val adapter = bluetoothAdapter ?: run {
            trySend(BluetoothConnectionResult.Error("Bluetooth not available"))
            close()
            return@callbackFlow
        }

        val device = try {
            adapter.getRemoteDevice(address)
        } catch (e: Exception) {
            trySend(BluetoothConnectionResult.Error("Invalid device address", e))
            close()
            return@callbackFlow
        }

        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Timber.d("Connected to GATT server")
                        currentGatt = gatt
                        gatt.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Timber.d("Disconnected from GATT server")
                        currentGatt = null
                        _connectedDevice.value = null
                        trySend(BluetoothConnectionResult.Disconnected(address))
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val services = gatt.services.map { parseGattService(it) }
                    val deviceInfo = BluetoothDeviceInfo(
                        address = address,
                        name = device.name,
                        deviceType = BluetoothDeviceType.LE,
                        bondState = parseBondState(device.bondState),
                        services = services
                    )
                    _connectedDevice.value = deviceInfo
                    trySend(BluetoothConnectionResult.Connected(deviceInfo))
                } else {
                    trySend(BluetoothConnectionResult.Error("Service discovery failed: $status"))
                }
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, gattCallback)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error connecting to device")
            trySend(BluetoothConnectionResult.Error(e.message ?: "Unknown error", e))
        }

        awaitClose {
            currentGatt?.disconnect()
            currentGatt?.close()
            currentGatt = null
        }
    }

    /**
     * Disconnects from the current device.
     */
    @SuppressLint("MissingPermission")
    fun disconnect() {
        currentGatt?.disconnect()
        currentGatt?.close()
        currentGatt = null
        _connectedDevice.value = null
    }

    /**
     * Gets paired devices.
     */
    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDeviceInfo> {
        return bluetoothAdapter?.bondedDevices?.map { device ->
            BluetoothDeviceInfo(
                address = device.address,
                name = device.name,
                deviceType = parseDeviceType(device.type),
                bondState = BondState.BONDED
            )
        } ?: emptyList()
    }

    /**
     * Estimates distance based on RSSI.
     */
    fun estimateDistance(rssi: Int, txPower: Int = -59): Double {
        if (rssi == 0) return -1.0

        val ratio = rssi * 1.0 / txPower
        return if (ratio < 1.0) {
            ratio.pow(10.0)
        } else {
            0.89976 * ratio.pow(7.7095) + 0.111
        }
    }

    /**
     * Stops any ongoing scan.
     */
    @SuppressLint("MissingPermission")
    fun stopScan() {
        _isScanning.value = false
    }

    // Helper functions

    @SuppressLint("MissingPermission")
    private fun parseBleScanResult(result: ScanResult): BluetoothDeviceInfo {
        val device = result.device
        return BluetoothDeviceInfo(
            address = device.address,
            name = result.scanRecord?.deviceName ?: device.name,
            deviceType = BluetoothDeviceType.LE,
            bondState = parseBondState(device.bondState),
            rssi = result.rssi,
            txPower = result.scanRecord?.txPowerLevel ?: -1,
            isConnectable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                result.isConnectable
            } else true
        )
    }

    private fun parseBeacon(result: ScanResult): BeaconInfo? {
        val scanRecord = result.scanRecord ?: return null
        val bytes = scanRecord.bytes ?: return null

        // Try to parse as iBeacon
        parseIBeacon(bytes, result.rssi, result.device.address)?.let { return it }

        // Try to parse as Eddystone
        parseEddystone(scanRecord, result.rssi, result.device.address)?.let { return it }

        return null
    }

    private fun parseIBeacon(bytes: ByteArray, rssi: Int, address: String): BeaconInfo? {
        if (bytes.size < 25) return null

        // Find iBeacon prefix
        var offset = 0
        while (offset < bytes.size - 25) {
            if (bytes[offset] == 0x02.toByte() && 
                bytes[offset + 1] == 0x15.toByte()) {
                break
            }
            offset++
        }

        if (offset >= bytes.size - 25) return null

        val uuid = ByteBuffer.wrap(bytes, offset + 2, 16)
            .let { buffer ->
                val high = buffer.long
                val low = buffer.long
                UUID(high, low).toString()
            }

        val major = ByteBuffer.wrap(bytes, offset + 18, 2)
            .order(ByteOrder.BIG_ENDIAN)
            .short.toInt() and 0xFFFF

        val minor = ByteBuffer.wrap(bytes, offset + 20, 2)
            .order(ByteOrder.BIG_ENDIAN)
            .short.toInt() and 0xFFFF

        val txPower = bytes[offset + 22].toInt()
        val distance = estimateDistance(rssi, txPower)

        return BeaconInfo(
            type = BeaconType.IBEACON,
            uuid = uuid,
            major = major,
            minor = minor,
            namespace = null,
            instance = null,
            rssi = rssi,
            distance = distance,
            deviceAddress = address
        )
    }

    private fun parseEddystone(
        scanRecord: android.bluetooth.le.ScanRecord,
        rssi: Int,
        address: String
    ): BeaconInfo? {
        val serviceData = scanRecord.getServiceData(ParcelUuid(EDDYSTONE_SERVICE_UUID))
            ?: return null

        if (serviceData.isEmpty()) return null

        return when (serviceData[0]) {
            EDDYSTONE_UID_FRAME -> {
                if (serviceData.size < 18) return null
                val namespace = serviceData.sliceArray(2..11).toHexString()
                val instance = serviceData.sliceArray(12..17).toHexString()
                val txPower = serviceData[1].toInt()
                val distance = estimateDistance(rssi, txPower)

                BeaconInfo(
                    type = BeaconType.EDDYSTONE_UID,
                    uuid = null,
                    major = null,
                    minor = null,
                    namespace = namespace,
                    instance = instance,
                    rssi = rssi,
                    distance = distance,
                    deviceAddress = address
                )
            }
            EDDYSTONE_URL_FRAME -> {
                BeaconInfo(
                    type = BeaconType.EDDYSTONE_URL,
                    uuid = null,
                    major = null,
                    minor = null,
                    namespace = null,
                    instance = null,
                    rssi = rssi,
                    distance = estimateDistance(rssi),
                    deviceAddress = address
                )
            }
            EDDYSTONE_TLM_FRAME -> {
                BeaconInfo(
                    type = BeaconType.EDDYSTONE_TLM,
                    uuid = null,
                    major = null,
                    minor = null,
                    namespace = null,
                    instance = null,
                    rssi = rssi,
                    distance = estimateDistance(rssi),
                    deviceAddress = address
                )
            }
            else -> null
        }
    }

    private fun parseGattService(service: BluetoothGattService): BluetoothServiceInfo {
        return BluetoothServiceInfo(
            uuid = service.uuid.toString(),
            name = getServiceName(service.uuid),
            characteristics = service.characteristics.map { parseCharacteristic(it) }
        )
    }

    private fun parseCharacteristic(characteristic: BluetoothGattCharacteristic): BluetoothCharacteristicInfo {
        val properties = characteristic.properties
        return BluetoothCharacteristicInfo(
            uuid = characteristic.uuid.toString(),
            name = getCharacteristicName(characteristic.uuid),
            properties = properties,
            isReadable = properties and BluetoothGattCharacteristic.PROPERTY_READ != 0,
            isWritable = properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or 
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0,
            isNotifiable = properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
        )
    }

    private fun parseBondState(state: Int): BondState {
        return when (state) {
            BluetoothDevice.BOND_NONE -> BondState.NONE
            BluetoothDevice.BOND_BONDING -> BondState.BONDING
            BluetoothDevice.BOND_BONDED -> BondState.BONDED
            else -> BondState.NONE
        }
    }

    private fun parseDeviceType(type: Int): BluetoothDeviceType {
        return when (type) {
            BluetoothDevice.DEVICE_TYPE_CLASSIC -> BluetoothDeviceType.CLASSIC
            BluetoothDevice.DEVICE_TYPE_LE -> BluetoothDeviceType.LE
            BluetoothDevice.DEVICE_TYPE_DUAL -> BluetoothDeviceType.DUAL
            else -> BluetoothDeviceType.UNKNOWN
        }
    }

    private fun updateDiscoveredDevice(device: BluetoothDeviceInfo) {
        _discoveredDevices.update { devices ->
            val existingIndex = devices.indexOfFirst { it.address == device.address }
            if (existingIndex >= 0) {
                devices.toMutableList().apply { set(existingIndex, device) }
            } else {
                devices + device
            }
        }
    }

    private fun getServiceName(uuid: UUID): String? {
        return KNOWN_SERVICES[uuid.toString().uppercase()]
    }

    private fun getCharacteristicName(uuid: UUID): String? {
        return KNOWN_CHARACTERISTICS[uuid.toString().uppercase()]
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02X".format(it) }
    }

    private val KNOWN_SERVICES = mapOf(
        "00001800-0000-1000-8000-00805F9B34FB" to "Generic Access",
        "00001801-0000-1000-8000-00805F9B34FB" to "Generic Attribute",
        "0000180A-0000-1000-8000-00805F9B34FB" to "Device Information",
        "0000180F-0000-1000-8000-00805F9B34FB" to "Battery Service",
        "00001809-0000-1000-8000-00805F9B34FB" to "Health Thermometer",
        "0000180D-0000-1000-8000-00805F9B34FB" to "Heart Rate",
        "00001802-0000-1000-8000-00805F9B34FB" to "Immediate Alert",
        "00001803-0000-1000-8000-00805F9B34FB" to "Link Loss",
        "00001804-0000-1000-8000-00805F9B34FB" to "Tx Power"
    )

    private val KNOWN_CHARACTERISTICS = mapOf(
        "00002A00-0000-1000-8000-00805F9B34FB" to "Device Name",
        "00002A01-0000-1000-8000-00805F9B34FB" to "Appearance",
        "00002A19-0000-1000-8000-00805F9B34FB" to "Battery Level",
        "00002A29-0000-1000-8000-00805F9B34FB" to "Manufacturer Name",
        "00002A24-0000-1000-8000-00805F9B34FB" to "Model Number",
        "00002A25-0000-1000-8000-00805F9B34FB" to "Serial Number",
        "00002A26-0000-1000-8000-00805F9B34FB" to "Firmware Revision",
        "00002A27-0000-1000-8000-00805F9B34FB" to "Hardware Revision",
        "00002A28-0000-1000-8000-00805F9B34FB" to "Software Revision"
    )
}
