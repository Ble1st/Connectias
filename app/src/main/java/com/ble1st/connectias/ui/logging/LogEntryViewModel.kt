package com.ble1st.connectias.ui.logging

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.core.domain.GetLogsUseCase
import com.ble1st.connectias.core.model.LogLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * ViewModel for the Log Viewer screen.
 * Provides log entries from the database using GetLogsUseCase.
 */
@HiltViewModel
class LogEntryViewModel @Inject constructor(
    private val getLogsUseCase: GetLogsUseCase
) : ViewModel() {
    
    private var currentMinLevel: LogLevel = LogLevel.DEBUG
    private var currentLimit: Int = 1000
    
    val logsResult: StateFlow<com.ble1st.connectias.core.domain.LogsResult?> = 
        getLogsUseCase(
            minLevel = currentMinLevel,
            limit = currentLimit
        ).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
    
    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState = _exportState.asStateFlow()
    
    sealed class ExportState {
        object Idle : ExportState()
        object Exporting : ExportState()
        data class Success(val message: String) : ExportState()
        data class Error(val message: String) : ExportState()
    }
    
    /**
     * Updates the minimum log level filter.
     */
    fun setMinLevel(level: LogLevel) {
        currentMinLevel = level
        // The StateFlow will automatically update via the UseCase
    }
    
    /**
     * Updates the log limit.
     */
    fun setLimit(limit: Int) {
        currentLimit = limit
        // The StateFlow will automatically update via the UseCase
    }
    
    /**
     * Exports filtered logs to a text file.
     */
    fun exportLogs(
        context: Context,
        uri: Uri,
        logs: List<com.ble1st.connectias.core.model.LogEntry>
    ) {
        viewModelScope.launch {
            try {
                _exportState.value = ExportState.Exporting
                
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    writeLogsToTxt(outputStream, logs)
                }
                
                _exportState.value = ExportState.Success(
                    "Successfully exported ${logs.size} log entries"
                )
            } catch (e: Exception) {
                _exportState.value = ExportState.Error(
                    "Failed to export logs: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Resets export state to idle.
     */
    fun resetExportState() {
        _exportState.value = ExportState.Idle
    }
    
    /**
     * Writes log entries to a text file.
     */
    private fun writeLogsToTxt(
        outputStream: OutputStream,
        logs: List<com.ble1st.connectias.core.model.LogEntry>
    ) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        val fileDateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        
        // Write header
        outputStream.write("Connectias Log Export\n".toByteArray())
        outputStream.write("Generated: ${dateFormat.format(Date())}\n".toByteArray())
        outputStream.write("Total Logs: ${logs.size}\n".toByteArray())
        outputStream.write("${"=".repeat(80)}\n\n".toByteArray())
        
        // Write log entries
        logs.forEach { log ->
            val timestamp = dateFormat.format(Date(log.timestamp))
            val line = buildString {
                append("[$timestamp] ")
                append("[${log.level.name}] ")
                append("[${log.tag}] ")
                append(log.message)
                
                if (!log.throwable.isNullOrBlank()) {
                    append("\n")
                    append("Exception: ")
                    append(log.throwable!!.replace("\n", "\n    "))
                }
                append("\n\n")
            }
            outputStream.write(line.toByteArray())
        }
        
        // Write footer
        outputStream.write("${"=".repeat(80)}\n".toByteArray())
        outputStream.write("End of log export\n".toByteArray())
    }
}
