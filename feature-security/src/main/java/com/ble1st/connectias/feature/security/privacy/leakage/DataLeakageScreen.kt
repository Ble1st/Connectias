package com.ble1st.connectias.feature.security.privacy.leakage

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.common.ui.strings.LocalAppStrings // Import LocalAppStrings
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DataLeakageScreen(
    state: DataLeakageState,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit,
    onGetApps: () -> Unit,
    onAnalyzeText: (String) -> Unit
) {
    var textToAnalyze by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = LocalAppStrings.current.dataLeakageCheckTitle,
                style = MaterialTheme.typography.headlineMedium
            )
        }

        // Clipboard Monitoring Section
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        LocalAppStrings.current.clipboardMonitorTitle,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (state is DataLeakageState.Monitoring && state.isMonitoring) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(LocalAppStrings.current.monitoringActive, style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onStopMonitoring,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text(LocalAppStrings.current.stopMonitoring)
                        }
                    } else {
                        Button(
                            onClick = onStartMonitoring,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(LocalAppStrings.current.startMonitoring)
                        }
                    }
                }
            }
        }

        // Text Analysis Section
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        LocalAppStrings.current.sensitiveDataAnalysisTitle,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = textToAnalyze,
                        onValueChange = { textToAnalyze = it },
                        label = { Text(LocalAppStrings.current.enterTextToAnalyze) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(
                        onClick = { 
                            if (textToAnalyze.isNotBlank()) {
                                onAnalyzeText(textToAnalyze)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = textToAnalyze.isNotBlank()
                    ) {
                        Text(LocalAppStrings.current.analyzeAction)
                    }
                    
                    if (state is DataLeakageState.SensitivityAnalysis) {
                        Spacer(modifier = Modifier.height(8.dp))
                        SensitivityBadge(state.sensitivity)
                    }
                }
            }
        }

        // Clipboard Access Apps Section
        item {
            Button(
                onClick = onGetApps,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text(LocalAppStrings.current.checkAppsClipboardAccess)
            }
        }

        if (state is DataLeakageState.AppsWithAccess) {
            item {
                Text(
                    text = "${LocalAppStrings.current.appsWithClipboardAccess} ${state.apps.size})", // Placeholder for format string
                    style = MaterialTheme.typography.titleMedium
                )
            }
            items(state.apps) { app ->
                AppAccessItem(app)
            }
        }

        // Clipboard History (if monitoring was active and caught something)
        if (state is DataLeakageState.ClipboardEntryState) {
             item {
                Text(
                    LocalAppStrings.current.latestClipboardEntry,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            item {
                ClipboardEntryItem(state.entry)
            }
        }
        
        if (state is DataLeakageState.Error) {
            item {
                Text(
                    text = "${LocalAppStrings.current.alertError}: ${state.message}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SensitivityBadge(level: SensitivityLevel) {
    val (color, text) = when (level) {
        SensitivityLevel.CRITICAL -> MaterialTheme.colorScheme.error to LocalAppStrings.current.criticalSensitivity
        SensitivityLevel.HIGH -> Color(0xFFFF9800) to LocalAppStrings.current.highSensitivity
        SensitivityLevel.MEDIUM -> Color(0xFFFFC107) to LocalAppStrings.current.mediumSensitivity
        SensitivityLevel.LOW -> Color(0xFF4CAF50) to LocalAppStrings.current.lowSensitivity
        SensitivityLevel.NONE -> Color.Gray to LocalAppStrings.current.noSensitiveDataDetected
    }
    
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.border(1.dp, color, MaterialTheme.shapes.small)
    ) {
        Text(
            text = text,
            color = color,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun ClipboardEntryItem(entry: ClipboardEntry) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(entry.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SensitivityBadge(entry.sensitivity)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = entry.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3
            )
        }
    }
}

@Composable
private fun AppAccessItem(app: AppClipboardAccess) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.ContentPaste, contentDescription = null)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(app.appName, style = MaterialTheme.typography.titleMedium)
                Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                if (app.isSystemApp) {
                    Text(
                        LocalAppStrings.current.systemAppLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
