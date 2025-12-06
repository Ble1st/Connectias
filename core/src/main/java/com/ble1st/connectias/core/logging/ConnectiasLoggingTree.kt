package com.ble1st.connectias.core.logging

import android.util.Log
import com.ble1st.connectias.core.database.dao.SystemLogDao
import com.ble1st.connectias.core.database.entities.LogEntryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
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

    private val logChannel = Channel<LogEntryEntity>(capacity = Channel.UNLIMITED)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        scope.launch {
            for (entry in logChannel) {
                try {
                    logDao.insertLog(entry)
                } catch (e: Exception) {
                    // Fallback to standard Android log if DB write fails
                    Log.e("ConnectiasLoggingTree", "Failed to write log to DB", e)
                }
            }
        }
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // Optional: Filter out verbose logs from DB to save space
        if (priority < Log.INFO) return 

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
}
