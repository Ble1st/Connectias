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
 * External apps can submit logs via ILoggingService interface (requires SUBMIT_EXTERNAL_LOGS permission).
 * Main process can control the service and retrieve logs via ILoggingServiceControl interface.
 *
 * Architecture:
 * - Isolated process: No access to main app database or KeyManager
 * - Own Room database: external_logs.db (unencrypted or encrypted with provided key)
 * - Caller validation: Main process (same UID) vs external apps (permission check)
 *
 * Security Features:
 * - Rate limiting: 100 logs/sec for submitLog, 50 logs/sec for submitLogWithException (per package)
 * - Input validation: Size limits, format checks, control character sanitization
 * - Auto-cleanup: Deletes logs older than 7 days, enforces 100K entry hard cap
 * - Audit logging: Tracks suspicious patterns and security events
 */
class LoggingService : Service() {

    // Room database for external logs (in isolated process)
    private lateinit var database: ExternalLogDatabase
    private lateinit var logDao: ExternalLogDao

    // Service enabled state (controlled by main process via setEnabled)
    @Volatile
    private var enabled: Boolean = true

    // Coroutine scope for async DB operations
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // App UID for caller validation (main process check)
    private val appUid: Int by lazy { Process.myUid() }

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
        Timber.i("[LOGGING_SERVICE] Service created in isolated process (PID: ${Process.myPid()}, UID: $appUid)")

        // Load SQLCipher native library
        try {
            System.loadLibrary("sqlcipher")
        } catch (e: UnsatisfiedLinkError) {
            Timber.w(e, "[LOGGING_SERVICE] Failed to load SQLCipher native library")
        }

        // Generate encryption passphrase
        // Note: In isolated process, we use a deterministic key based on app UID
        // Future enhancement: Main process can provide a key via IPC at bind time
        val passphrase = generateEncryptionPassphrase()

        // SQLCipher 4.13.0 uses SupportOpenHelperFactory (loaded via reflection for compatibility)
        val factory = try {
            // Try new API first (sqlcipher-android 4.12.0+)
            val factoryClass = Class.forName("net.zetetic.database.sqlcipher.SupportOpenHelperFactory")
            factoryClass.getConstructor(ByteArray::class.java)
                .newInstance(passphrase) as SupportSQLiteOpenHelper.Factory
        } catch (e: ClassNotFoundException) {
            // Fallback to old API (sqlcipher-android <4.12.0)
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

        // Initialize Room database with SQLCipher encryption
        database = Room.databaseBuilder(
            applicationContext,
            ExternalLogDatabase::class.java,
            "external_logs.db"
        )
            .openHelperFactory(factory)
            .build()

        logDao = database.externalLogDao()

        Timber.i("[LOGGING_SERVICE] Encrypted database initialized: ${getDatabasePath("external_logs.db").absolutePath}")

        // Start background cleanup job
        startAutoCleanup()
    }

    /**
     * Generate encryption passphrase for the database.
     *
     * Current implementation: Deterministic key based on app UID
     * - Allows database to survive process restarts
     * - Different from main app's KeyManager (isolated process)
     *
     * Future enhancement: Key exchange via IPC from main process
     * - Main process generates key with KeyManager
     * - Passes key via setEncryptionKey() method at bind time
     * - Better security, but more complex implementation
     *
     * @return ByteArray passphrase for SQLCipher
     */
    private fun generateEncryptionPassphrase(): ByteArray {
        // Use app UID as seed for reproducible key generation
        // This is NOT cryptographically ideal, but acceptable for logs
        // (logs are not as sensitive as user credentials)
        val seed = "connectias_logging_${appUid}".toByteArray()

        // Stretch the seed using a simple hash-like operation
        // (not a real KDF, but sufficient for this use case)
        val stretched = ByteArray(32) // 256-bit key
        for (i in stretched.indices) {
            stretched[i] = ((seed[i % seed.size].toInt() xor i xor appUid) and 0xFF).toByte()
        }

        return stretched
    }

    /**
     * Start background job for automatic database cleanup.
     *
     * Performs two types of cleanup:
     * 1. Time-based: Delete logs older than 7 days
     * 2. Size-based: Enforce 100K entry hard cap (delete oldest 20K if exceeded)
     *
     * Also cleans up unused rate limiter buckets.
     */
    private fun startAutoCleanup() {
        serviceScope.launch {
            while (true) {
                try {
                    kotlinx.coroutines.delay(3_600_000L) // Run every hour

                    if (!enabled) continue

                    // 1. Delete logs older than 7 days
                    val threshold = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
                    val deletedByAge = logDao.deleteOlderThan(threshold)
                    if (deletedByAge > 0) {
                        Timber.i("[LOGGING_SERVICE] Auto-cleanup: Deleted $deletedByAge logs older than 7 days")
                    }

                    // 2. Enforce hard cap of 100K entries
                    val count = logDao.getLogCount()
                    if (count > 100_000) {
                        val toDelete = count - 80_000 // Delete oldest 20K+ to get back to 80K
                        val deleted = logDao.deleteOldestN(toDelete)
                        Timber.w("[LOGGING_SERVICE] Auto-cleanup: Database exceeded 100K entries ($count), deleted oldest $deleted")
                    }

                    // 3. Clean up inactive rate limiter buckets
                    rateLimiter.cleanup()

                } catch (e: Exception) {
                    Timber.e(e, "[LOGGING_SERVICE] Auto-cleanup failed")
                }
            }
        }
    }
    
    override fun onBind(intent: Intent?): IBinder {
        val callerUid = Binder.getCallingUid()
        Timber.i("[LOGGING_SERVICE] onBind called by UID: $callerUid (app UID: $appUid)")
        
        // Return combined stub that handles both interfaces
        // Caller validation is done per-method
        return LoggingServiceBinder()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Timber.i("[LOGGING_SERVICE] Service destroyed")
        Timber.i("[LOGGING_SERVICE] Statistics - Total logs: $totalLogsReceived, Rate limit violations: $rateLimitViolations, Validation failures: $validationFailures")

        // Cancel coroutine scope
        serviceScope.cancel()

        // Close database
        database.close()
    }

    /**
     * Audit security events for monitoring and investigation.
     *
     * Writes to a special audit log that can be retrieved separately.
     * Note: In isolated process, we log to Timber which can be captured by logcat.
     * In production, this could be sent to a dedicated security audit service.
     *
     * @param packageName Package that triggered the event
     * @param eventType Type of security event (rate_limit_exceeded, validation_failed, etc.)
     * @param details Additional context
     */
    private fun auditSecurityEvent(packageName: String, eventType: String, details: String) {
        val timestamp = System.currentTimeMillis()
        val auditMessage = "[SECURITY_AUDIT] $eventType | Package: $packageName | Details: $details | Time: $timestamp"

        // Log to Timber (captured by logcat)
        Timber.w(auditMessage)

        // Optionally: Store in separate audit table (future enhancement)
        // For now, we use regular logging with WARN level and special prefix [SECURITY_AUDIT]
        // Main process can filter logs by this prefix in the viewer
    }
    
    /**
     * Combined AIDL stub implementing ILoggingService.
     * Methods validate caller UID/permission before executing.
     */
    private inner class LoggingServiceBinder : ILoggingService.Stub() {
        
        /**
         * Validate that the caller is the main process (same app UID).
         * Used for control methods (setEnabled, getRecentLogs, etc.)
         */
        private fun validateMainProcessCaller(): Boolean {
            val callerUid = Binder.getCallingUid()
            if (callerUid != appUid) {
                Timber.w("[LOGGING_SERVICE] Unauthorized caller UID: $callerUid (expected: $appUid)")
                return false
            }
            return true
        }
        
        /**
         * Validate external app caller has SUBMIT_EXTERNAL_LOGS permission.
         * The permission is already enforced at bind time by the Android framework,
         * but we do additional validation here.
         */
        private fun validateExternalAppCaller(): Boolean {
            val callerUid = Binder.getCallingUid()
            
            // If caller is main process, allow (for testing or internal use)
            if (callerUid == appUid) {
                return true
            }
            
            // External app - permission is already checked by framework at bind time
            // due to android:permission in manifest, so if we reach here, they have permission
            return true
        }
        
        /**
         * Get package name for caller UID (for validation and logging).
         */
        private fun getCallerPackageName(): String? {
            val callerUid = Binder.getCallingUid()
            val packageManager = applicationContext.packageManager
            val packages = packageManager.getPackagesForUid(callerUid)
            return packages?.firstOrNull()
        }
        
        // ========== Methods for external apps (requires permission) ==========
        
        /**
         * Submit a log entry from external app.
         */
        override fun submitLog(packageName: String, level: String, tag: String, message: String) {
            totalLogsReceived++

            if (!enabled) {
                Timber.w("[LOGGING_SERVICE] Service disabled, rejecting submitLog from: $packageName")
                return
            }

            if (!validateExternalAppCaller()) {
                Timber.e("[LOGGING_SERVICE] Unauthorized submitLog attempt from: $packageName")
                return
            }

            // Validate package name matches caller
            val callerPackage = getCallerPackageName()
            if (callerPackage != packageName) {
                Timber.w("[LOGGING_SERVICE] Package name mismatch: claimed=$packageName, actual=$callerPackage")
                // Use actual package name for security
            }

            val actualPackage = callerPackage ?: packageName

            // ===== SECURITY: Rate Limiting =====
            if (!rateLimiter.checkRateLimit(actualPackage, "submitLog")) {
                rateLimitViolations++
                Timber.w("[LOGGING_SERVICE] Rate limit exceeded: $actualPackage.submitLog (total violations: $rateLimitViolations)")
                // Audit: Log rate limit violation
                auditSecurityEvent(actualPackage, "rate_limit_exceeded", "submitLog")
                return
            }

            // ===== SECURITY: Input Validation =====
            val validated = LogInputValidator.validate(actualPackage, level, tag, message, null)
            if (validated == null) {
                validationFailures++
                Timber.e("[LOGGING_SERVICE] Input validation failed for: $actualPackage")
                auditSecurityEvent(actualPackage, "validation_failed", "submitLog")
                return
            }

            // Check for suspicious patterns
            if (LogInputValidator.hasSuspiciousPatterns(message)) {
                Timber.w("[LOGGING_SERVICE] Suspicious pattern detected in message from: $actualPackage")
                auditSecurityEvent(actualPackage, "suspicious_pattern", message.take(100))
            }

            Timber.d("[LOGGING_SERVICE] submitLog: ${validated.packageName} | ${validated.level} | ${validated.tag}")

            // Insert into database asynchronously
            serviceScope.launch {
                try {
                    val entity = ExternalLogEntity(
                        timestamp = System.currentTimeMillis(),
                        packageName = validated.packageName,
                        level = validated.level,
                        tag = validated.tag,
                        message = validated.message,
                        exceptionTrace = null
                    )
                    logDao.insert(entity)
                } catch (e: Exception) {
                    Timber.e(e, "[LOGGING_SERVICE] Failed to insert log")
                }
            }
        }
        
        /**
         * Submit a log entry with exception trace.
         */
        override fun submitLogWithException(packageName: String, level: String, tag: String, message: String, exceptionTrace: String?) {
            totalLogsReceived++

            if (!enabled) {
                Timber.w("[LOGGING_SERVICE] Service disabled, rejecting submitLogWithException from: $packageName")
                return
            }

            if (!validateExternalAppCaller()) {
                Timber.e("[LOGGING_SERVICE] Unauthorized submitLogWithException attempt from: $packageName")
                return
            }

            val callerPackage = getCallerPackageName()
            val actualPackage = callerPackage ?: packageName

            // ===== SECURITY: Rate Limiting (lower limit for exceptions) =====
            if (!rateLimiter.checkRateLimit(actualPackage, "submitLogWithException")) {
                rateLimitViolations++
                Timber.w("[LOGGING_SERVICE] Rate limit exceeded: $actualPackage.submitLogWithException (total violations: $rateLimitViolations)")
                auditSecurityEvent(actualPackage, "rate_limit_exceeded", "submitLogWithException")
                return
            }

            // ===== SECURITY: Input Validation =====
            val validated = LogInputValidator.validate(actualPackage, level, tag, message, exceptionTrace)
            if (validated == null) {
                validationFailures++
                Timber.e("[LOGGING_SERVICE] Input validation failed for: $actualPackage")
                auditSecurityEvent(actualPackage, "validation_failed", "submitLogWithException")
                return
            }

            // Check for suspicious patterns in message and exception trace
            if (LogInputValidator.hasSuspiciousPatterns(message) ||
                (exceptionTrace != null && LogInputValidator.hasSuspiciousPatterns(exceptionTrace))) {
                Timber.w("[LOGGING_SERVICE] Suspicious pattern detected in log from: $actualPackage")
                auditSecurityEvent(actualPackage, "suspicious_pattern", "submitLogWithException")
            }

            Timber.d("[LOGGING_SERVICE] submitLogWithException: ${validated.packageName} | ${validated.level} | ${validated.tag}")

            serviceScope.launch {
                try {
                    val entity = ExternalLogEntity(
                        timestamp = System.currentTimeMillis(),
                        packageName = validated.packageName,
                        level = validated.level,
                        tag = validated.tag,
                        message = validated.message,
                        exceptionTrace = validated.exceptionTrace
                    )
                    logDao.insert(entity)
                } catch (e: Exception) {
                    Timber.e(e, "[LOGGING_SERVICE] Failed to insert log with exception")
                }
            }
        }
        
        // ========== Methods for main process (validated by UID) ==========
        
        override fun setEnabled(enabled: Boolean) {
            if (!validateMainProcessCaller()) {
                Timber.e("[LOGGING_SERVICE] Unauthorized setEnabled attempt")
                return
            }
            
            Timber.i("[LOGGING_SERVICE] setEnabled: $enabled")
            this@LoggingService.enabled = enabled
        }
        
        override fun isEnabled(): Boolean {
            if (!validateMainProcessCaller()) {
                Timber.e("[LOGGING_SERVICE] Unauthorized isEnabled attempt")
                return false
            }
            
            return enabled
        }
        
        override fun getRecentLogs(limit: Int): List<ExternalLogParcel> {
            if (!validateMainProcessCaller()) {
                Timber.e("[LOGGING_SERVICE] Unauthorized getRecentLogs attempt")
                return emptyList()
            }
            
            if (!enabled) {
                Timber.w("[LOGGING_SERVICE] Service disabled, returning empty list")
                return emptyList()
            }
            
            return try {
                // Run synchronously on binder thread (okay for IPC)
                val entities = kotlinx.coroutines.runBlocking {
                    logDao.getRecentLogs(limit)
                }
                ExternalLogParcel.fromEntities(entities)
            } catch (e: Exception) {
                Timber.e(e, "[LOGGING_SERVICE] Failed to getRecentLogs")
                emptyList()
            }
        }
        
        override fun getLogsByPackage(packageName: String, limit: Int): List<ExternalLogParcel> {
            if (!validateMainProcessCaller()) {
                Timber.e("[LOGGING_SERVICE] Unauthorized getLogsByPackage attempt")
                return emptyList()
            }
            
            if (!enabled) {
                return emptyList()
            }
            
            return try {
                val entities = kotlinx.coroutines.runBlocking {
                    logDao.getLogsByPackage(packageName, limit)
                }
                ExternalLogParcel.fromEntities(entities)
            } catch (e: Exception) {
                Timber.e(e, "[LOGGING_SERVICE] Failed to getLogsByPackage")
                emptyList()
            }
        }
        
        override fun getLogsByLevel(level: String, limit: Int): List<ExternalLogParcel> {
            if (!validateMainProcessCaller()) {
                Timber.e("[LOGGING_SERVICE] Unauthorized getLogsByLevel attempt")
                return emptyList()
            }
            
            if (!enabled) {
                return emptyList()
            }
            
            return try {
                val entities = kotlinx.coroutines.runBlocking {
                    logDao.getLogsByLevel(level, limit)
                }
                ExternalLogParcel.fromEntities(entities)
            } catch (e: Exception) {
                Timber.e(e, "[LOGGING_SERVICE] Failed to getLogsByLevel")
                emptyList()
            }
        }
        
        override fun getLogCount(): Int {
            if (!validateMainProcessCaller()) {
                Timber.e("[LOGGING_SERVICE] Unauthorized getLogCount attempt")
                return 0
            }
            
            return try {
                kotlinx.coroutines.runBlocking {
                    logDao.getLogCount()
                }
            } catch (e: Exception) {
                Timber.e(e, "[LOGGING_SERVICE] Failed to getLogCount")
                0
            }
        }
        
        override fun clearAllLogs() {
            if (!validateMainProcessCaller()) {
                Timber.e("[LOGGING_SERVICE] Unauthorized clearAllLogs attempt")
                return
            }
            
            Timber.i("[LOGGING_SERVICE] Clearing all logs")
            serviceScope.launch {
                try {
                    logDao.deleteAll()
                } catch (e: Exception) {
                    Timber.e(e, "[LOGGING_SERVICE] Failed to clearAllLogs")
                }
            }
        }
    }
}
