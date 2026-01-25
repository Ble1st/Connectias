package com.ble1st.connectias.ui.plugin.security

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
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ble1st.connectias.plugin.security.PluginBehaviorAnalyzer
import com.ble1st.connectias.plugin.security.SecurityAuditManager
import kotlinx.coroutines.flow.scan

/**
 * Central Security Dashboard UI (all plugins).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityDashboardScreen(
    auditManager: SecurityAuditManager,
    behaviorAnalyzer: PluginBehaviorAnalyzer,
    onNavigateBack: () -> Unit
) {
    val stats by auditManager.securityStats.collectAsStateWithLifecycle(
        initialValue = SecurityAuditManager.SecurityStatistics()
    )

    val recentEvents by auditManager.recentEvents.collectAsStateWithLifecycle(initialValue = emptyList())

    val anomaliesFlow = remember {
        behaviorAnalyzer.anomalies
            .scan(emptyList<PluginBehaviorAnalyzer.Anomaly>()) { acc, anomaly ->
                (acc + anomaly).takeLast(50)
            }
    }
    val anomalies by anomaliesFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val pluginIds = remember(recentEvents) {
        recentEvents.mapNotNull { it.pluginId }.distinct().sorted()
    }

    var selectedPluginId by remember { mutableStateOf<String?>(null) }

    if (selectedPluginId != null) {
        val pluginId = selectedPluginId!!
        val summary = auditManager.getPluginSecuritySummary(pluginId)

        AlertDialog(
            onDismissRequest = { selectedPluginId = null },
            title = { Text("Plugin security summary") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Plugin: ${summary.pluginId}")
                    Text("Risk: ${summary.riskLevel}")
                    Text("Total violations: ${summary.totalViolations}")
                    Text("Critical: ${summary.criticalViolations} | High: ${summary.highViolations}")
                    Text("Network violations: ${summary.networkViolations}")
                    Text("Resource violations: ${summary.resourceViolations}")
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedPluginId = null }) { Text("Close") }
            },
            dismissButton = {}
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = androidx.compose.ui.Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(modifier = androidx.compose.ui.Modifier.fillMaxWidth()) {
                    Column(modifier = androidx.compose.ui.Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Security, contentDescription = null)
                            Text("Overview", style = MaterialTheme.typography.titleMedium)
                        }
                        Text("Total events: ${stats.totalEvents}")
                        Text("Critical: ${stats.criticalEvents} | High: ${stats.highSeverityEvents}")
                        Text("Plugin violations: ${stats.pluginViolations}")
                        Text("Network violations: ${stats.networkViolations}")
                        Text("Resource violations: ${stats.resourceViolations}")
                    }
                }
            }

            item {
                Text("Plugins", style = MaterialTheme.typography.titleMedium)
            }

            if (pluginIds.isEmpty()) {
                item { Text("No plugin security events recorded yet.") }
            } else {
                items(pluginIds) { pluginId ->
                    val summary = auditManager.getPluginSecuritySummary(pluginId)
                    Card(
                        onClick = { selectedPluginId = pluginId },
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = androidx.compose.ui.Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(pluginId, style = MaterialTheme.typography.titleSmall)
                            Text("Risk: ${summary.riskLevel} • Violations: ${summary.totalViolations}")
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(4.dp)) }

            item { Text("Recent security events", style = MaterialTheme.typography.titleMedium) }

            items(recentEvents.takeLast(20).reversed()) { ev ->
                Card(modifier = androidx.compose.ui.Modifier.fillMaxWidth()) {
                    Column(modifier = androidx.compose.ui.Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${ev.severity} • ${ev.eventType}", style = MaterialTheme.typography.labelLarge)
                        Text(ev.message, style = MaterialTheme.typography.bodyMedium)
                        if (ev.pluginId != null) {
                            Text("Plugin: ${ev.pluginId}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(4.dp)) }

            item { Text("Behavior anomalies", style = MaterialTheme.typography.titleMedium) }

            if (anomalies.isEmpty()) {
                item { Text("No anomalies detected.") }
            } else {
                items(anomalies.takeLast(20).reversed()) { a ->
                    Card(modifier = androidx.compose.ui.Modifier.fillMaxWidth()) {
                        Column(modifier = androidx.compose.ui.Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("${a.severity} • ${a.type}", style = MaterialTheme.typography.labelLarge)
                            Text(a.description, style = MaterialTheme.typography.bodyMedium)
                            Text("Plugin: ${a.pluginId}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

