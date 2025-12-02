package com.ble1st.connectias.feature.reporting.models

import kotlinx.serialization.Serializable

/**
 * Configuration for report generation.
 */
@Serializable
data class ReportConfig(
    val title: String,
    val subtitle: String? = null,
    val author: String? = null,
    val format: ReportFormat = ReportFormat.PDF,
    val includeTimestamp: Boolean = true,
    val includeMetadata: Boolean = true,
    val includeSummary: Boolean = true,
    val includeCharts: Boolean = false,
    val pageSize: PageSize = PageSize.A4,
    val orientation: PageOrientation = PageOrientation.PORTRAIT
)

/**
 * Output format for reports.
 */
enum class ReportFormat {
    PDF,
    CSV,
    JSON,
    HTML
}

/**
 * Page size for PDF reports.
 */
enum class PageSize {
    A4,
    LETTER,
    LEGAL
}

/**
 * Page orientation for PDF reports.
 */
enum class PageOrientation {
    PORTRAIT,
    LANDSCAPE
}

/**
 * A section of a report.
 */
@Serializable
data class ReportSection(
    val title: String,
    val description: String? = null,
    val content: List<ReportContent> = emptyList(),
    val subsections: List<ReportSection> = emptyList()
)

/**
 * Content within a report section.
 */
@Serializable
sealed class ReportContent {
    /**
     * Plain text paragraph.
     */
    @Serializable
    data class Paragraph(val text: String) : ReportContent()

    /**
     * Bullet point list.
     */
    @Serializable
    data class BulletList(val items: List<String>) : ReportContent()

    /**
     * Numbered list.
     */
    @Serializable
    data class NumberedList(val items: List<String>) : ReportContent()

    /**
     * Key-value table.
     */
    @Serializable
    data class KeyValueTable(val entries: Map<String, String>) : ReportContent()

    /**
     * Data table with headers.
     */
    @Serializable
    data class DataTable(
        val headers: List<String>,
        val rows: List<List<String>>
    ) : ReportContent()

    /**
     * Code block.
     */
    @Serializable
    data class CodeBlock(val code: String, val language: String = "text") : ReportContent()

    /**
     * Alert/warning box.
     */
    @Serializable
    data class Alert(
        val message: String,
        val type: AlertType = AlertType.INFO
    ) : ReportContent()

    /**
     * Separator line.
     */
    @Serializable
    data object Separator : ReportContent()
}

/**
 * Alert type for report alerts.
 */
enum class AlertType {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}

/**
 * Data to be exported.
 */
@Serializable
data class ReportData(
    val type: ReportDataType,
    val name: String,
    val description: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val sections: List<ReportSection> = emptyList(),
    val metadata: Map<String, String> = emptyMap(),
    val rawData: String? = null // For CSV/JSON exports
)

/**
 * Type of data being reported.
 */
enum class ReportDataType {
    NETWORK_SCAN,
    SECURITY_AUDIT,
    PRIVACY_ANALYSIS,
    DEVICE_INFO,
    USB_DEVICES,
    SPEED_TEST,
    CUSTOM
}

/**
 * Result of a report generation.
 */
sealed class ReportResult {
    /**
     * Report generated successfully.
     */
    data class Success(
        val filePath: String,
        val format: ReportFormat,
        val size: Long
    ) : ReportResult()

    /**
     * Report generation failed.
     */
    data class Error(
        val message: String,
        val exception: Throwable? = null
    ) : ReportResult()

    /**
     * Progress update during generation.
     */
    data class Progress(
        val percent: Int,
        val currentSection: String?
    ) : ReportResult()
}

/**
 * Schedule for automatic report generation.
 */
@Serializable
data class ReportSchedule(
    val id: String = java.util.UUID.randomUUID().toString(),
    val reportType: ReportDataType,
    val config: ReportConfig,
    val frequency: ScheduleFrequency,
    val time: Int, // Minutes from midnight
    val daysOfWeek: List<Int> = emptyList(), // 1 = Monday, 7 = Sunday
    val dayOfMonth: Int? = null,
    val isEnabled: Boolean = true,
    val lastRun: Long? = null,
    val nextRun: Long? = null
)

/**
 * Frequency of scheduled reports.
 */
enum class ScheduleFrequency {
    DAILY,
    WEEKLY,
    MONTHLY,
    ON_DEMAND
}
