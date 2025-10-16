package com.ble1st.connectias.plugin

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import com.ble1st.connectias.api.SystemInfoService
import timber.log.Timber

class SystemInfoServiceImpl(
    private val context: Context,
    private val pluginId: String
) : SystemInfoService {
    
    override fun getBatteryLevel(): Int {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            Timber.d("Plugin $pluginId: Retrieved battery level: $batteryLevel%")
            batteryLevel
        } catch (e: Exception) {
            Timber.e(e, "Plugin $pluginId: Failed to get battery level")
            0
        }
    }
    
    override fun getDeviceModel(): String {
        return try {
            val model = "${Build.MANUFACTURER} ${Build.MODEL}"
            Timber.d("Plugin $pluginId: Retrieved device model: $model")
            model
        } catch (e: Exception) {
            Timber.e(e, "Plugin $pluginId: Failed to get device model")
            "Unknown Device"
        }
    }
    
    override fun getAndroidVersion(): String {
        return try {
            val version = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
            Timber.d("Plugin $pluginId: Retrieved Android version: $version")
            version
        } catch (e: Exception) {
            Timber.e(e, "Plugin $pluginId: Failed to get Android version")
            "Unknown Version"
        }
    }
    
    override fun getTotalMemory(): Long {
        return try {
            val runtime = Runtime.getRuntime()
            val totalMemory = runtime.totalMemory()
            Timber.d("Plugin $pluginId: Retrieved total memory: ${totalMemory / (1024 * 1024)} MB")
            totalMemory
        } catch (e: Exception) {
            Timber.e(e, "Plugin $pluginId: Failed to get total memory")
            0L
        }
    }
    
    override fun getAvailableMemory(): Long {
        return try {
            val runtime = Runtime.getRuntime()
            val freeMemory = runtime.freeMemory()
            Timber.d("Plugin $pluginId: Retrieved available memory: ${freeMemory / (1024 * 1024)} MB")
            freeMemory
        } catch (e: Exception) {
            Timber.e(e, "Plugin $pluginId: Failed to get available memory")
            0L
        }
    }
    
    override fun getCpuUsage(): Float {
        return try {
            // Vereinfachte CPU-Usage-Berechnung
            // In einer echten Implementierung würde man /proc/stat lesen
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val totalMemory = runtime.totalMemory()
            val cpuUsage = (usedMemory.toFloat() / totalMemory.toFloat()) * 100f
            
            Timber.d("Plugin $pluginId: Retrieved CPU usage: ${cpuUsage.toInt()}%")
            cpuUsage
        } catch (e: Exception) {
            Timber.e(e, "Plugin $pluginId: Failed to get CPU usage")
            0f
        }
    }
}
