package com.ble1st.connectias.plugin

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.ble1st.connectias.R
import timber.log.Timber

/**
 * Manages notifications for plugin events
 */
class PluginNotificationManager(
    private val context: Context
) {
    
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    companion object {
        private const val CHANNEL_ID = "plugin_notifications"
        private const val CHANNEL_NAME = "Plugin System"
        private const val NOTIFICATION_ID_BASE = 10000
    }
    
    init {
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifications for plugin system events"
        }
        notificationManager.createNotificationChannel(channel)
    }
    
    fun notifyPluginLoaded(pluginName: String, pluginVersion: String) {
        showNotification(
            title = "Plugin Loaded",
            message = "$pluginName v$pluginVersion loaded successfully",
            notificationId = NOTIFICATION_ID_BASE + 1
        )
    }
    
    fun notifyPluginEnabled(pluginName: String) {
        showNotification(
            title = "Plugin Enabled",
            message = "$pluginName is now active",
            notificationId = NOTIFICATION_ID_BASE + 2
        )
    }
    
    fun notifyPluginDisabled(pluginName: String) {
        showNotification(
            title = "Plugin Disabled",
            message = "$pluginName has been disabled",
            notificationId = NOTIFICATION_ID_BASE + 3
        )
    }
    
    fun notifyPluginError(pluginName: String, error: String) {
        showNotification(
            title = "Plugin Error",
            message = "$pluginName: $error",
            notificationId = NOTIFICATION_ID_BASE + 4,
            priority = NotificationCompat.PRIORITY_HIGH
        )
    }
    
    fun notifyPluginUpdate(pluginName: String, newVersion: String) {
        showNotification(
            title = "Plugin Update Available",
            message = "$pluginName v$newVersion is available",
            notificationId = NOTIFICATION_ID_BASE + 5
        )
    }
    
    private fun showNotification(
        title: String,
        message: String,
        notificationId: Int,
        priority: Int = NotificationCompat.PRIORITY_DEFAULT
    ) {
        try {
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(priority)
                .setAutoCancel(true)
                .build()
            
            notificationManager.notify(notificationId, notification)
            Timber.d("Notification shown: $title - $message")
        } catch (e: Exception) {
            Timber.e(e, "Failed to show notification")
        }
    }
    
    fun cancelAll() {
        notificationManager.cancelAll()
    }
}
