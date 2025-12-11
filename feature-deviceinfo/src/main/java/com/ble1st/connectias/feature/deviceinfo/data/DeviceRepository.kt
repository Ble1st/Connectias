package com.ble1st.connectias.feature.deviceinfo.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.app.ActivityManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import java.io.File
import kotlin.math.roundToInt

data class BatteryInfo(
    val level: Int,
    val status: String,
    val plugged: String,
    val health: String,
    val technology: String,
    val temperature: Float, // Battery temp
    val voltage: Int
)

data class StorageInfo(
    val totalRam: Long,
    val availableRam: Long,
    val totalInternal: Long,
    val availableInternal: Long
)

@Singleton
class DeviceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val temperatureDao: TemperatureDao
) {

    val temperatureHistory: Flow<List<TemperatureEntity>> = temperatureDao.getAllHistory()

    fun getBatteryInfo(): BatteryInfo {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level != -1 && scale != -1) (level / scale.toFloat() * 100).roundToInt() else 0

        val status = when (intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
            else -> "Unknown"
        }
        
        val plugged = when (intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "Battery"
        }
        
        val health = when (intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            else -> "Unknown"
        }
        
        val technology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Unknown"
        val temp = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0

        return BatteryInfo(batteryPct, status, plugged, health, technology, temp, voltage)
    }

    fun getStorageInfo(): StorageInfo {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        
        val dataDir = Environment.getDataDirectory()
        val stat = android.os.StatFs(dataDir.path)
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availableBlocks = stat.availableBlocksLong
        
        return StorageInfo(
            totalRam = memInfo.totalMem,
            availableRam = memInfo.availMem,
            totalInternal = totalBlocks * blockSize,
            availableInternal = availableBlocks * blockSize
        )
    }
    
    // Simulating CPU temp if not available via standard API (HardwarePropertiesManager requires system app usually)
    // We use battery temp as a proxy or try reading /sys/class/thermal/thermal_zone0/temp
    fun getCpuTemperature(): Float {
        try {
            val process = Runtime.getRuntime().exec("cat /sys/class/thermal/thermal_zone0/temp")
            process.waitFor()
            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            val line = reader.readLine()
            if (line != null) {
                return line.toFloat() / 1000f
            }
        } catch (e: Exception) {
            // Fallback to battery temp
        }
        return getBatteryInfo().temperature
    }

    suspend fun logTemperature() {
        val temp = getCpuTemperature()
        temperatureDao.insert(TemperatureEntity(value = temp, timestamp = System.currentTimeMillis()))
    }
    
    suspend fun clearHistory() {
        temperatureDao.clearAll()
    }
}
