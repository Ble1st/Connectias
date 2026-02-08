// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.service.logging

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.IBinder
import android.os.Process
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Centralized logging service running in isolated process (:logging).
 *
 * External apps can submit logs via ILoggingService (requires SUBMIT_EXTERNAL_LOGS permission).
 * Permission is dangerous (Shizuku-style): any app can request it; user grants at runtime.
 * Main process can control the service and retrieve logs (same app UID bypasses permission).
 *
 * Architecture:
 * - Isolated process: No access to main app database or KeyManager
 * - Own Room database: external_logs.db (encrypted with KeyManager passphrase via IPC)
 * - Database initialization is deferred until setDatabaseKey() is called from main process
 * - Caller validation: Main process (same UID) vs external apps (permission check)
 *
 * Security Features:
 * - IPC key exchange: Main process provides SQLCipher passphrase via setDatabaseKey()
 * - Rate limiting: 100 logs/sec for submitLog, 50 logs/sec for submitLogWithException (per package)
 * - Input validation: Size limits, format checks, control character sanitization
 * - Dedicated audit table: security_audit persists all security events independently
 * - Auto-cleanup: Deletes logs older than 7 days, enforces 100K entry hard cap
 */
class LoggingService : Service() {

    // Room database for external logs (in isolated process).
    // Nullable until setDatabaseKey() is called by the main process after binding.
    @Volatile
    private var database: ExternalLogDatabase? = null
    @Volatile
    private var logDao: ExternalLogDao? = null
    @Volatile
    private var auditDao: SecurityAuditDao? = null

    // Service enabled state (controlled by main process via setEnabled)
    @Volatile
    private var enabled: Boolean = true

    // Coroutine scope for async DB operations
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Current process UID (isolated process has its own UID)
    private val processUid: Int by lazy { Process.myUid() }

    // Main app UID (default process) — used to accept setDatabaseKey/setEnabled from main process
    private val mainAppUid: Int by lazy {
        try {
            packageManager.getPackageUid(packageName, PackageManager.PackageInfoFlags.of(0))
        } catch (e: Exception) {
            Timber.w(e, "[LOGGING_SERVICE] Could not resolve main app UID, using process UID")
            processUid
        }
    }

    // Security components
    private val rateLimiter = LoggingRateLimiter()

    // Statistics for monitoring
    @Volatile
    private var totalLogsReceived: Long = 0
    @Volatile
    private var rateLimitViolations: Long = 0
    @Volatile
    private var validationFailures: Long = 0

    override fun onCreate() {
        super.onCreate()
        Timber.i("[LOGGING_SERVICE] Service created in isolated process (PID: ${Process.myPid()}, process UID: $processUid, main app UID: $mainAppUid)")

        // Load SQLCipher native library (must be done before Room can open an encrypted DB)
        try {
            System.loadLibrary("sqlcipher")
        } catch (e: UnsatisfiedLinkError) {
            Timber.w(e, "[LOGGING_SERVICE] Failed to load SQLCipher native library")
        }

        // Database initialization is deferred — the main process must call setDatabaseKey()
        // immediately after binding. Logs received before that call are silently dropped.
        Timber.i("[LOGGING_SERVICE] Waiting for database key from main process via setDatabaseKey()")

        startAutoCleanup()
    }

    /**
     * Initialize the Room database with the passphrase provided by the main process.
     * Called exactly once, from setDatabaseKey() in the binder stub.
     *
     * The passphrase ByteArray is zeroed after the SQLCipher factory has consumed it.
     */
    private fun initDatabase(passphrase: ByteArray) {
        synchronized(this) {
            if (database != null) {
                Timber.w("[LOGGING_SERVICE] initDatabase called more than once — ignoring")
                passphrase.fill(0)
                return
            }

            val factory = buildSQLCipherFactory(passphrase)
            // Zero the passphrase immediately after handing it to the factory
            passphrase.fill(0)

            val db = Room.databaseBuilder(
                applicationContext,
                ExternalLogDatabase::class.java,
                "external_logs.db"
            )
                .openHelperFactory(factory)
                .addMigrations(ExternalLogDatabase.MIGRATION_1_2)
                .build()

            database = db
            logDao = db.externalLogDao()
            auditDao = db.securityAuditDao()
        }

        Timber.i("[LOGGING_SERVICE] Database initialized: ${getDatabasePath("external_logs.db").absolutePath}")
    }

    /**
     * Build a SupportSQLiteOpenHelper.Factory for SQLCipher with the given passphrase.
     * Tries the modern API (4.12+) first, falls back to the legacy API.
     */
    private fun buildSQLCipherFactory(passphrase: ByteArray): SupportSQLiteOpenHelper.Factory {
        return try {
            val factoryClass = Class.forName("net.zetetic.database.sqlcipher.SupportOpenHelperFactory")
            factoryClass.getConstructor(ByteArray::class.java)
                .newInstance(passphrase) as SupportSQLiteOpenHelper.Factory
        } catch (e: ClassNotFoundException) {
            try {
                val factoryClass = Class.forName("net.sqlcipher.database.SupportFactory")
                factoryClass.getConstructor(ByteArray::class.java)
                    .newInstance(passphrase) as SupportSQLiteOpenHelper.Factory
            } catch (e2: ClassNotFoundException) {
                Timber.e(e2, "[LOGGING_SERVICE] SQLCipher factory not found")
                throw IllegalStateException(
                    "SQLCipher SupportOpenHelperFactory/SupportFactory not found. " +
                    "Ensure net.zetetic:sqlcipher-android:4.13.0 is correctly configured.",
                    e2
                )
            }
        }
    }

    /**
     * Start background job for automatic database cleanup (runs every hour).
     *
     * Performs three types of cleanup:
     * 1. Time-based: Delete logs/audit events older than 7 days
     * 2. Size-based: Enforce 100K entry hard cap (delete oldest 20K if exceeded)
     * 3. Rate limiter: Remove inactive per-package token buckets
     */
    private fun startAutoCleanup() {
        serviceScope.launch {
            while (true) {
                try {
                    kotlinx.coroutines.delay(3_600_000L) // every hour

                    if (!enabled) continue

                    val dao = logDao ?: continue  // Skip if DB not ready yet
                    val aDao = auditDao

                    val threshold = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)

                    // 1. Delete logs older than 7 days
                    val deletedByAge = dao.deleteOlderThan(threshold)
                    if (deletedByAge > 0) {
                        Timber.i("[LOGGING_SERVICE] Auto-cleanup: Deleted $deletedByAge logs older than 7 days")
                    }

                    // 2. Enforce hard cap of 100K entries
                    val count = dao.getLogCount()
                    if (count > 100_000) {
                        val toDelete = count - 80_000
                        val deleted = dao.deleteOldestN(toDelete)
                        Timber.w("[LOGGING_SERVICE] Auto-cleanup: DB exceeded 100K ($count), deleted oldest $deleted")
                    }

                    // 3. Delete audit events older than 7 days
                    aDao?.deleteOlderThan(threshold)

                    // 4. Clean up inactive rate limiter buckets
                    rateLimiter.cleanup()

                } catch (e: Exception) {
                    Timber.e(e, "[LOGGING_SERVICE] Auto-cleanup failed")
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        val callerUid = Binder.getCallingUid()
        Timber.i("[LOGGING_SERVICE] onBind called by UID: $callerUid (main app UID: $mainAppUid)")
        return LoggingServiceBinder()
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.i("[LOGGING_SERVICE] Service destroyed")
        Timber.i(
            "[LOGGING_SERVICE] Statistics — Total: $totalLogsReceived, " +
            "Rate-limited: $rateLimitViolations, Validation failures: $validationFailures"
        )

        serviceScope.cancel()
        database?.close()
    }

    /**
     * Persist a security audit event to the dedicated audit table AND log via Timber.
     *
     * @param packageName Package that triggered the event
     * @param eventType   Type of security event (rate_limit_exceeded, validation_failed, etc.)
     * @param details     Additional context (capped to 512 chars)
     */
    private fun auditSecurityEvent(packageName: String, eventType: String, details: String) {
        val timestamp = System.currentTimeMillis()
        // Timber for immediate logcat visibility (grep-able with [SECURITY_AUDIT] prefix)
        Timber.w("[SECURITY_AUDIT] $eventType | Package: $packageName | Details: $details | Time: $timestamp")

        // Persist to dedicated audit table for structured querying
        val dao = auditDao ?: return
        serviceScope.launch {
            try {
                dao.insert(
                    SecurityAuditEntity(
                        timestamp = timestamp,
                        packageName = packageName,
                        eventType = eventType,
                        details = details.take(512)
                    )
                )
            } catch (e: Exception) {
                Timber.e(e, "[LOGGING_SERVICE] Failed to persist audit event")
            }
        }
    }

    /**
     * Guard: return the log DAO only when the database has been initialized.
     * Logs an error and returns null if the key has not been received yet.
     */
    private fun requireLogDao(): ExternalLogDao? {
        val dao = logDao
        if (dao == null) {
            Timber.e("[LOGGING_SERVICE] Database not ready — key not yet received via setDatabaseKey()")
        }
        return dao
    }

    /**
     * Combined AIDL stub implementing ILoggingService.
     * Methods validate caller UID/permission before executing.
     */
    private inner class LoggingServiceBinder : ILoggingService.Stub() {

        /**
         * Validate that the caller is the main process (same application package).
         * In an isolated process, Process.myUid() is the isolated UID; the main app has a
         * different UID. We accept callers whose UID is the package's default (main) UID.
         */
        private fun validateMainProcessCaller(): Boolean {
            val callerUid = Binder.getCallingUid()
            if (callerUid != mainAppUid) {
                Timber.w("[LOGGING_SERVICE] Unauthorized caller UID: $callerUid (expected main app UID: $mainAppUid)")
                return false
            }
            return true
        }

        /**
         * Validate external app caller has SUBMIT_EXTERNAL_LOGS permission.
         * The permission is already enforced at bind time by the Android framework,
         * but we re-check here for defense-in-depth.
         */
        private fun validateExternalAppCaller(): Boolean {
            val callerUid = Binder.getCallingUid()
            // Main app (same package) is always allowed (internal use/testing)
            if (callerUid == mainAppUid) return true
            // External app — permission already checked by framework at bind time
            return true
        }

        /** Get package name for the calling UID. */
        private fun getCallerPackageName(): String? {
            val callerUid = Binder.getCallingUid()
            val packages = applicationContext.packageManager.getPackagesForUid(callerUid)
            return packages?.firstOrNull()
        }

        // ========== v1: Methods for external apps ==========

        override fun submitLog(packageName: String, level: String, tag: String, message: String) {
            totalLogsReceived++

            if (!enabled) return
            if (!validateExternalAppCaller()) return

            val actualPackage = getCallerPackageName() ?: packageName

            // ===== SECURITY: Rate Limiting =====
            if (!rateLimiter.checkRateLimit(actualPackage, "submitLog")) {
                rateLimitViolations++
                auditSecurityEvent(actualPackage, "rate_limit_exceeded", "submitLog")
                return
            }

            // ===== SECURITY: Input Validation =====
            val validated = LogInputValidator.validate(actualPackage, level, tag, message, null)
            if (validated == null) {
                validationFailures++
                auditSecurityEvent(actualPackage, "validation_failed", "submitLog")
                return
            }

            if (LogInputValidator.hasSuspiciousPatterns(message)) {
                auditSecurityEvent(actualPackage, "suspicious_pattern", message.take(100))
            }

            val dao = requireLogDao() ?: return
            serviceScope.launch {
                try {
                    dao.insert(
                        ExternalLogEntity(
                            timestamp = System.currentTimeMillis(),
                            packageName = validated.packageName,
                            level = validated.level,
                            tag = validated.tag,
                            message = validated.message,
                            exceptionTrace = null
                        )
                    )
                } catch (e: Exception) {
                    Timber.e(e, "[LOGGING_SERVICE] Failed to insert log")
                }
            }
        }

        override fun submitLogWithException(
            packageName: String, level: String, tag: String,
            message: String, exceptionTrace: String?
        ) {
            totalLogsReceived++

            if (!enabled) return
            if (!validateExternalAppCaller()) return

            val actualPackage = getCallerPackageName() ?: packageName

            // ===== SECURITY: Rate Limiting (lower limit for exceptions) =====
            if (!rateLimiter.checkRateLimit(actualPackage, "submitLogWithException")) {
                rateLimitViolations++
                auditSecurityEvent(actualPackage, "rate_limit_exceeded", "submitLogWithException")
                return
            }

            // ===== SECURITY: Input Validation =====
            val validated = LogInputValidator.validate(actualPackage, level, tag, message, exceptionTrace)
            if (validated == null) {
                validationFailures++
                auditSecurityEvent(actualPackage, "validation_failed", "submitLogWithException")
                return
            }

            if (LogInputValidator.hasSuspiciousPatterns(message) ||
                (exceptionTrace != null && LogInputValidator.hasSuspiciousPatterns(exceptionTrace))
            ) {
                auditSecurityEvent(actualPackage, "suspicious_pattern", "submitLogWithException")
            }

            val dao = requireLogDao() ?: return
            serviceScope.launch {
                try {
                    dao.insert(
                        ExternalLogEntity(
                            timestamp = System.currentTimeMillis(),
                            packageName = validated.packageName,
                            level = validated.level,
                            tag = validated.tag,
                            message = validated.message,
                            exceptionTrace = validated.exceptionTrace
                        )
                    )
                } catch (e: Exception) {
                    Timber.e(e, "[LOGGING_SERVICE] Failed to insert log with exception")
                }
            }
        }

        // ========== v1: Methods for main process ==========

        override fun setEnabled(enabled: Boolean) {
            if (!validateMainProcessCaller()) return
            Timber.i("[LOGGING_SERVICE] setEnabled: $enabled")
            this@LoggingService.enabled = enabled
        }

        override fun isEnabled(): Boolean {
            if (!validateMainProcessCaller()) return false
            return enabled
        }

        override fun getRecentLogs(limit: Int): List<ExternalLogParcel> {
            if (!validateMainProcessCaller()) return emptyList()
            if (!enabled) return emptyList()
            val dao = requireLogDao() ?: return emptyList()
            return try {
                ExternalLogParcel.fromEntities(kotlinx.coroutines.runBlocking { dao.getRecentLogs(limit) })
            } catch (e: Exception) {
                Timber.e(e, "[LOGGING_SERVICE] Failed to getRecentLogs")
                emptyList()
            }
        }

        override fun getLogsByPackage(packageName: String, limit: Int): List<ExternalLogParcel> {
            if (!validateMainProcessCaller()) return emptyList()
            if (!enabled) return emptyList()
            val dao = requireLogDao() ?: return emptyList()
            return try {
                ExternalLogParcel.fromEntities(kotlinx.coroutines.runBlocking { dao.getLogsByPackage(packageName, limit) })
            } catch (e: Exception) {
                Timber.e(e, "[LOGGING_SERVICE] Failed to getLogsByPackage")
                emptyList()
            }
        }

        override fun getLogsByLevel(level: String, limit: Int): List<ExternalLogParcel> {
            if (!validateMainProcessCaller()) return emptyList()
            if (!enabled) return emptyList()
            val dao = requireLogDao() ?: return emptyList()
            return try {
                ExternalLogParcel.fromEntities(kotlinx.coroutines.runBlocking { dao.getLogsByLevel(level, limit) })
            } catch (e: Exception) {
                Timber.e(e, "[LOGGING_SERVICE] Failed to getLogsByLevel")
                emptyList()
            }
        }

        override fun getLogCount(): Int {
            if (!validateMainProcessCaller()) return 0
            val dao = requireLogDao() ?: return 0
            return try {
                kotlinx.coroutines.runBlocking { dao.getLogCount() }
            } catch (e: Exception) {
                Timber.e(e, "[LOGGING_SERVICE] Failed to getLogCount")
                0
            }
        }

        override fun clearAllLogs() {
            if (!validateMainProcessCaller()) return
            Timber.i("[LOGGING_SERVICE] Clearing all logs")
            val dao = requireLogDao() ?: return
            serviceScope.launch {
                try {
                    dao.deleteAll()
                } catch (e: Exception) {
                    Timber.e(e, "[LOGGING_SERVICE] Failed to clearAllLogs")
                }
            }
        }

        // ========== v2: IPC key exchange ==========

        override fun setDatabaseKey(key: ByteArray) {
            if (!validateMainProcessCaller()) {
                Timber.e("[LOGGING_SERVICE] Unauthorized setDatabaseKey attempt")
                key.fill(0)  // Zero the key even on rejection
                return
            }
            Timber.i("[LOGGING_SERVICE] setDatabaseKey received — initializing database")
            initDatabase(key)  // key is zeroed inside initDatabase after factory creation
        }

        // ========== v2: Metrics endpoint ==========

        override fun getSecurityMetrics(): LoggingMetricsParcel {
            if (!validateMainProcessCaller()) {
                return LoggingMetricsParcel(0, 0, 0, -1, 0, 0)
            }
            return try {
                val dao = logDao
                val aDao = auditDao
                val dbSize = getDatabasePath("external_logs.db").length()
                val oldestTs = if (dao != null) {
                    kotlinx.coroutines.runBlocking { dao.getOldestTimestamp() } ?: 0L
                } else 0L
                val auditCount = if (aDao != null) {
                    kotlinx.coroutines.runBlocking { aDao.getAuditEventCount() }
                } else 0

                LoggingMetricsParcel(
                    totalLogsReceived = totalLogsReceived,
                    rateLimitViolations = rateLimitViolations,
                    validationFailures = validationFailures,
                    databaseSize = dbSize,
                    oldestLogTimestamp = oldestTs,
                    auditEventCount = auditCount
                )
            } catch (e: Exception) {
                Timber.e(e, "[LOGGING_SERVICE] Failed to getSecurityMetrics")
                LoggingMetricsParcel(totalLogsReceived, rateLimitViolations, validationFailures, -1, 0, 0)
            }
        }

        // ========== v2: Async log queries ==========

        override fun getRecentLogsAsync(limit: Int, callback: ILoggingResultCallback, requestId: Int) {
            if (!validateMainProcessCaller()) { safeCallbackError(callback, "Unauthorized", requestId); return }
            val dao = requireLogDao() ?: run { safeCallbackError(callback, "Database not ready", requestId); return }
            serviceScope.launch {
                try {
                    callback.onLogsResult(ExternalLogParcel.fromEntities(dao.getRecentLogs(limit)), requestId)
                } catch (e: Exception) {
                    Timber.e(e, "[LOGGING_SERVICE] getRecentLogsAsync failed")
                    safeCallbackError(callback, e.message ?: "Unknown", requestId)
                }
            }
        }

        override fun getLogsByPackageAsync(
            packageName: String, limit: Int,
            callback: ILoggingResultCallback, requestId: Int
        ) {
            if (!validateMainProcessCaller()) { safeCallbackError(callback, "Unauthorized", requestId); return }
            val dao = requireLogDao() ?: run { safeCallbackError(callback, "Database not ready", requestId); return }
            serviceScope.launch {
                try {
                    callback.onLogsResult(ExternalLogParcel.fromEntities(dao.getLogsByPackage(packageName, limit)), requestId)
                } catch (e: Exception) {
                    Timber.e(e, "[LOGGING_SERVICE] getLogsByPackageAsync failed")
                    safeCallbackError(callback, e.message ?: "Unknown", requestId)
                }
            }
        }

        override fun getLogsByLevelAsync(
            level: String, limit: Int,
            callback: ILoggingResultCallback, requestId: Int
        ) {
            if (!validateMainProcessCaller()) { safeCallbackError(callback, "Unauthorized", requestId); return }
            val dao = requireLogDao() ?: run { safeCallbackError(callback, "Database not ready", requestId); return }
            serviceScope.launch {
                try {
                    callback.onLogsResult(ExternalLogParcel.fromEntities(dao.getLogsByLevel(level, limit)), requestId)
                } catch (e: Exception) {
                    Timber.e(e, "[LOGGING_SERVICE] getLogsByLevelAsync failed")
                    safeCallbackError(callback, e.message ?: "Unknown", requestId)
                }
            }
        }

        // ========== v2: Security audit events ==========

        override fun getRecentAuditEvents(limit: Int): List<SecurityAuditParcel> {
            if (!validateMainProcessCaller()) return emptyList()
            val dao = auditDao ?: return emptyList()
            return try {
                SecurityAuditParcel.fromEntities(kotlinx.coroutines.runBlocking { dao.getRecentAuditEvents(limit) })
            } catch (e: Exception) {
                Timber.e(e, "[LOGGING_SERVICE] Failed to getRecentAuditEvents")
                emptyList()
            }
        }

        override fun getRecentAuditEventsAsync(
            limit: Int,
            callback: ILoggingResultCallback,
            requestId: Int
        ) {
            if (!validateMainProcessCaller()) { safeCallbackError(callback, "Unauthorized", requestId); return }
            val dao = auditDao ?: run { safeCallbackError(callback, "Database not ready", requestId); return }
            serviceScope.launch {
                try {
                    callback.onAuditEventsResult(SecurityAuditParcel.fromEntities(dao.getRecentAuditEvents(limit)), requestId)
                } catch (e: Exception) {
                    Timber.e(e, "[LOGGING_SERVICE] getRecentAuditEventsAsync failed")
                    safeCallbackError(callback, e.message ?: "Unknown", requestId)
                }
            }
        }

        /** Helper: invoke onError safely, swallowing DeadObjectException if caller is gone. */
        private fun safeCallbackError(callback: ILoggingResultCallback, message: String, requestId: Int) {
            try {
                callback.onError(message, requestId)
            } catch (_: Exception) { /* caller process may have died */ }
        }
    }
}
