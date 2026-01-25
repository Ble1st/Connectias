package com.ble1st.connectias.ui.plugin.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.ble1st.connectias.analytics.repo.PluginAnalyticsRepository
import com.ble1st.connectias.analytics.export.AnalyticsExportBundle
import com.ble1st.connectias.analytics.export.AnalyticsExportMetadata
import com.ble1st.connectias.analytics.export.AnalyticsPluginStat
import com.ble1st.connectias.analytics.export.EncryptedAnalyticsExportWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginAnalyticsDashboardScreen(
    repo: PluginAnalyticsRepository,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var window by remember { mutableStateOf(PluginAnalyticsRepository.TimeWindow.LAST_24H) }
    var stats by remember { mutableStateOf<List<PluginAnalyticsRepository.PluginPerfStats>>(emptyList()) }
    var selected by remember { mutableStateOf<PluginAnalyticsRepository.PluginPerfStats?>(null) }

    var showExportDialog by remember { mutableStateOf(false) }
    var passphrase by remember { mutableStateOf("") }
    var passphraseConfirm by remember { mutableStateOf("") }
    var exportPendingUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            exportPendingUri = uri
            showExportDialog = true
        }
    }

    LaunchedEffect(window) {
        stats = withContext(Dispatchers.Default) { repo.getPerfStats(window) }
    }

    if (showExportDialog) {
        ExportPassphraseDialog(
            passphrase = passphrase,
            passphraseConfirm = passphraseConfirm,
            onPassphraseChange = { passphrase = it },
            onPassphraseConfirmChange = { passphraseConfirm = it },
            onDismiss = {
                showExportDialog = false
                passphrase = ""
                passphraseConfirm = ""
                exportPendingUri = null
            },
            onConfirm = {
                val uri = exportPendingUri ?: return@ExportPassphraseDialog
                val passChars = passphrase.toCharArray()
                val now = System.currentTimeMillis()
                val start = now - window.durationMillis
                val bundle = AnalyticsExportBundle(
                    metadata = AnalyticsExportMetadata(
                        createdAtEpochMillis = now,
                        windowLabel = window.label,
                        windowStartEpochMillis = start,
                        windowEndEpochMillis = now
                    ),
                    pluginStats = stats.map {
                        AnalyticsPluginStat(
                            pluginId = it.pluginId,
                            samples = it.samples,
                            avgCpu = it.avgCpu,
                            peakCpu = it.peakCpu,
                            avgMemMB = it.avgMemMB,
                            peakMemMB = it.peakMemMB,
                            netInBytes = it.netInBytes,
                            netOutBytes = it.netOutBytes,
                            uiActions = it.uiActions,
                            rateLimitHits = it.rateLimitHits
                        )
                    }
                )

                showExportDialog = false
                passphrase = ""
                passphraseConfirm = ""
                exportPendingUri = null

                scope.launch {
                    try {
                        EncryptedAnalyticsExportWriter.writeEncryptedZip(
                            context = context,
                            outputUri = uri,
                            passphrase = passChars,
                            exportBundle = bundle
                        )
                        snackbarHostState.showSnackbar("✅ Analytics export created (encrypted)")
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("❌ Export failed: ${e.message ?: "unknown error"}")
                    } finally {
                        passChars.fill('\u0000')
                    }
                }
            }
        )
    }

    selected?.let { s ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("Plugin analytics") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Plugin: ${s.pluginId}")
                    Text("Samples: ${s.samples}")
                    Text("CPU avg/peak: ${format1(s.avgCpu)}% / ${format1(s.peakCpu)}%")
                    Text("RAM avg/peak: ${format1(s.avgMemMB)} MB / ${s.peakMemMB} MB")
                    Text("Net in/out: ${formatBytes(s.netInBytes)} / ${formatBytes(s.netOutBytes)}")
                    Text("UI actions: ${s.uiActions}")
                    Text("Rate limit hits: ${s.rateLimitHits}")
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Close") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plugin Analytics") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
        ,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeWindowChip("24h", window == PluginAnalyticsRepository.TimeWindow.LAST_24H) {
                    window = PluginAnalyticsRepository.TimeWindow.LAST_24H
                }
                TimeWindowChip("7d", window == PluginAnalyticsRepository.TimeWindow.LAST_7D) {
                    window = PluginAnalyticsRepository.TimeWindow.LAST_7D
                }
                TimeWindowChip("30d", window == PluginAnalyticsRepository.TimeWindow.LAST_30D) {
                    window = PluginAnalyticsRepository.TimeWindow.LAST_30D
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.BarChart, contentDescription = null)
                        Text("Overview (${window.label})", style = MaterialTheme.typography.titleMedium)
                    }
                    Text("Tracked plugins: ${stats.size}")
                    Spacer(Modifier.height(4.dp))
                    Text("Sorted by CPU peak → RAM peak → Net out", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val filename = "connectias_analytics_${
                                java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                                    .format(java.util.Date())
                            }.canalytics"
                            createDocumentLauncher.launch(filename)
                        }
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null)
                        Spacer(Modifier.padding(4.dp))
                        Text("Create encrypted export (JSON+CSV)")
                    }
                }
            }

            if (stats.isEmpty()) {
                Text("No analytics samples yet. (Collector needs runtime activity.)")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(stats) { s ->
                        Card(
                            onClick = { selected = s },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(s.pluginId, style = MaterialTheme.typography.titleSmall)
                                Text("CPU peak: ${format1(s.peakCpu)}% • RAM peak: ${s.peakMemMB}MB")
                                Text("Net out: ${formatBytes(s.netOutBytes)} • UI: ${s.uiActions} • RL: ${s.rateLimitHits}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeWindowChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val container = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    Card(
        onClick = onClick,
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = container)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

private fun format1(v: Float): String = String.format(java.util.Locale.US, "%.1f", v)

private fun formatBytes(bytes: Long): String {
    val kb = 1024L
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes >= gb -> "${bytes / gb}GB"
        bytes >= mb -> "${bytes / mb}MB"
        bytes >= kb -> "${bytes / kb}KB"
        else -> "${bytes}B"
    }
}

@Composable
private fun ExportPassphraseDialog(
    passphrase: String,
    passphraseConfirm: String,
    onPassphraseChange: (String) -> Unit,
    onPassphraseConfirmChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val passMismatch = passphrase.isNotEmpty() && passphraseConfirm.isNotEmpty() && passphrase != passphraseConfirm
    val passTooShort = passphrase.isNotEmpty() && passphrase.length < 10
    val canConfirm = passphrase.isNotBlank() && !passMismatch && !passTooShort

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Encrypt analytics export") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Choose a passphrase to encrypt the analytics export.")
                androidx.compose.material3.OutlinedTextField(
                    value = passphrase,
                    onValueChange = onPassphraseChange,
                    label = { Text("Passphrase") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                androidx.compose.material3.OutlinedTextField(
                    value = passphraseConfirm,
                    onValueChange = onPassphraseConfirmChange,
                    label = { Text("Confirm passphrase") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (passTooShort) {
                    Text("Passphrase must be at least 10 characters.", color = MaterialTheme.colorScheme.error)
                }
                if (passMismatch) {
                    Text("Passphrases do not match.", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = canConfirm) {
                Text("Export")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

