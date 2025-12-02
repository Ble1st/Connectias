package com.ble1st.connectias.feature.reporting

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import com.ble1st.connectias.feature.reporting.models.ReportConfig
import com.ble1st.connectias.feature.reporting.models.ReportData
import com.ble1st.connectias.feature.reporting.models.ReportDataType
import com.ble1st.connectias.feature.reporting.models.ReportFormat
import com.ble1st.connectias.feature.reporting.models.ReportResult
import com.ble1st.connectias.feature.reporting.models.ReportSchedule
import com.ble1st.connectias.feature.reporting.models.ReportSection
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for report generation functionality.
 *
 * Supports:
 * - PDF report generation
 * - CSV export
 * - JSON export
 * - HTML export
 * - Report sharing
 * - Scheduled reports
 */
@Singleton
class ReportingProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    private val _schedules = MutableStateFlow<List<ReportSchedule>>(emptyList())
    val schedules: StateFlow<List<ReportSchedule>> = _schedules.asStateFlow()

    private val reportsDir: File
        get() = File(context.getExternalFilesDir(null), "reports").apply {
            if (!exists()) mkdirs()
        }

    /**
     * Generates a report with the given configuration and data.
     */
    fun generateReport(
        config: ReportConfig,
        data: ReportData
    ): Flow<ReportResult> = flow {
        try {
            emit(ReportResult.Progress(0, "Initializing..."))

            val fileName = generateFileName(config.title, config.format)
            val file = File(reportsDir, fileName)

            emit(ReportResult.Progress(20, "Preparing content..."))

            when (config.format) {
                ReportFormat.PDF -> {
                    emit(ReportResult.Progress(40, "Generating PDF..."))
                    generatePdfReport(file, config, data)
                }
                ReportFormat.CSV -> {
                    emit(ReportResult.Progress(40, "Generating CSV..."))
                    generateCsvReport(file, data)
                }
                ReportFormat.JSON -> {
                    emit(ReportResult.Progress(40, "Generating JSON..."))
                    generateJsonReport(file, data)
                }
                ReportFormat.HTML -> {
                    emit(ReportResult.Progress(40, "Generating HTML..."))
                    generateHtmlReport(file, config, data)
                }
            }

            emit(ReportResult.Progress(90, "Finalizing..."))

            emit(ReportResult.Success(
                filePath = file.absolutePath,
                format = config.format,
                size = file.length()
            ))
        } catch (e: Exception) {
            Timber.e(e, "Error generating report")
            emit(ReportResult.Error("Failed to generate report: ${e.message}", e))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Generates a PDF report.
     */
    private suspend fun generatePdfReport(
        file: File,
        config: ReportConfig,
        data: ReportData
    ) = withContext(Dispatchers.IO) {
        // Note: iText PDF generation would be implemented here
        // For now, we create a simple text-based PDF simulation
        file.writeText(buildString {
            appendLine("=" .repeat(60))
            appendLine(config.title.uppercase())
            config.subtitle?.let { appendLine(it) }
            appendLine("=" .repeat(60))
            appendLine()

            if (config.includeTimestamp) {
                appendLine("Generated: ${formatTimestamp(System.currentTimeMillis())}")
                appendLine()
            }

            if (config.includeMetadata && data.metadata.isNotEmpty()) {
                appendLine("METADATA")
                appendLine("-".repeat(40))
                data.metadata.forEach { (key, value) ->
                    appendLine("$key: $value")
                }
                appendLine()
            }

            if (config.includeSummary) {
                appendLine("SUMMARY")
                appendLine("-".repeat(40))
                appendLine("Report Type: ${data.type.name}")
                appendLine("Data Name: ${data.name}")
                data.description?.let { appendLine("Description: $it") }
                appendLine()
            }

            data.sections.forEach { section ->
                appendSection(section, 0)
            }
        })
    }

    private fun StringBuilder.appendSection(section: ReportSection, level: Int) {
        val indent = "  ".repeat(level)
        
        appendLine("$indent${section.title.uppercase()}")
        appendLine("$indent${"-".repeat(40)}")
        section.description?.let { appendLine("$indent$it") }
        appendLine()

        section.content.forEach { content ->
            when (content) {
                is com.ble1st.connectias.feature.reporting.models.ReportContent.Paragraph -> {
                    appendLine("$indent${content.text}")
                    appendLine()
                }
                is com.ble1st.connectias.feature.reporting.models.ReportContent.BulletList -> {
                    content.items.forEach { appendLine("$indent• $it") }
                    appendLine()
                }
                is com.ble1st.connectias.feature.reporting.models.ReportContent.NumberedList -> {
                    content.items.forEachIndexed { index, item ->
                        appendLine("$indent${index + 1}. $item")
                    }
                    appendLine()
                }
                is com.ble1st.connectias.feature.reporting.models.ReportContent.KeyValueTable -> {
                    content.entries.forEach { (key, value) ->
                        appendLine("$indent$key: $value")
                    }
                    appendLine()
                }
                is com.ble1st.connectias.feature.reporting.models.ReportContent.DataTable -> {
                    appendLine("$indent${content.headers.joinToString(" | ")}")
                    appendLine("$indent${"-".repeat(content.headers.sumOf { it.length + 3 })}")
                    content.rows.forEach { row ->
                        appendLine("$indent${row.joinToString(" | ")}")
                    }
                    appendLine()
                }
                is com.ble1st.connectias.feature.reporting.models.ReportContent.CodeBlock -> {
                    appendLine("$indent```${content.language}")
                    content.code.lines().forEach { appendLine("$indent$it") }
                    appendLine("$indent```")
                    appendLine()
                }
                is com.ble1st.connectias.feature.reporting.models.ReportContent.Alert -> {
                    appendLine("$indent[${content.type.name}] ${content.message}")
                    appendLine()
                }
                is com.ble1st.connectias.feature.reporting.models.ReportContent.Separator -> {
                    appendLine("$indent${"-".repeat(60)}")
                    appendLine()
                }
            }
        }

        section.subsections.forEach { subsection ->
            appendSection(subsection, level + 1)
        }
    }

    /**
     * Generates a CSV report.
     */
    private suspend fun generateCsvReport(
        file: File,
        data: ReportData
    ) = withContext(Dispatchers.IO) {
        FileWriter(file).use { writer ->
            // Write header
            writer.appendLine("Section,Key,Value")

            // Write metadata
            data.metadata.forEach { (key, value) ->
                writer.appendLine("Metadata,${escapeCSV(key)},${escapeCSV(value)}")
            }

            // Write sections
            data.sections.forEach { section ->
                writeCsvSection(writer, section, "")
            }
        }
    }

    private fun writeCsvSection(writer: FileWriter, section: ReportSection, prefix: String) {
        val sectionName = if (prefix.isEmpty()) section.title else "$prefix > ${section.title}"
        
        section.content.forEach { content ->
            when (content) {
                is com.ble1st.connectias.feature.reporting.models.ReportContent.KeyValueTable -> {
                    content.entries.forEach { (key, value) ->
                        writer.appendLine("$sectionName,${escapeCSV(key)},${escapeCSV(value)}")
                    }
                }
                is com.ble1st.connectias.feature.reporting.models.ReportContent.DataTable -> {
                    content.rows.forEach { row ->
                        val rowData = row.mapIndexed { index, value ->
                            "${content.headers.getOrElse(index) { "Column$index" }}=$value"
                        }.joinToString(";")
                        writer.appendLine("$sectionName,Data,${escapeCSV(rowData)}")
                    }
                }
                else -> {
                    // Skip non-tabular content for CSV
                }
            }
        }

        section.subsections.forEach { subsection ->
            writeCsvSection(writer, subsection, sectionName)
        }
    }

    /**
     * Generates a JSON report.
     */
    private suspend fun generateJsonReport(
        file: File,
        data: ReportData
    ) = withContext(Dispatchers.IO) {
        val jsonContent = json.encodeToString(data)
        file.writeText(jsonContent)
    }

    /**
     * Generates an HTML report.
     */
    private suspend fun generateHtmlReport(
        file: File,
        config: ReportConfig,
        data: ReportData
    ) = withContext(Dispatchers.IO) {
        file.writeText(buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html lang=\"en\">")
            appendLine("<head>")
            appendLine("<meta charset=\"UTF-8\">")
            appendLine("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
            appendLine("<title>${config.title}</title>")
            appendLine("<style>")
            appendLine("""
                body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; 
                       max-width: 900px; margin: 0 auto; padding: 20px; }
                h1 { color: #1a1a2e; border-bottom: 2px solid #4a4e69; padding-bottom: 10px; }
                h2 { color: #4a4e69; }
                h3 { color: #9a8c98; }
                table { width: 100%; border-collapse: collapse; margin: 15px 0; }
                th, td { padding: 10px; text-align: left; border: 1px solid #ddd; }
                th { background-color: #4a4e69; color: white; }
                tr:nth-child(even) { background-color: #f9f9f9; }
                .alert { padding: 15px; margin: 15px 0; border-radius: 5px; }
                .alert-info { background-color: #e3f2fd; border-left: 4px solid #2196f3; }
                .alert-success { background-color: #e8f5e9; border-left: 4px solid #4caf50; }
                .alert-warning { background-color: #fff3e0; border-left: 4px solid #ff9800; }
                .alert-error { background-color: #ffebee; border-left: 4px solid #f44336; }
                pre { background-color: #f5f5f5; padding: 15px; overflow-x: auto; border-radius: 5px; }
                .metadata { background-color: #f5f5f5; padding: 15px; margin: 15px 0; border-radius: 5px; }
            """.trimIndent())
            appendLine("</style>")
            appendLine("</head>")
            appendLine("<body>")

            appendLine("<h1>${config.title}</h1>")
            config.subtitle?.let { appendLine("<p><em>$it</em></p>") }

            if (config.includeTimestamp) {
                appendLine("<p>Generated: ${formatTimestamp(System.currentTimeMillis())}</p>")
            }

            if (config.includeMetadata && data.metadata.isNotEmpty()) {
                appendLine("<div class=\"metadata\">")
                appendLine("<h3>Metadata</h3>")
                data.metadata.forEach { (key, value) ->
                    appendLine("<p><strong>$key:</strong> $value</p>")
                }
                appendLine("</div>")
            }

            data.sections.forEach { section ->
                appendHtmlSection(section, 2)
            }

            appendLine("</body>")
            appendLine("</html>")
        })
    }

    private fun StringBuilder.appendHtmlSection(section: ReportSection, level: Int) {
        val tag = "h${minOf(level, 6)}"
        appendLine("<$tag>${section.title}</$tag>")
        section.description?.let { appendLine("<p>$it</p>") }

        section.content.forEach { content ->
            when (content) {
                is com.ble1st.connectias.feature.reporting.models.ReportContent.Paragraph -> {
                    appendLine("<p>${content.text}</p>")
                }
                is com.ble1st.connectias.feature.reporting.models.ReportContent.BulletList -> {
                    appendLine("<ul>")
                    content.items.forEach { appendLine("<li>$it</li>") }
                    appendLine("</ul>")
                }
                is com.ble1st.connectias.feature.reporting.models.ReportContent.NumberedList -> {
                    appendLine("<ol>")
                    content.items.forEach { appendLine("<li>$it</li>") }
                    appendLine("</ol>")
                }
                is com.ble1st.connectias.feature.reporting.models.ReportContent.KeyValueTable -> {
                    appendLine("<table>")
                    appendLine("<tr><th>Key</th><th>Value</th></tr>")
                    content.entries.forEach { (key, value) ->
                        appendLine("<tr><td>$key</td><td>$value</td></tr>")
                    }
                    appendLine("</table>")
                }
                is com.ble1st.connectias.feature.reporting.models.ReportContent.DataTable -> {
                    appendLine("<table>")
                    appendLine("<tr>${content.headers.joinToString("") { "<th>$it</th>" }}</tr>")
                    content.rows.forEach { row ->
                        appendLine("<tr>${row.joinToString("") { "<td>$it</td>" }}</tr>")
                    }
                    appendLine("</table>")
                }
                is com.ble1st.connectias.feature.reporting.models.ReportContent.CodeBlock -> {
                    appendLine("<pre><code class=\"language-${content.language}\">${content.code}</code></pre>")
                }
                is com.ble1st.connectias.feature.reporting.models.ReportContent.Alert -> {
                    val alertClass = "alert-${content.type.name.lowercase()}"
                    appendLine("<div class=\"alert $alertClass\">${content.message}</div>")
                }
                is com.ble1st.connectias.feature.reporting.models.ReportContent.Separator -> {
                    appendLine("<hr>")
                }
            }
        }

        section.subsections.forEach { subsection ->
            appendHtmlSection(subsection, level + 1)
        }
    }

    /**
     * Exports data to CSV.
     */
    suspend fun exportToCsv(data: ReportData): File = withContext(Dispatchers.IO) {
        val fileName = generateFileName(data.name, ReportFormat.CSV)
        val file = File(reportsDir, fileName)
        generateCsvReport(file, data)
        file
    }

    /**
     * Exports data to JSON.
     */
    suspend fun exportToJson(data: ReportData): File = withContext(Dispatchers.IO) {
        val fileName = generateFileName(data.name, ReportFormat.JSON)
        val file = File(reportsDir, fileName)
        generateJsonReport(file, data)
        file
    }

    /**
     * Shares a report file.
     */
    suspend fun shareReport(file: File) = withContext(Dispatchers.Main) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = getMimeType(file.extension)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(intent, "Share Report").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Timber.e(e, "Error sharing report")
        }
    }

    /**
     * Gets all generated reports.
     */
    fun getReports(): List<File> {
        return reportsDir.listFiles()?.toList() ?: emptyList()
    }

    /**
     * Deletes a report.
     */
    fun deleteReport(file: File): Boolean {
        return file.delete()
    }

    /**
     * Adds a scheduled report.
     */
    fun addSchedule(schedule: ReportSchedule) {
        _schedules.update { it + schedule }
    }

    /**
     * Removes a scheduled report.
     */
    fun removeSchedule(id: String) {
        _schedules.update { it.filter { s -> s.id != id } }
    }

    // Helper functions

    private fun generateFileName(title: String, format: ReportFormat): String {
        val sanitized = title.replace(Regex("[^a-zA-Z0-9]"), "_").take(30)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val extension = when (format) {
            ReportFormat.PDF -> "pdf"
            ReportFormat.CSV -> "csv"
            ReportFormat.JSON -> "json"
            ReportFormat.HTML -> "html"
        }
        return "${sanitized}_$timestamp.$extension"
    }

    private fun formatTimestamp(timestamp: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return formatter.format(Date(timestamp))
    }

    private fun escapeCSV(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    private fun getMimeType(extension: String): String {
        return when (extension.lowercase()) {
            "pdf" -> "application/pdf"
            "csv" -> "text/csv"
            "json" -> "application/json"
            "html" -> "text/html"
            else -> "application/octet-stream"
        }
    }
}
