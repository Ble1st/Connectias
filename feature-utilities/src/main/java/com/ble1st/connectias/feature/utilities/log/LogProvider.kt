package com.ble1st.connectias.feature.utilities.log

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for log viewing operations.
 * Reads system logs and app logs.
 */
@Singleton
class LogProvider @Inject constructor() {

    /**
     * Log levels.
     */
    enum class LogLevel(val tag: String) {
        VERBOSE("V"),
        DEBUG("D"),
        INFO("I"),
        WARN("W"),
        ERROR("E")
    }

    /**
     * Reads system logcat logs.
     * Note: Requires READ_LOGS permission (deprecated on Android 6.0+).
     * 
     * @param filter Filter by log level or tag (optional)
     * @param maxLines Maximum number of lines to read
     * @return List of log entries
     */
    suspend fun readSystemLogs(filter: String? = null, maxLines: Int = 1000): List<LogEntry> = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("logcat -d -v time")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            
            val logs = mutableListOf<LogEntry>()
            var line: String?
            var count = 0
            
            while (reader.readLine().also { line = it } != null && count < maxLines) {
                line?.let { logLine ->
                    if (filter == null || logLine.contains(filter, ignoreCase = true)) {
                        val entry = parseLogLine(logLine)
                        if (entry != null) {
                            logs.add(entry)
                            count++
                        }
                    }
                }
            }
            
            reader.close()
            process.destroy()
            
            logs.reversed() // Most recent first
        } catch (e: Exception) {
            Timber.e(e, "Failed to read system logs")
            emptyList()
        }
    }

    /**
     * Clears system logcat buffer.
     */
    suspend fun clearSystemLogs(): Boolean = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec("logcat -c")
            process.waitFor()
            process.destroy()
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear system logs")
            false
        }
    }

    /**
     * Parses a logcat line into LogEntry.
     */
    private fun parseLogLine(line: String): LogEntry? {
        // Format: MM-DD HH:MM:SS.mmm PID TID LEVEL TAG: Message
        val pattern = Regex("""(\d{2}-\d{2})\s+(\d{2}:\d{2}:\d{2}\.\d{3})\s+(\d+)\s+(\d+)\s+([VDIWE])\s+([^:]+):\s*(.*)""")
        val match = pattern.find(line) ?: return null
        
        val level = when (match.groupValues[5]) {
            "V" -> LogLevel.VERBOSE
            "D" -> LogLevel.DEBUG
            "I" -> LogLevel.INFO
            "W" -> LogLevel.WARN
            "E" -> LogLevel.ERROR
            else -> LogLevel.DEBUG
        }

        return LogEntry(
            timestamp = "${match.groupValues[1]} ${match.groupValues[2]}",
            pid = match.groupValues[3].toIntOrNull() ?: 0,
            tid = match.groupValues[4].toIntOrNull() ?: 0,
            level = level,
            tag = match.groupValues[6],
            message = match.groupValues[7]
        )
    }

    /**
     * Filters logs by level.
     */
    fun filterLogsByLevel(logs: List<LogEntry>, level: LogLevel?): List<LogEntry> {
        if (level == null) return logs
        return logs.filter { it.level == level }
    }

    /**
     * Filters logs by tag.
     */
    fun filterLogsByTag(logs: List<LogEntry>, tag: String): List<LogEntry> {
        if (tag.isBlank()) return logs
        return logs.filter { it.tag.contains(tag, ignoreCase = true) }
    }
}

/**
 * Represents a log entry.
 */
data class LogEntry(
    val timestamp: String,
    val pid: Int,
    val tid: Int,
    val level: LogProvider.LogLevel,
    val tag: String,
    val message: String
)

