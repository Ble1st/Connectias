package com.ble1st.connectias.plugin

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import timber.log.Timber

/**
 * Broadcast system for synchronizing plugin permission changes between main process and sandbox
 * 
 * When permissions are granted/revoked in the main process, this broadcasts the change
 * to the sandbox process so it can update its PluginPermissionManager accordingly.
 */
object PluginPermissionBroadcast {
    
    private const val ACTION_PERMISSION_CHANGED = "com.ble1st.connectias.PLUGIN_PERMISSION_CHANGED"
    private const val EXTRA_PLUGIN_ID = "plugin_id"
    private const val EXTRA_PERMISSION = "permission"
    private const val EXTRA_GRANTED = "granted"
    
    /**
     * Send broadcast when plugin permissions change
     * Called from main process when user grants/revokes permissions
     */
    fun sendPermissionChanged(context: Context, pluginId: String, permission: String, granted: Boolean) {
        try {
            val intent = Intent(ACTION_PERMISSION_CHANGED).apply {
                putExtra(EXTRA_PLUGIN_ID, pluginId)
                putExtra(EXTRA_PERMISSION, permission)
                putExtra(EXTRA_GRANTED, granted)
                // Restrict to our app only for security
                setPackage(context.packageName)
            }
            
            context.sendBroadcast(intent)
            Timber.d("Broadcast permission change: $pluginId - $permission = $granted")
        } catch (e: Exception) {
            // In tests, Intent.putExtra might not be mocked
            Timber.w(e, "Failed to send permission broadcast (possibly in test environment)")
        }
    }
    
    /**
     * Send broadcast for multiple permissions at once
     */
    fun sendPermissionsChanged(context: Context, pluginId: String, permissions: List<String>, granted: Boolean) {
        try {
            permissions.forEach { permission ->
                sendPermissionChanged(context, pluginId, permission, granted)
            }
        } catch (e: Exception) {
            // In tests, Intent.putExtra might not be mocked
            Timber.w(e, "Failed to send permission broadcast (possibly in test environment)")
        }
    }
    
    /**
     * Register receiver in sandbox process to listen for permission changes
     */
    fun registerReceiver(context: Context, permissionManager: PluginPermissionManager): BroadcastReceiver {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != ACTION_PERMISSION_CHANGED) return
                
                val pluginId = intent.getStringExtra(EXTRA_PLUGIN_ID) ?: return
                val permission = intent.getStringExtra(EXTRA_PERMISSION) ?: return
                val granted = intent.getBooleanExtra(EXTRA_GRANTED, false)
                
                Timber.i("[SANDBOX] Received permission change broadcast: $pluginId - $permission = $granted")
                
                // Update permission manager in sandbox process
                if (granted) {
                    permissionManager.grantPermissionForPlugin(pluginId, listOf(permission))
                } else {
                    permissionManager.revokePermissionForPlugin(pluginId, listOf(permission))
                }
            }
        }
        
        val filter = IntentFilter(ACTION_PERMISSION_CHANGED)
        
        // Use RECEIVER_NOT_EXPORTED for security (Android 13+)
        try {
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            Timber.i("[SANDBOX] Registered permission broadcast receiver")
        } catch (e: Exception) {
            // Fallback for older Android versions
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            Timber.i("[SANDBOX] Registered permission broadcast receiver (legacy)")
        }
        
        return receiver
    }
    
    /**
     * Unregister receiver when sandbox is destroyed
     */
    fun unregisterReceiver(context: Context, receiver: BroadcastReceiver) {
        try {
            context.unregisterReceiver(receiver)
            Timber.d("[SANDBOX] Unregistered permission broadcast receiver")
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered
            Timber.w("[SANDBOX] Attempted to unregister non-registered receiver")
        }
    }
}
