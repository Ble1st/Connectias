package com.ble1st.connectias.api

data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdkVersion: Int,
    val architecture: String
)

data class CpuInfo(
    val cores: Int,
    val maxFrequency: Long,
    val currentFrequency: Long,
    val architecture: String
)

data class MemoryInfo(
    val totalMemory: Long,
    val availableMemory: Long,
    val usedMemory: Long
)

data class NetworkInfo(
    val isConnected: Boolean,
    val connectionType: String,
    val ipAddress: String?,
    val macAddress: String?
)

interface SystemInfoService {
    fun getDeviceInfo(): DeviceInfo
    fun getCpuInfo(): CpuInfo
    fun getMemoryInfo(): MemoryInfo
    fun getNetworkInfo(): NetworkInfo
}
