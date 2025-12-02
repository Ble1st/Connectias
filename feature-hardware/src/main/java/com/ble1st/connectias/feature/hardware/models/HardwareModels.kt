package com.ble1st.connectias.feature.hardware.models

import kotlinx.serialization.Serializable

// ============================================================================
// NFC Models
// ============================================================================

/**
 * Information about an NFC tag.
 */
@Serializable
data class NfcTagInfo(
    val id: String,
    val techList: List<String>,
    val type: NfcTagType,
    val maxSize: Int,
    val isWritable: Boolean,
    val canMakeReadOnly: Boolean,
    val ndefRecords: List<NdefRecordInfo> = emptyList()
)

/**
 * NFC tag types.
 */
enum class NfcTagType {
    NDEF,
    NDEF_FORMATABLE,
    MIFARE_CLASSIC,
    MIFARE_ULTRALIGHT,
    NFC_A,
    NFC_B,
    NFC_F,
    NFC_V,
    ISO_DEP,
    UNKNOWN
}

/**
 * NDEF record information.
 */
@Serializable
data class NdefRecordInfo(
    val tnf: Int,
    val type: String,
    val payload: String,
    val recordType: NdefRecordType
)

/**
 * NDEF record types.
 */
enum class NdefRecordType {
    TEXT,
    URI,
    SMART_POSTER,
    MIME,
    EXTERNAL,
    UNKNOWN
}

/**
 * Result of NFC write operation.
 */
sealed class NfcWriteResult {
    data class Success(val bytesWritten: Int) : NfcWriteResult()
    data class Error(val message: String, val exception: Throwable? = null) : NfcWriteResult()
    data object TagNotWritable : NfcWriteResult()
    data object TagTooSmall : NfcWriteResult()
    data object TagLost : NfcWriteResult()
}

/**
 * NFC scan event.
 */
sealed class NfcEvent {
    data class TagDiscovered(val tagInfo: NfcTagInfo) : NfcEvent()
    data class TagLost(val id: String) : NfcEvent()
    data class ReadSuccess(val records: List<NdefRecordInfo>) : NfcEvent()
    data class WriteSuccess(val bytesWritten: Int) : NfcEvent()
    data class Error(val message: String) : NfcEvent()
}

// ============================================================================
// Bluetooth Models
// ============================================================================

/**
 * Bluetooth device information.
 */
@Serializable
data class BluetoothDeviceInfo(
    val address: String,
    val name: String?,
    val deviceType: BluetoothDeviceType,
    val bondState: BondState,
    val rssi: Int? = null,
    val txPower: Int? = null,
    val services: List<BluetoothServiceInfo> = emptyList(),
    val isConnectable: Boolean = true,
    val lastSeen: Long = System.currentTimeMillis()
)

/**
 * Bluetooth device types.
 */
enum class BluetoothDeviceType {
    CLASSIC,
    LE,
    DUAL,
    UNKNOWN
}

/**
 * Bluetooth bond state.
 */
enum class BondState {
    NONE,
    BONDING,
    BONDED
}

/**
 * Bluetooth service information.
 */
@Serializable
data class BluetoothServiceInfo(
    val uuid: String,
    val name: String?,
    val characteristics: List<BluetoothCharacteristicInfo> = emptyList()
)

/**
 * Bluetooth characteristic information.
 */
@Serializable
data class BluetoothCharacteristicInfo(
    val uuid: String,
    val name: String?,
    val properties: Int,
    val isReadable: Boolean,
    val isWritable: Boolean,
    val isNotifiable: Boolean,
    val value: String? = null
)

/**
 * Beacon information.
 */
@Serializable
data class BeaconInfo(
    val type: BeaconType,
    val uuid: String?,
    val major: Int?,
    val minor: Int?,
    val namespace: String?,
    val instance: String?,
    val rssi: Int,
    val distance: Double,
    val deviceAddress: String
)

/**
 * Beacon types.
 */
enum class BeaconType {
    IBEACON,
    EDDYSTONE_UID,
    EDDYSTONE_URL,
    EDDYSTONE_TLM,
    ALTBEACON,
    UNKNOWN
}

/**
 * Bluetooth scan result.
 */
sealed class BluetoothScanResult {
    data class DeviceFound(val device: BluetoothDeviceInfo) : BluetoothScanResult()
    data class DeviceLost(val address: String) : BluetoothScanResult()
    data class BeaconFound(val beacon: BeaconInfo) : BluetoothScanResult()
    data class ScanStarted(val mode: ScanMode) : BluetoothScanResult()
    data object ScanStopped : BluetoothScanResult()
    data class Error(val message: String) : BluetoothScanResult()
}

/**
 * Bluetooth scan mode.
 */
enum class ScanMode {
    LOW_POWER,
    BALANCED,
    LOW_LATENCY
}

/**
 * Bluetooth connection result.
 */
sealed class BluetoothConnectionResult {
    data class Connected(val device: BluetoothDeviceInfo) : BluetoothConnectionResult()
    data class Disconnected(val address: String) : BluetoothConnectionResult()
    data class Error(val message: String, val exception: Throwable? = null) : BluetoothConnectionResult()
}
