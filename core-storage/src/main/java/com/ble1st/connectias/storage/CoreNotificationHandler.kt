package com.ble1st.connectias.storage

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import android.R as AndroidR

class CoreNotificationHandler(
    private val context: Context,
    private val notificationManager: NotificationManager
) {
    companion object {
        const val CHANNEL_ID_ALERTS = "plugin_alerts"
        const val CHANNEL_ID_CRASHES = "plugin_crashes"
        const val CHANNEL_ID_UPDATES = "plugin_updates"
    }
    
    init {
        createNotificationChannels()
    }
    
    private fun createNotificationChannels() {
        val alertChannel = NotificationChannel(
            CHANNEL_ID_ALERTS,
            "Plugin Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Benachrichtigungen über Plugin-Warnungen"
            enableVibration(true)
        }
        
        val crashChannel = NotificationChannel(
            CHANNEL_ID_CRASHES,
            "Plugin Crashes",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Benachrichtigungen über Plugin-Abstürze"
            enableVibration(true)
            enableLights(true)
            lightColor = android.graphics.Color.RED
        }
        
        val updateChannel = NotificationChannel(
            CHANNEL_ID_UPDATES,
            "Plugin Updates",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Benachrichtigungen über verfügbare Updates"
        }
        
        notificationManager.createNotificationChannels(
            listOf(alertChannel, crashChannel, updateChannel)
        )
    }
    
    fun sendPluginAlert(pluginId: String, alert: MonitoringAlert) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(AndroidR.drawable.ic_dialog_alert)
            .setContentTitle("Plugin-Warnung: $pluginId")
            .setContentText(alert.message)
            .setPriority(getSeverityPriority(alert.severity))
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(pluginId.hashCode(), notification)
    }
    
    fun sendPluginCrashNotification(pluginId: String, error: Throwable, crashReport: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_CRASHES)
            .setSmallIcon(AndroidR.drawable.ic_dialog_alert)
            .setContentTitle("Plugin abgestürzt: $pluginId")
            .setContentText("Fehler: ${error.message}")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Das Plugin wurde automatisch deaktiviert.\n\nFehler: ${error.message}\n\nDetails im Crash-Report verfügbar."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(false)
            .build()
        
        notificationManager.notify(pluginId.hashCode() + 1000, notification)
    }
    
    fun sendNetworkOfflineNotification(pluginId: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(AndroidR.drawable.ic_dialog_alert)
            .setContentTitle("Netzwerk nicht verfügbar")
            .setContentText("Plugin $pluginId benötigt Internetzugriff")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(pluginId.hashCode() + 2000, notification)
    }
    
    private fun getSeverityPriority(severity: AlertSeverity): Int {
        return when (severity) {
            AlertSeverity.CRITICAL -> NotificationCompat.PRIORITY_MAX
            AlertSeverity.HIGH -> NotificationCompat.PRIORITY_HIGH
            AlertSeverity.MEDIUM -> NotificationCompat.PRIORITY_DEFAULT
            AlertSeverity.LOW -> NotificationCompat.PRIORITY_LOW
        }
    }
}
