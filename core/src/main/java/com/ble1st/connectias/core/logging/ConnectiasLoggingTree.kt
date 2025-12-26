package com.ble1st.connectias.core.logging

import android.content.Context
import android.os.Build
import android.util.Log
import com.ble1st.connectias.core.database.dao.SystemLogDao
import com.ble1st.connectias.core.database.entities.LogEntryEntity
import com.ble1st.connectias.core.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A Timber Tree that logs to the Room database asynchronously.
 * Uses a Channel to buffer logs and a background coroutine to write them.
 * Includes security redaction, dynamic configuration, and fault tolerance.
 */
@Singleton
class ConnectiasLoggingTree @Inject constructor(
    private val logDao: SystemLogDao,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
) : Timber.DebugTree() {

    private val logChannel = Channel<LogEntryEntity>(
        capacity = 1000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    @Volatile
    private var minPriority: Int = Log.INFO

    // Circuit Breaker
    private val consecutiveFailures = AtomicInteger(0)
    @Volatile
    private var circuitOpen = false
    private val FAILURE_THRESHOLD = 10
    private val CIRCUIT_RESET_TIME_MS = 5 * 60 * 1000L // 5 minutes

    init {
        // Start processing logs
        scope.launch {
            for (entry in logChannel) {
                processLogEntry(entry)
            }
        }

        // Observe Log Level Settings
        scope.launch {
            settingsRepository.observeLoggingLevel().collectLatest { levelString ->
                minPriority = mapLevelToPriority(levelString)
            }
        }

        // Log System Context once
        logSystemContext()
    }

    private fun logSystemContext() {
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionInfo = "App Version: ${packageInfo.versionName} (${packageInfo.longVersionCode})"
            val deviceInfo = "Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})"
            
            // Send directly to channel to bypass priority check or force INFO
            val contextLog = LogEntryEntity(
                timestamp = System.currentTimeMillis(),
                level = Log.INFO,
                tag = "SystemContext",
                message = "$versionInfo | $deviceInfo",
                threadName = Thread.currentThread().name,
                exceptionTrace = null
            )
            logChannel.trySend(contextLog)
        } catch (e: Exception) {
            // Ignore context logging errors
        }
    }

    private suspend fun processLogEntry(entry: LogEntryEntity) {
        if (circuitOpen) return

        try {
            logDao.insertLog(entry)
            // Reset failure count on success
            if (consecutiveFailures.get() > 0) {
                consecutiveFailures.set(0)
            }
        } catch (e: Exception) {
            // Fallback to standard Android log if DB write fails
            val failures = consecutiveFailures.incrementAndGet()
            if (failures >= FAILURE_THRESHOLD) {
                circuitOpen = true
                Timber.e("Logging circuit breaker OPENED. Pausing DB logs for 5 mins.")
                
                // Reset circuit after delay
                scope.launch {
                    delay(CIRCUIT_RESET_TIME_MS)
                    circuitOpen = false
                    consecutiveFailures.set(0)
                    Timber.i("Logging circuit breaker RESET.")
                }
            }
        }
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority < minPriority) return

        // 1. Redact sensitive info
        val safeMessage = LogRedactor.redact(message)

        val threadName = Thread.currentThread().name
        val exceptionTrace = t?.let {
            val sw = StringWriter()
            it.printStackTrace(PrintWriter(sw))
            sw.toString() // Stacktraces might contain secrets too, but usually code paths.
        }

        val entry = LogEntryEntity(
            timestamp = System.currentTimeMillis(),
            level = priority,
            tag = tag,
            message = safeMessage,
            threadName = threadName,
            exceptionTrace = exceptionTrace
        )

        // Non-blocking send
        logChannel.trySend(entry)
        
        // Still log to Logcat for debugging (redacted)
        super.log(priority, tag, safeMessage, t)
    }

    /**
     * Sets the minimum logging priority from a human-readable level string.
     * This can be called to immediately update the logging level.
     * 
     * @param levelString The logging level string (VERBOSE, DEBUG, INFO, WARN, ERROR, ASSERT)
     */
    fun setMinPriority(levelString: String?) {
        minPriority = mapLevelToPriority(levelString)
    }

    /**
     * Updates minimum priority from a human-readable level string.
     */
    private fun mapLevelToPriority(levelName: String?): Int {
        return when (levelName?.uppercase()) {
            "VERBOSE" -> Log.VERBOSE
            "DEBUG" -> Log.DEBUG
            "INFO" -> Log.INFO
            "WARN", "WARNING" -> Log.WARN
            "ERROR" -> Log.ERROR
            "ASSERT" -> Log.ASSERT
            else -> Log.INFO
        }
    }
}
