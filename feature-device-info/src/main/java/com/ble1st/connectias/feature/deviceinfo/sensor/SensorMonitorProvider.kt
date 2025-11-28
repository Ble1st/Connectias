package com.ble1st.connectias.feature.deviceinfo.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for sensor monitoring.
 */
@Singleton
class SensorMonitorProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    /**
     * Gets list of available sensors.
     */
    suspend fun getAvailableSensors(): List<SensorInfo> = withContext(Dispatchers.IO) {
        try {
            val sensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
            sensors.map { sensor ->
                SensorInfo(
                    name = sensor.name,
                    type = sensor.type,
                    vendor = sensor.vendor,
                    version = sensor.version,
                    maxRange = sensor.maximumRange,
                    resolution = sensor.resolution,
                    power = sensor.power,
                    minDelay = sensor.minDelay
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get available sensors")
            emptyList()
        }
    }

    /**
     * Monitors a specific sensor.
     * 
     * @param sensorType Sensor type (e.g., Sensor.TYPE_ACCELEROMETER)
     * @param samplingPeriodUs Sampling period in microseconds
     */
    fun monitorSensor(
        sensorType: Int,
        samplingPeriodUs: Int = SensorManager.SENSOR_DELAY_NORMAL
    ): Flow<SensorData> = callbackFlow {
        val sensor = sensorManager.getDefaultSensor(sensorType)
        if (sensor == null) {
            close()
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(
                    SensorData(
                        sensorType = event.sensor.type,
                        sensorName = event.sensor.name,
                        values = event.values.toList(),
                        accuracy = event.accuracy,
                        timestamp = event.timestamp
                    )
                )
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                // Handle accuracy changes if needed
            }
        }

        sensorManager.registerListener(listener, sensor, samplingPeriodUs)

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Gets sensor calibration status.
     */
    suspend fun getSensorCalibrationStatus(sensorType: Int): CalibrationStatus = withContext(Dispatchers.Default) {
        val sensor = sensorManager.getDefaultSensor(sensorType)
        if (sensor == null) {
            return@withContext CalibrationStatus.NOT_AVAILABLE
        }

        // Simplified - would need actual calibration check
        CalibrationStatus.UNKNOWN
    }
}

/**
 * Sensor information.
 */
data class SensorInfo(
    val name: String,
    val type: Int,
    val vendor: String,
    val version: Int,
    val maxRange: Float,
    val resolution: Float,
    val power: Float,
    val minDelay: Int
)

/**
 * Sensor data.
 */
data class SensorData(
    val sensorType: Int,
    val sensorName: String,
    val values: List<Float>,
    val accuracy: Int,
    val timestamp: Long
)

/**
 * Calibration status.
 */
enum class CalibrationStatus {
    CALIBRATED,
    NEEDS_CALIBRATION,
    NOT_AVAILABLE,
    UNKNOWN
}

