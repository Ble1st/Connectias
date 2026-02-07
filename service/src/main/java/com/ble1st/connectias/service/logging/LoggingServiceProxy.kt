// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.service.logging

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Proxy for main process to communicate with LoggingService in isolated process.
 * 
 * Used by:
 * - PluginManagerSandbox: applyServiceState (enable/disable service)
 * - ServiceLogViewerViewModel: getRecentLogs (retrieve logs for UI)
 * 
 * Thread-safe: All bind/unbind operations are synchronized.
 */
@Singleton
class LoggingServiceProxy @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    // AIDL interface to LoggingService
    private var loggingService: ILoggingService? = null
    
    // Connection state
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    // Mutex for thread-safe bind/unbind
    private val connectionMutex = Mutex()
    
    // Service connection callback
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Timber.i("[LOGGING_PROXY] Service connected: $name")
            loggingService = ILoggingService.Stub.asInterface(service)
            _isConnected.value = true
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            Timber.w("[LOGGING_PROXY] Service disconnected: $name")
            loggingService = null
            _isConnected.value = false
        }
    }
    
    /**
     * Connect to LoggingService (bind).
     * Call this when enabling the service in the dashboard.
     * 
     * @return Result indicating success or failure
     */
    suspend fun connect(): Result<Unit> = connectionMutex.withLock {
        if (loggingService != null) {
            Timber.i("[LOGGING_PROXY] Already connected")
            return Result.success(Unit)
        }
        
        return try {
            val intent = Intent().apply {
                component = ComponentName(
                    context.packageName,
                    "com.ble1st.connectias.service.logging.LoggingService"
                )
            }
            
            val bound = context.bindService(
                intent,
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )
            
            if (bound) {
                Timber.i("[LOGGING_PROXY] Bind request sent successfully")
                Result.success(Unit)
            } else {
                Timber.e("[LOGGING_PROXY] Failed to bind to LoggingService")
                Result.failure(Exception("Failed to bind to LoggingService"))
            }
        } catch (e: Exception) {
            Timber.e(e, "[LOGGING_PROXY] Exception during bind")
            Result.failure(e)
        }
    }
    
    /**
     * Disconnect from LoggingService (unbind).
     * Call this when disabling the service in the dashboard.
     */
    suspend fun disconnect() = connectionMutex.withLock {
        if (loggingService == null) {
            Timber.i("[LOGGING_PROXY] Not connected, nothing to disconnect")
            return@withLock
        }
        
        try {
            context.unbindService(serviceConnection)
            loggingService = null
            _isConnected.value = false
            Timber.i("[LOGGING_PROXY] Disconnected successfully")
        } catch (e: Exception) {
            Timber.e(e, "[LOGGING_PROXY] Exception during unbind")
        }
    }
    
    /**
     * Enable or disable the logging service.
     * When disabled, the service rejects submitLog calls from external apps.
     * 
     * @param enabled True to enable, false to disable
     * @return Result indicating success or failure
     */
    suspend fun setEnabled(enabled: Boolean): Result<Unit> {
        val service = loggingService
        if (service == null) {
            Timber.e("[LOGGING_PROXY] Not connected to service")
            return Result.failure(Exception("Not connected to LoggingService"))
        }
        
        return try {
            service.setEnabled(enabled)
            Timber.i("[LOGGING_PROXY] setEnabled($enabled) successful")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[LOGGING_PROXY] setEnabled($enabled) failed")
            Result.failure(e)
        }
    }
    
    /**
     * Check if the logging service is currently enabled.
     */
    suspend fun isEnabled(): Boolean {
        val service = loggingService ?: return false
        
        return try {
            service.isEnabled()
        } catch (e: Exception) {
            Timber.e(e, "[LOGGING_PROXY] isEnabled() failed")
            false
        }
    }
    
    /**
     * Get recent logs from the service.
     * Used by Service-Log-Viewer.
     * 
     * @param limit Maximum number of logs to return
     * @return List of log entries or empty list on error
     */
    suspend fun getRecentLogs(limit: Int = 1000): List<ExternalLogParcel> {
        val service = loggingService
        if (service == null) {
            Timber.e("[LOGGING_PROXY] Not connected to service")
            return emptyList()
        }
        
        return try {
            service.getRecentLogs(limit) ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "[LOGGING_PROXY] getRecentLogs($limit) failed")
            emptyList()
        }
    }
    
    /**
     * Get logs for a specific package.
     */
    suspend fun getLogsByPackage(packageName: String, limit: Int = 1000): List<ExternalLogParcel> {
        val service = loggingService ?: return emptyList()
        
        return try {
            service.getLogsByPackage(packageName, limit) ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "[LOGGING_PROXY] getLogsByPackage($packageName, $limit) failed")
            emptyList()
        }
    }
    
    /**
     * Get logs by level (e.g., ERROR, WARN).
     */
    suspend fun getLogsByLevel(level: String, limit: Int = 1000): List<ExternalLogParcel> {
        val service = loggingService ?: return emptyList()
        
        return try {
            service.getLogsByLevel(level, limit) ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "[LOGGING_PROXY] getLogsByLevel($level, $limit) failed")
            emptyList()
        }
    }
    
    /**
     * Get total count of stored logs.
     */
    suspend fun getLogCount(): Int {
        val service = loggingService ?: return 0
        
        return try {
            service.logCount
        } catch (e: Exception) {
            Timber.e(e, "[LOGGING_PROXY] getLogCount() failed")
            0
        }
    }
    
    /**
     * Clear all logs (for testing or manual cleanup).
     */
    suspend fun clearAllLogs(): Result<Unit> {
        val service = loggingService
        if (service == null) {
            return Result.failure(Exception("Not connected to LoggingService"))
        }
        
        return try {
            service.clearAllLogs()
            Timber.i("[LOGGING_PROXY] clearAllLogs() successful")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[LOGGING_PROXY] clearAllLogs() failed")
            Result.failure(e)
        }
    }
}
