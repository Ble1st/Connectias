package com.ble1st.connectias.core.logging

import android.util.Log
import com.ble1st.connectias.core.database.dao.SystemLogDao
import com.ble1st.connectias.core.database.entities.LogEntryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.PrintWriter
import java.io.StringWriter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A Timber Tree that logs to the Room database asynchronously.
 * Uses a Channel to buffer logs and a background coroutine to write them.
 */
@Singleton
class ConnectiasLoggingTree @Inject constructor(
    private val logDao: SystemLogDao
) : Timber.DebugTree() {

    private val logChannel = Channel<LogEntryEntity>(
        capacity = 1000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    @Volatile
    private var minPriority: Int = Log.INFO

    init {
        scope.launch {
            for (entry in logChannel) {
                try {
                    logDao.insertLog(entry)
                } catch (e: Exception) {
                    // Fallback to standard Android log if DB write fails
                    Timber.e(e, "Failed to write log to DB")
                }
            }
        }
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority < minPriority) return

        val threadName = Thread.currentThread().name
        val exceptionTrace = t?.let {
            val sw = StringWriter()
            it.printStackTrace(PrintWriter(sw))
            sw.toString()
        }

        val entry = LogEntryEntity(
            timestamp = System.currentTimeMillis(),
            level = priority,
            tag = tag,
            message = message,
            threadName = threadName,
            exceptionTrace = exceptionTrace
        )

        // Non-blocking send
        logChannel.trySend(entry)
        
        // Still log to Logcat for debugging
        super.log(priority, tag, message, t)
    }

    /**
     * Updates the minimum priority that will be written to the DB.
     */
    fun setMinPriority(priority: Int) {
        minPriority = priority
    }

    /**
     * Updates minimum priority from a human-readable level string.
     */
    fun setMinPriority(levelName: String?) {
        minPriority = mapLevelToPriority(levelName)
    }

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
