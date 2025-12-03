package com.ble1st.connectias.feature.reporting.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.feature.reporting.ReportingProvider
import com.ble1st.connectias.feature.reporting.models.ReportConfig
import com.ble1st.connectias.feature.reporting.models.ReportContent
import com.ble1st.connectias.feature.reporting.models.ReportData
import com.ble1st.connectias.feature.reporting.models.ReportDataType
import com.ble1st.connectias.feature.reporting.models.ReportFormat
import com.ble1st.connectias.feature.reporting.models.ReportResult
import com.ble1st.connectias.feature.reporting.models.ReportSection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * ViewModel for Reporting screen.
 */
@HiltViewModel
class ReportingViewModel @Inject constructor(
    private val reportingProvider: ReportingProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportingUiState())
    val uiState: StateFlow<ReportingUiState> = _uiState.asStateFlow()

    val schedules = reportingProvider.schedules

    init {
        loadReports()
    }

    private fun loadReports() {
        val reports = reportingProvider.getReports()
        _uiState.update { it.copy(savedReports = reports) }
    }

    /**
     * Generates a report with the given configuration.
     */
    fun generateReport(
        title: String,
        subtitle: String? = null,
        format: ReportFormat,
        dataType: ReportDataType,
        includeTimestamp: Boolean = true,
        includeMetadata: Boolean = true
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true, progress = 0, progressMessage = "Starting...") }

            val config = ReportConfig(
                title = title,
                subtitle = subtitle,
                format = format,
                includeTimestamp = includeTimestamp,
                includeMetadata = includeMetadata,
                includeSummary = true
            )

            val data = createSampleData(dataType, title)

            reportingProvider.generateReport(config, data).collect { result ->
                handleReportResult(result)
            }
        }
    }

    private fun createSampleData(dataType: ReportDataType, name: String): ReportData {
        return ReportData(
            type = dataType,
            name = name,
            description = "Generated report for $name",
            sections = listOf(
                ReportSection(
                    title = "Overview",
                    description = "Summary of the report data",
                    content = listOf(
                        ReportContent.Paragraph("This report was generated automatically."),
                        ReportContent.KeyValueTable(
                            mapOf(
                                "Report Type" to dataType.name,
                                "Generation Time" to System.currentTimeMillis().toString()
                            )
                        )
                    )
                )
            ),
            metadata = mapOf(
                "Generator" to "Connectias",
                "Version" to "1.0.0"
            )
        )
    }

    private fun handleReportResult(result: ReportResult) {
        when (result) {
            is ReportResult.Progress -> {
                _uiState.update { it.copy(
                    progress = result.percent,
                    progressMessage = result.currentSection
                ) }
            }
            is ReportResult.Success -> {
                _uiState.update { it.copy(
                    isGenerating = false,
                    progress = 100,
                    snackbarMessage = "Report generated successfully",
                    lastGeneratedReport = File(result.filePath)
                ) }
                loadReports()
            }
            is ReportResult.Error -> {
                _uiState.update { it.copy(
                    isGenerating = false,
                    progress = 0,
                    snackbarMessage = "Error: ${result.message}"
                ) }
                Timber.e(result.exception, "Report generation failed")
            }
        }
    }

    /**
     * Shares a report file.
     */
    fun shareReport(file: File) {
        viewModelScope.launch {
            reportingProvider.shareReport(file)
        }
    }

    /**
     * Deletes a report file.
     */
    fun deleteReport(file: File) {
        val deleted = reportingProvider.deleteReport(file)
        if (deleted) {
            _uiState.update { it.copy(snackbarMessage = "Report deleted") }
            loadReports()
        }
    }

    /**
     * Sets the selected format.
     */
    fun setSelectedFormat(format: ReportFormat) {
        _uiState.update { it.copy(selectedFormat = format) }
    }

    /**
     * Sets the selected data type.
     */
    fun setSelectedDataType(type: ReportDataType) {
        _uiState.update { it.copy(selectedDataType = type) }
    }

    /**
     * Shows the create report dialog.
     */
    fun showCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = true) }
    }

    /**
     * Hides the create report dialog.
     */
    fun hideCreateDialog() {
        _uiState.update { it.copy(showCreateDialog = false) }
    }

    /**
     * Clears the snackbar message.
     */
    fun clearSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    /**
     * Refreshes the reports list.
     */
    fun refresh() {
        loadReports()
    }
}

/**
 * UI state for Reporting screen.
 */
data class ReportingUiState(
    val isGenerating: Boolean = false,
    val progress: Int = 0,
    val progressMessage: String? = null,
    val savedReports: List<File> = emptyList(),
    val lastGeneratedReport: File? = null,
    val selectedFormat: ReportFormat = ReportFormat.PDF,
    val selectedDataType: ReportDataType = ReportDataType.CUSTOM,
    val showCreateDialog: Boolean = false,
    val snackbarMessage: String? = null
)

