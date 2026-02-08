// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.service.logging

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.ble1st.connectias.core.security.KeyManager
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
 * After connecting, the proxy immediately sends the database passphrase from
 * [KeyManager] to the service via [ILoggingService.setDatabaseKey]. This upgrades
 * the encryption from the previous deterministic UID-based key to a proper
 * cryptographically random key managed by the Android Keystore.
 *
 * Used by:
 * - PluginManagerSandbox: applyServiceState (enable/disable service)
 * - ServiceLogViewerViewModel: getRecentLogs / getRecentLogsAsync (retrieve logs for UI)
 *
 * Thread-safe: All bind/unbind operations are synchronized.
 */
@Singleton
class LoggingServiceProxy @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyManager: KeyManager
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
            val stub = ILoggingService.Stub.asInterface(service)
            loggingService = stub
            _isConnected.value = true

            // ===== SECURITY: IPC key exchange =====
            // Send the KeyManager passphrase immediately so the service can open
            // its SQLCipher database with a proper cryptographic key.
            try {
                val key = keyManager.getDatabasePassphrase()
                stub.setDatabaseKey(key)
                // key ByteArray is zeroed inside LoggingService.initDatabase()
                Timber.i("[LOGGING_PROXY] Database key sent to LoggingService")
            } catch (e: Exception) {
                Timber.e(e, "[LOGGING_PROXY] Failed to send database key — logs will be dropped until retry")
            }
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
     */
    suspend fun setEnabled(enabled: Boolean): Result<Unit> {
        val service = loggingService
            ?: return Result.failure(Exception("Not connected to LoggingService"))
        return try {
            service.setEnabled(enabled)
            Timber.i("[LOGGING_PROXY] setEnabled($enabled) successful")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[LOGGING_PROXY] setEnabled($enabled) failed")
            Result.failure(e)
        }
    }

    /** Check if the logging service is currently enabled. */
    suspend fun isEnabled(): Boolean {
        return try {
            loggingService?.isEnabled() ?: false
        } catch (e: Exception) {
            Timber.e(e, "[LOGGING_PROXY] isEnabled() failed")
            false
        }
    }

    // ========== v1: Log retrieval (synchronous) ==========

    /** Get recent logs (synchronous, safe for small limits). */
    suspend fun getRecentLogs(limit: Int = 1000): List<ExternalLogParcel> {
        return try {
            loggingService?.getRecentLogs(limit) ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "[LOGGING_PROXY] getRecentLogs($limit) failed")
            emptyList()
        }
    }

    /** Get logs for a specific package (synchronous). */
    suspend fun getLogsByPackage(packageName: String, limit: Int = 1000): List<ExternalLogParcel> {
        return try {
            loggingService?.getLogsByPackage(packageName, limit) ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "[LOGGING_PROXY] getLogsByPackage($packageName, $limit) failed")
            emptyList()
        }
    }

    /** Get logs by level (synchronous). */
    suspend fun getLogsByLevel(level: String, limit: Int = 1000): List<ExternalLogParcel> {
        return try {
            loggingService?.getLogsByLevel(level, limit) ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "[LOGGING_PROXY] getLogsByLevel($level, $limit) failed")
            emptyList()
        }
    }

    /** Get total count of stored logs. */
    suspend fun getLogCount(): Int {
        return try {
            loggingService?.logCount ?: 0
        } catch (e: Exception) {
            Timber.e(e, "[LOGGING_PROXY] getLogCount() failed")
            0
        }
    }

    /** Clear all logs. */
    suspend fun clearAllLogs(): Result<Unit> {
        val service = loggingService
            ?: return Result.failure(Exception("Not connected to LoggingService"))
        return try {
            service.clearAllLogs()
            Timber.i("[LOGGING_PROXY] clearAllLogs() successful")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[LOGGING_PROXY] clearAllLogs() failed")
            Result.failure(e)
        }
    }

    // ========== v2: Metrics ==========

    /** Get runtime security metrics snapshot. */
    suspend fun getSecurityMetrics(): LoggingMetricsParcel? {
        return try {
            loggingService?.securityMetrics
        } catch (e: Exception) {
            Timber.e(e, "[LOGGING_PROXY] getSecurityMetrics() failed")
            null
        }
    }

    // ========== v2: Async log retrieval ==========

    /**
     * Request recent logs asynchronously. Prefer this for > 500 entries.
     * Results are delivered via [callback].
     *
     * @param limit     Maximum entries (recommend <= 500 to stay under 1 MB Binder limit)
     * @param callback  Receives onLogsResult or onError
     */
    fun getRecentLogsAsync(
        limit: Int = 500,
        callback: ILoggingResultCallback
    ): Result<Unit> {
        val service = loggingService
            ?: return Result.failure(Exception("Not connected to LoggingService"))
        return try {
            val requestId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
            service.getRecentLogsAsync(limit, callback, requestId)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[LOGGING_PROXY] getRecentLogsAsync failed")
            Result.failure(e)
        }
    }

    /** Async version of [getLogsByPackage]. */
    fun getLogsByPackageAsync(
        packageName: String,
        limit: Int = 500,
        callback: ILoggingResultCallback
    ): Result<Unit> {
        val service = loggingService
            ?: return Result.failure(Exception("Not connected to LoggingService"))
        return try {
            val requestId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
            service.getLogsByPackageAsync(packageName, limit, callback, requestId)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[LOGGING_PROXY] getLogsByPackageAsync failed")
            Result.failure(e)
        }
    }

    /** Async version of [getLogsByLevel]. */
    fun getLogsByLevelAsync(
        level: String,
        limit: Int = 500,
        callback: ILoggingResultCallback
    ): Result<Unit> {
        val service = loggingService
            ?: return Result.failure(Exception("Not connected to LoggingService"))
        return try {
            val requestId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
            service.getLogsByLevelAsync(level, limit, callback, requestId)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[LOGGING_PROXY] getLogsByLevelAsync failed")
            Result.failure(e)
        }
    }

    // ========== v2: Audit events ==========

    /** Get recent security audit events (synchronous). */
    suspend fun getRecentAuditEvents(limit: Int = 500): List<SecurityAuditParcel> {
        return try {
            loggingService?.getRecentAuditEvents(limit) ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "[LOGGING_PROXY] getRecentAuditEvents($limit) failed")
            emptyList()
        }
    }

    /** Async version of [getRecentAuditEvents]. */
    fun getRecentAuditEventsAsync(
        limit: Int = 500,
        callback: ILoggingResultCallback
    ): Result<Unit> {
        val service = loggingService
            ?: return Result.failure(Exception("Not connected to LoggingService"))
        return try {
            val requestId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
            service.getRecentAuditEventsAsync(limit, callback, requestId)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "[LOGGING_PROXY] getRecentAuditEventsAsync failed")
            Result.failure(e)
        }
    }
}
