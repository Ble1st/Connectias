package com.ble1st.connectias.analytics.store

import android.content.Context
import com.ble1st.connectias.analytics.model.PluginPerformanceSample
import com.ble1st.connectias.analytics.model.PluginUiActionEvent
import com.ble1st.connectias.analytics.model.SecurityEventCounterSample
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Append-only JSONL store for plugin analytics.
 *
 * Notes:
 * - Designed for cross-process reads; writes are best-effort.
 * - Retention is enforced by compaction (rewrite last N days).
 */
@Singleton
class PluginAnalyticsStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    private val dir: File by lazy {
        File(context.filesDir, "analytics").also { it.mkdirs() }
    }

    private val perfFile: File by lazy { File(dir, "perf_samples.jsonl") }
    private val uiFile: File by lazy { File(dir, "ui_actions.jsonl") }
    private val securityFile: File by lazy { File(dir, "security_events.jsonl") }

    suspend fun appendPerformance(sample: PluginPerformanceSample) = withContext(Dispatchers.IO) {
        appendJsonLine(perfFile, json.encodeToString(sample))
    }

    suspend fun appendUiAction(event: PluginUiActionEvent) = withContext(Dispatchers.IO) {
        appendJsonLine(uiFile, json.encodeToString(event))
    }

    suspend fun appendSecurityEvent(sample: SecurityEventCounterSample) = withContext(Dispatchers.IO) {
        appendJsonLine(securityFile, json.encodeToString(sample))
    }

    suspend fun readPerformanceSince(sinceEpochMillis: Long): List<PluginPerformanceSample> =
        withContext(Dispatchers.IO) { readJsonLines(perfFile) { json.decodeFromString(it) } }

    suspend fun readUiActionsSince(sinceEpochMillis: Long): List<PluginUiActionEvent> =
        withContext(Dispatchers.IO) { readJsonLines(uiFile) { json.decodeFromString(it) } }

    suspend fun readSecurityEventsSince(sinceEpochMillis: Long): List<SecurityEventCounterSample> =
        withContext(Dispatchers.IO) { readJsonLines(securityFile) { json.decodeFromString(it) } }

    suspend fun compactRetention(retentionDays: Int) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - retentionDays * 24L * 60 * 60 * 1000
        compactFile(perfFile, cutoff)
        compactFile(uiFile, cutoff)
        compactFile(securityFile, cutoff)
    }

    private fun appendJsonLine(file: File, jsonLine: String) {
        try {
            file.parentFile?.mkdirs()
            file.appendText(jsonLine)
            file.appendText("\n")
        } catch (e: Exception) {
            Timber.w(e, "[ANALYTICS] Failed to append to ${file.name}")
        }
    }

    private fun <T> readJsonLines(
        file: File,
        decode: (String) -> T
    ): List<T> {
        if (!file.exists()) return emptyList()
        val out = ArrayList<T>()
        try {
            file.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                try {
                    val obj = decode(line)
                    // Timestamp filtering done in compaction; keep decode generic here.
                    // We apply timestamp filtering in compactFile and in repository aggregation.
                    out.add(obj)
                } catch (_: Exception) {
                    // Skip corrupted lines
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "[ANALYTICS] Failed to read ${file.name}")
        }
        return out
    }

    private fun compactFile(file: File, cutoffEpochMillis: Long) {
        if (!file.exists()) return
        val tmp = File(file.parentFile, "${file.name}.tmp")
        try {
            tmp.bufferedWriter().use { w ->
                file.forEachLine { line ->
                    if (line.isBlank()) return@forEachLine
                    // Best-effort: keep lines that contain a timestamp >= cutoff.
                    // We avoid decoding for speed; fallback keeps the line if unsure.
                    val keep = try {
                        val idx = line.indexOf("\"timestamp\":")
                        if (idx == -1) true else {
                            val start = idx + "\"timestamp\":".length
                            val end = line.indexOfAny(charArrayOf(',', '}'), startIndex = start).let { if (it == -1) line.length else it }
                            val ts = line.substring(start, end).trim().toLong()
                            ts >= cutoffEpochMillis
                        }
                    } catch (_: Exception) {
                        true
                    }
                    if (keep) {
                        w.appendLine(line)
                    }
                }
            }
            if (tmp.exists()) {
                if (!tmp.renameTo(file)) {
                    // Fallback replace
                    tmp.copyTo(file, overwrite = true)
                    tmp.delete()
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "[ANALYTICS] Failed to compact ${file.name}")
            tmp.delete()
        }
    }
}

