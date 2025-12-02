package com.ble1st.connectias.feature.deviceinfo.thermal

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import timber.log.Timber
import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for thermal monitoring functionality.
 *
 * Features:
 * - CPU temperature monitoring
 * - Battery temperature monitoring
 * - Thermal zone reading
 * - Temperature alerts
 * - Throttling detection
 */
@Singleton
class ThermalMonitorProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val _currentTemperatures = MutableStateFlow<ThermalState>(ThermalState())
    val currentTemperatures: StateFlow<ThermalState> = _currentTemperatures.asStateFlow()

    private val _alerts = MutableStateFlow<List<ThermalAlert>>(emptyList())
    val alerts: StateFlow<List<ThermalAlert>> = _alerts.asStateFlow()

    // Common thermal zone paths
    private val thermalZonePaths = listOf(
        "/sys/class/thermal/thermal_zone0/temp",
        "/sys/class/thermal/thermal_zone1/temp",
        "/sys/class/thermal/thermal_zone2/temp",
        "/sys/class/thermal/thermal_zone3/temp",
        "/sys/class/thermal/thermal_zone4/temp",
        "/sys/class/thermal/thermal_zone5/temp",
        "/sys/devices/virtual/thermal/thermal_zone0/temp",
        "/sys/devices/system/cpu/cpu0/cpufreq/cpu_temp",
        "/sys/devices/platform/s5p-tmu/curr_temp",
        "/sys/kernel/debug/tegra_thermal/temp_tj"
    )

    /**
     * Gets current thermal state.
     */
    suspend fun getThermalState(): ThermalState = withContext(Dispatchers.IO) {
        val batteryTemp = getBatteryTemperature()
        val cpuTemp = getCpuTemperature()
        val thermalZones = readThermalZones()
        val throttlingStatus = getThrottlingStatus()

        val state = ThermalState(
            batteryTemperature = batteryTemp,
            cpuTemperature = cpuTemp,
            thermalZones = thermalZones,
            isThrottling = throttlingStatus != ThrottlingStatus.NONE,
            throttlingStatus = throttlingStatus,
            timestamp = System.currentTimeMillis()
        )

        _currentTemperatures.value = state
        checkAlerts(state)
        state
    }

    /**
     * Gets battery temperature.
     */
    suspend fun getBatteryTemperature(): Float = withContext(Dispatchers.IO) {
        try {
            val batteryIntent = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )

            val temperature = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            temperature / 10f // Convert to degrees Celsius
        } catch (e: Exception) {
            Timber.e(e, "Error getting battery temperature")
            0f
        }
    }

    /**
     * Gets CPU temperature.
     */
    suspend fun getCpuTemperature(): Float? = withContext(Dispatchers.IO) {
        for (path in thermalZonePaths) {
            try {
                val file = File(path)
                if (file.exists() && file.canRead()) {
                    val temp = file.readText().trim().toIntOrNull()
                    if (temp != null && temp > 0) {
                        // Some devices report in millidegrees
                        return@withContext if (temp > 1000) temp / 1000f else temp.toFloat()
                    }
                }
            } catch (e: Exception) {
                // Try next path
            }
        }
        null
    }

    /**
     * Reads all thermal zones.
     */
    suspend fun readThermalZones(): List<ThermalZone> = withContext(Dispatchers.IO) {
        val zones = mutableListOf<ThermalZone>()
        val thermalDir = File("/sys/class/thermal/")

        if (thermalDir.exists()) {
            thermalDir.listFiles()?.filter { it.name.startsWith("thermal_zone") }?.forEach { zoneDir ->
                try {
                    val tempFile = File(zoneDir, "temp")
                    val typeFile = File(zoneDir, "type")

                    if (tempFile.exists()) {
                        val temp = tempFile.readText().trim().toIntOrNull() ?: 0
                        val type = if (typeFile.exists()) typeFile.readText().trim() else "unknown"

                        zones.add(
                            ThermalZone(
                                name = zoneDir.name,
                                type = type,
                                temperature = if (temp > 1000) temp / 1000f else temp.toFloat()
                            )
                        )
                    }
                } catch (e: Exception) {
                    // Skip this zone
                }
            }
        }

        zones
    }

    /**
     * Gets throttling status.
     */
    suspend fun getThrottlingStatus(): ThrottlingStatus = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            when (powerManager.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> ThrottlingStatus.NONE
                PowerManager.THERMAL_STATUS_LIGHT -> ThrottlingStatus.LIGHT
                PowerManager.THERMAL_STATUS_MODERATE -> ThrottlingStatus.MODERATE
                PowerManager.THERMAL_STATUS_SEVERE -> ThrottlingStatus.SEVERE
                PowerManager.THERMAL_STATUS_CRITICAL -> ThrottlingStatus.CRITICAL
                PowerManager.THERMAL_STATUS_EMERGENCY -> ThrottlingStatus.EMERGENCY
                PowerManager.THERMAL_STATUS_SHUTDOWN -> ThrottlingStatus.SHUTDOWN
                else -> ThrottlingStatus.UNKNOWN
            }
        } else {
            ThrottlingStatus.UNKNOWN
        }
    }

    /**
     * Gets current CPU frequencies.
     */
    suspend fun getCpuFrequencies(): List<CpuFrequency> = withContext(Dispatchers.IO) {
        val frequencies = mutableListOf<CpuFrequency>()
        val cpuDir = File("/sys/devices/system/cpu/")

        if (cpuDir.exists()) {
            cpuDir.listFiles()?.filter { it.name.matches(Regex("cpu\\d+")) }?.forEach { cpuPath ->
                try {
                    val freqPath = File(cpuPath, "cpufreq/scaling_cur_freq")
                    val maxPath = File(cpuPath, "cpufreq/scaling_max_freq")
                    val minPath = File(cpuPath, "cpufreq/scaling_min_freq")

                    if (freqPath.exists()) {
                        val current = freqPath.readText().trim().toLongOrNull() ?: 0
                        val max = if (maxPath.exists()) maxPath.readText().trim().toLongOrNull() ?: 0 else 0
                        val min = if (minPath.exists()) minPath.readText().trim().toLongOrNull() ?: 0 else 0

                        frequencies.add(
                            CpuFrequency(
                                cpuId = cpuPath.name.removePrefix("cpu").toIntOrNull() ?: 0,
                                currentFrequency = current / 1000, // Convert to MHz
                                maxFrequency = max / 1000,
                                minFrequency = min / 1000
                            )
                        )
                    }
                } catch (e: Exception) {
                    // Skip this CPU
                }
            }
        }

        frequencies.sortedBy { it.cpuId }
    }

    /**
     * Monitors temperatures continuously.
     */
    fun monitorTemperatures(intervalMs: Long = 5000): Flow<ThermalState> = flow {
        while (true) {
            emit(getThermalState())
            delay(intervalMs)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Adds a temperature alert.
     */
    fun addAlert(alert: ThermalAlert) {
        _alerts.value = _alerts.value + alert
    }

    /**
     * Removes an alert.
     */
    fun removeAlert(alertId: String) {
        _alerts.value = _alerts.value.filter { it.id != alertId }
    }

    /**
     * Checks and triggers alerts.
     */
    private fun checkAlerts(state: ThermalState) {
        val triggeredAlerts = mutableListOf<TriggeredAlert>()

        for (alert in _alerts.value) {
            val temperature = when (alert.source) {
                ThermalSource.BATTERY -> state.batteryTemperature
                ThermalSource.CPU -> state.cpuTemperature ?: 0f
                ThermalSource.THERMAL_ZONE -> {
                    state.thermalZones.find { it.name == alert.zoneName }?.temperature ?: 0f
                }
            }

            if (temperature >= alert.thresholdCelsius && !alert.triggered) {
                triggeredAlerts.add(
                    TriggeredAlert(
                        alert = alert,
                        currentTemperature = temperature,
                        triggeredAt = System.currentTimeMillis()
                    )
                )

                // Mark as triggered
                _alerts.value = _alerts.value.map {
                    if (it.id == alert.id) it.copy(triggered = true) else it
                }
            } else if (temperature < alert.thresholdCelsius - 5 && alert.triggered) {
                // Reset alert if temperature drops 5 degrees below threshold
                _alerts.value = _alerts.value.map {
                    if (it.id == alert.id) it.copy(triggered = false) else it
                }
            }
        }
    }

    /**
     * Gets temperature history summary.
     */
    fun getTemperatureSummary(): ThermalSummary {
        val state = _currentTemperatures.value
        return ThermalSummary(
            batteryTemperature = state.batteryTemperature,
            cpuTemperature = state.cpuTemperature,
            zoneCount = state.thermalZones.size,
            maxZoneTemp = state.thermalZones.maxOfOrNull { it.temperature } ?: 0f,
            isThrottling = state.isThrottling,
            throttlingStatus = state.throttlingStatus,
            batteryStatus = when {
                state.batteryTemperature < 30 -> TemperatureStatus.NORMAL
                state.batteryTemperature < 40 -> TemperatureStatus.WARM
                state.batteryTemperature < 45 -> TemperatureStatus.HOT
                else -> TemperatureStatus.CRITICAL
            }
        )
    }
}

/**
 * Thermal state.
 */
@Serializable
data class ThermalState(
    val batteryTemperature: Float = 0f,
    val cpuTemperature: Float? = null,
    val thermalZones: List<ThermalZone> = emptyList(),
    val isThrottling: Boolean = false,
    val throttlingStatus: ThrottlingStatus = ThrottlingStatus.UNKNOWN,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Thermal zone information.
 */
@Serializable
data class ThermalZone(
    val name: String,
    val type: String,
    val temperature: Float
)

/**
 * CPU frequency information.
 */
@Serializable
data class CpuFrequency(
    val cpuId: Int,
    val currentFrequency: Long,
    val maxFrequency: Long,
    val minFrequency: Long
) {
    val utilizationPercentage: Float
        get() = if (maxFrequency > 0) currentFrequency.toFloat() / maxFrequency * 100 else 0f
}

/**
 * Throttling status.
 */
enum class ThrottlingStatus {
    NONE,
    LIGHT,
    MODERATE,
    SEVERE,
    CRITICAL,
    EMERGENCY,
    SHUTDOWN,
    UNKNOWN
}

/**
 * Temperature status.
 */
enum class TemperatureStatus {
    NORMAL,
    WARM,
    HOT,
    CRITICAL
}

/**
 * Thermal source.
 */
enum class ThermalSource {
    BATTERY,
    CPU,
    THERMAL_ZONE
}

/**
 * Thermal alert configuration.
 */
@Serializable
data class ThermalAlert(
    val id: String = java.util.UUID.randomUUID().toString(),
    val source: ThermalSource,
    val zoneName: String? = null,
    val thresholdCelsius: Float,
    val triggered: Boolean = false
)

/**
 * Triggered alert info.
 */
@Serializable
data class TriggeredAlert(
    val alert: ThermalAlert,
    val currentTemperature: Float,
    val triggeredAt: Long
)

/**
 * Thermal summary.
 */
@Serializable
data class ThermalSummary(
    val batteryTemperature: Float,
    val cpuTemperature: Float?,
    val zoneCount: Int,
    val maxZoneTemp: Float,
    val isThrottling: Boolean,
    val throttlingStatus: ThrottlingStatus,
    val batteryStatus: TemperatureStatus
)
