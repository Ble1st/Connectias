package com.ble1st.connectias.feature.deviceinfo.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for battery analysis and monitoring.
 */
@Singleton
class BatteryAnalyzerProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

    /**
     * Gets current battery information.
     */
    suspend fun getBatteryInfo(): BatteryInfo = withContext(Dispatchers.IO) {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return@withContext BatteryInfo.default()

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percentage = if (level >= 0 && scale > 0) {
            (level * 100 / scale.toFloat()).toInt()
        } else {
            -1
        }

        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
        val healthStatus = when (health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> HealthStatus.GOOD
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> HealthStatus.OVERHEAT
            BatteryManager.BATTERY_HEALTH_DEAD -> HealthStatus.DEAD
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> HealthStatus.OVER_VOLTAGE
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> HealthStatus.FAILURE
            BatteryManager.BATTERY_HEALTH_COLD -> HealthStatus.COLD
            else -> HealthStatus.UNKNOWN
        }

        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val chargeType = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> ChargeType.AC
            BatteryManager.BATTERY_PLUGGED_USB -> ChargeType.USB
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> ChargeType.WIRELESS
            else -> ChargeType.NONE
        }

        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        val temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10.0f // in Celsius

        // Get battery capacity (mAh) if available
        val capacity = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        } else {
            -1
        }

        // Get current average (microamps)
        val currentAverage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
        } else {
            0
        }

        BatteryInfo(
            percentage = percentage,
            isCharging = isCharging,
            healthStatus = healthStatus,
            chargeType = chargeType,
            voltage = voltage,
            temperature = temperature,
            capacity = capacity,
            currentAverage = currentAverage,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * Monitors battery information continuously.
     */
    fun monitorBattery(intervalMs: Long = 5000): Flow<BatteryInfo> = flow {
        while (true) {
            kotlinx.coroutines.delay(intervalMs)
            emit(getBatteryInfo())
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Calculates estimated time until full charge (if charging).
     */
    suspend fun estimateTimeToFullCharge(): Long? = withContext(Dispatchers.Default) {
        val info = getBatteryInfo()
        if (!info.isCharging || info.currentAverage <= 0 || info.percentage >= 100) {
            return@withContext null
        }

        val remainingPercentage = 100 - info.percentage
        val remainingCapacity = (info.capacity * remainingPercentage / 100.0).toLong()
        val timeMs = (remainingCapacity * 3600000L) / info.currentAverage // Convert to milliseconds
        timeMs
    }

    /**
     * Calculates estimated time until empty (if discharging).
     */
    suspend fun estimateTimeToEmpty(): Long? = withContext(Dispatchers.Default) {
        val info = getBatteryInfo()
        if (info.isCharging || info.currentAverage >= 0 || info.percentage <= 0) {
            return@withContext null
        }

        val currentDischarge = kotlin.math.abs(info.currentAverage)
        if (currentDischarge == 0) return@withContext null

        val remainingCapacity = (info.capacity * info.percentage / 100.0).toLong()
        val timeMs = (remainingCapacity * 3600000L) / currentDischarge
        timeMs
    }
}

/**
 * Battery information.
 */
data class BatteryInfo(
    val percentage: Int,
    val isCharging: Boolean,
    val healthStatus: HealthStatus,
    val chargeType: ChargeType,
    val voltage: Int,
    val temperature: Float,
    val capacity: Int,
    val currentAverage: Int,
    val timestamp: Long
) {
    companion object {
        fun default() = BatteryInfo(
            percentage = -1,
            isCharging = false,
            healthStatus = HealthStatus.UNKNOWN,
            chargeType = ChargeType.NONE,
            voltage = -1,
            temperature = -1f,
            capacity = -1,
            currentAverage = 0,
            timestamp = System.currentTimeMillis()
        )
    }
}

/**
 * Battery health status.
 */
enum class HealthStatus {
    GOOD,
    OVERHEAT,
    DEAD,
    OVER_VOLTAGE,
    FAILURE,
    COLD,
    UNKNOWN
}

/**
 * Charge type.
 */
enum class ChargeType {
    AC,
    USB,
    WIRELESS,
    NONE
}

