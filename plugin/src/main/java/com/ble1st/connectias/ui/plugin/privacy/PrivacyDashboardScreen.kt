package com.ble1st.connectias.ui.plugin.privacy

import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.privacy.PrivacyAggregator
import com.ble1st.connectias.privacy.export.EncryptedExportWriter
import com.ble1st.connectias.privacy.export.ExportDeviceInfo
import com.ble1st.connectias.privacy.export.ExportTimeWindow
import com.ble1st.connectias.privacy.export.PrivacyExportBundle
import com.ble1st.connectias.privacy.export.PrivacyExportMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class TimeWindowPreset(val label: String, val durationMillis: Long) {
    LAST_24H("Last 24h", 24L * 60 * 60 * 1000),
    LAST_7D("Last 7d", 7L * 24 * 60 * 60 * 1000),
    LAST_30D("Last 30d", 30L * 24 * 60 * 60 * 1000)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyDashboardScreen(
    privacyAggregator: PrivacyAggregator,
    onNavigateBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var preset by remember { mutableStateOf(TimeWindowPreset.LAST_24H) }
    val now = remember { System.currentTimeMillis() }
    var timeWindow by remember(preset, now) {
        mutableStateOf(
            ExportTimeWindow(
                startEpochMillis = System.currentTimeMillis() - preset.durationMillis,
                endEpochMillis = System.currentTimeMillis()
            )
        )
    }

    var snapshot by remember { mutableStateOf<PrivacyAggregator.PrivacySnapshot?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Export dialog state
    var showExportDialog by remember { mutableStateOf(false) }
    var passphrase by remember { mutableStateOf("") }
    var passphraseConfirm by remember { mutableStateOf("") }
    var exportPendingUri by remember { mutableStateOf<android.net.Uri?>(null) }

    // Plugin drilldown state
    var selectedPluginId by remember { mutableStateOf<String?>(null) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            exportPendingUri = uri
            showExportDialog = true
        }
    }

    LaunchedEffect(preset) {
        timeWindow = ExportTimeWindow(
            startEpochMillis = System.currentTimeMillis() - preset.durationMillis,
            endEpochMillis = System.currentTimeMillis()
        )
    }

    LaunchedEffect(timeWindow) {
        isLoading = true
        snapshot = withContext(Dispatchers.Default) {
            privacyAggregator.getPrivacySnapshot(timeWindow)
        }
        isLoading = false
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
                val currentSnapshot = snapshot ?: return@ExportPassphraseDialog

                val passChars = passphrase.toCharArray()
                val bundle = buildExportBundle(context, timeWindow, currentSnapshot)

                showExportDialog = false
                passphrase = ""
                passphraseConfirm = ""
                exportPendingUri = null

                scope.launch {
                    try {
                        EncryptedExportWriter.writeEncryptedZip(
                            context = context,
                            outputUri = uri,
                            passphrase = passChars,
                            exportBundle = bundle
                        )
                        snackbarHostState.showSnackbar("✅ Export created (encrypted)")
                    } catch (e: Exception) {
                        snackbarHostState.showSnackbar("❌ Export failed: ${e.message ?: "unknown error"}")
                    } finally {
                        // Best-effort cleanup
                        passChars.fill('\u0000')
                    }
                }
            }
        )
    }

    if (selectedPluginId != null && snapshot != null) {
        val pluginId = selectedPluginId!!
        val snap = snapshot!!

        val auditCount = snap.auditEvents.count { it.pluginId == pluginId }
        val permCount = snap.permissionUsage.count { it.pluginId == pluginId }
        val network = snap.networkUsage.firstOrNull { it.pluginId == pluginId }
        val leakageCount = snap.dataLeakageEvents.count { it.pluginId == pluginId }

        AlertDialog(
            onDismissRequest = { selectedPluginId = null },
            title = { Text("Plugin privacy details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Plugin: $pluginId")
                    Text("Audit events: $auditCount")
                    Text("Permission events: $permCount")
                    Text("Network: ${if (network != null) "tracked" else "no data"}")
                    Text("Data leakage events: $leakageCount")
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedPluginId = null }) { Text("Close") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Art. 15 GDPR Right to Access Header
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.PrivacyTip, contentDescription = null)
                        Text("Auskunft nach Art. 15 DSGVO", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        "Hier finden Sie eine Übersicht aller von Connectias gespeicherten personenbezogenen und plugin-bezogenen Daten. " +
                        "Der verschlüsselte Export enthält alle im gewählten Zeitfenster gespeicherten Daten.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        "Hinweis: Die Deinstallation eines Plugins entfernt alle zugehörigen Daten (Art. 17 DSGVO).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TimeWindowChip(
                    label = "24h",
                    selected = preset == TimeWindowPreset.LAST_24H,
                    onClick = { preset = TimeWindowPreset.LAST_24H }
                )
                TimeWindowChip(
                    label = "7d",
                    selected = preset == TimeWindowPreset.LAST_7D,
                    onClick = { preset = TimeWindowPreset.LAST_7D }
                )
                TimeWindowChip(
                    label = "30d",
                    selected = preset == TimeWindowPreset.LAST_30D,
                    onClick = { preset = TimeWindowPreset.LAST_30D }
                )
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.PrivacyTip, contentDescription = null)
                        Text("Overview", style = MaterialTheme.typography.titleMedium)
                    }

                    val summary = snapshot?.let { privacyAggregator.getPrivacySummary(timeWindow) }
                    if (isLoading) {
                        Text("Loading…")
                    } else if (summary != null) {
                        Text("Plugins: ${summary.trackedPluginIds.size}")
                        Text("Audit events: ${summary.auditEventCount}")
                        Text("Permission events: ${summary.permissionEventCount}")
                        Text("Network tracked plugins: ${summary.networkPluginCount}")
                        Text("Data leakage events: ${summary.dataLeakageEventCount}")
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val filename = "connectias_gdpr_export_${
                                    SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                                }.cgdpr"
                                createDocumentLauncher.launch(filename)
                            }
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null)
                            Spacer(Modifier.padding(4.dp))
                            Text("Auskunft exportieren (Art. 15 DSGVO)")
                        }
                    } else {
                        Text("No data available for selected time window.")
                    }
                }
            }

            // Data Categories Overview Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.PrivacyTip, contentDescription = null)
                        Text("Datenkategorien", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        "Übersicht der von Connectias gespeicherten Datenkategorien:",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(Modifier.height(4.dp))

                    // Audit Events Category
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Audit-Ereignisse", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Sicherheitsrelevante Ereignisse wie blockierte Reflexionsversuche, Rate-Limiting, Sandbox-Fehler",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Text(
                            "Quelle: SecurityAuditManager • Speicherdauer: Zeitfenster-abhängig (Retention-Policy)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }

                    // Permission Usage Category
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Berechtigungsnutzung", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Aufzeichnung der Plugin-Berechtigungszugriffe (Kamera, Netzwerk, etc.)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Text(
                            "Quelle: PluginPermissionMonitor • Speicherdauer: Zeitfenster-abhängig",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }

                    // Network Usage Category
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Netzwerk-Nutzung", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Überwachung des Netzwerk-Traffics pro Plugin (Bandbreite, Bytes In/Out)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Text(
                            "Quelle: EnhancedPluginNetworkPolicy • Speicherdauer: Zeitfenster-abhängig",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }

                    // Data Leakage Category
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Data-Leakage-Ereignisse", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Erkannte Versuche des unbefugten Datenzugriffs oder Datenexfiltration",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Text(
                            "Quelle: SecurityAuditManager • Speicherdauer: Zeitfenster-abhängig",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Security, contentDescription = null)
                        Text("Plugins (IDs)", style = MaterialTheme.typography.titleMedium)
                    }

                    val pluginIds = snapshot?.let {
                        buildSet {
                            it.auditEvents.mapNotNullTo(this) { ev -> ev.pluginId }
                            it.permissionUsage.mapTo(this) { ev -> ev.pluginId }
                            it.networkUsage.mapTo(this) { ev -> ev.pluginId }
                            it.dataLeakageEvents.mapTo(this) { ev -> ev.pluginId }
                        }.toList().sorted()
                    }.orEmpty()

                    if (pluginIds.isEmpty()) {
                        Text("No plugin activity in selected window.")
                    } else {
                        LazyColumn {
                            items(pluginIds) { id ->
                                TextButton(onClick = { selectedPluginId = id }) {
                                    Text(text = id, style = MaterialTheme.typography.bodyMedium)
                                }
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
        modifier = Modifier,
        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = container)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge
        )
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
        title = { Text("Encrypt export") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Choose a passphrase to encrypt the export. Keep it safe; it is required to decrypt.")
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = onPassphraseChange,
                    label = { Text("Passphrase") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = passphraseConfirm,
                    onValueChange = onPassphraseConfirmChange,
                    label = { Text("Confirm passphrase") },
                    visualTransformation = PasswordVisualTransformation(),
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
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun buildExportBundle(
    context: Context,
    timeWindow: ExportTimeWindow,
    snapshot: PrivacyAggregator.PrivacySnapshot
): PrivacyExportBundle {
    val versionName = runCatching {
        val pkg = context.packageName
        val pi = context.packageManager.getPackageInfo(pkg, 0)
        pi.versionName
    }.getOrNull()

    val deviceInfo = ExportDeviceInfo(
        manufacturer = Build.MANUFACTURER,
        model = Build.MODEL,
        androidVersion = Build.VERSION.RELEASE,
        securityPatchLevel = Build.VERSION.SECURITY_PATCH
    )

    return PrivacyExportBundle(
        metadata = PrivacyExportMetadata(
            createdAtEpochMillis = System.currentTimeMillis(),
            timeWindow = timeWindow,
            appVersionName = versionName,
            deviceInfo = deviceInfo
        ),
        auditEvents = snapshot.auditEvents,
        permissionUsage = snapshot.permissionUsage,
        networkUsage = snapshot.networkUsage,
        dataLeakageEvents = snapshot.dataLeakageEvents
    )
}

