package com.ble1st.connectias.ui.plugin.declruns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeclarativeFlowRunViewerScreen(
    viewModel: DeclarativeFlowRunViewModel,
    onNavigateBack: () -> Unit
) {
    val events by viewModel.events.collectAsStateWithLifecycleCompat()
    val pluginIds by viewModel.pluginIds.collectAsStateWithLifecycleCompat()
    val stats by viewModel.stats.collectAsStateWithLifecycleCompat()

    var selectedPluginId by remember { mutableStateOf<String?>(null) } // null = all
    var showOnlyFailed by remember { mutableStateOf(false) }
    var showOnlyRateLimited by remember { mutableStateOf(false) }

    val filtered = remember(events, selectedPluginId, showOnlyFailed, showOnlyRateLimited) {
        events.filter { ev ->
            val pluginOk = selectedPluginId?.let { ev.pluginId == it } ?: true
            val typeOk = when (ev) {
                is DeclarativeRunEvent.FlowRun -> !showOnlyRateLimited
                is DeclarativeRunEvent.AuditEvent -> !showOnlyFailed
            }
            val failOk = if (showOnlyFailed) {
                (ev as? DeclarativeRunEvent.FlowRun)?.ok == false
            } else true
            val rateOk = if (showOnlyRateLimited) {
                (ev as? DeclarativeRunEvent.AuditEvent)?.type == "rate_limited"
            } else true
            pluginOk && typeOk && failOk && rateOk
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Declarative Flow Runs") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* DB is reactive; refresh is a no-op but kept for UX */ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Übersicht", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        StatPill("OK", stats.ok)
                        StatPill("Failed", stats.failed)
                        StatPill("RateLimited", stats.rateLimited)
                        StatPill("Total", stats.total)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text("Plugin", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        FilterChip(
                            selected = selectedPluginId == null,
                            onClick = { selectedPluginId = null },
                            label = { Text("Alle") }
                        )
                        pluginIds.take(4).forEach { pid ->
                            FilterChip(
                                selected = selectedPluginId == pid,
                                onClick = { selectedPluginId = if (selectedPluginId == pid) null else pid },
                                label = { Text(pid) }
                            )
                        }
                    }
                    if (pluginIds.size > 4) {
                        Text(
                            "Weitere Plugins: ${pluginIds.drop(4).joinToString()}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.FilterAlt, contentDescription = null)
                        FilterChip(
                            selected = showOnlyFailed,
                            onClick = { showOnlyFailed = !showOnlyFailed },
                            label = { Text("nur Failed") }
                        )
                        FilterChip(
                            selected = showOnlyRateLimited,
                            onClick = { showOnlyRateLimited = !showOnlyRateLimited },
                            label = { Text("nur RateLimited") }
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Keine passenden Flow-Runs gefunden.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered) { ev ->
                        when (ev) {
                            is DeclarativeRunEvent.FlowRun -> FlowRunCard(ev)
                            is DeclarativeRunEvent.AuditEvent -> AuditCard(ev)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(label: String, value: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value.toString(), fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun FlowRunCard(run: DeclarativeRunEvent.FlowRun) {
    val df = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    val time = df.format(Date(run.timestamp))
    val color = if (run.ok) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer

    Card(colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.6f))) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(run.pluginId, fontWeight = FontWeight.SemiBold)
                Text(time, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "flowId=${run.flowId}  trigger=${run.triggerType}  ok=${run.ok}",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "steps=${run.steps}  durationMs=${run.durationMs}  error=${run.error ?: "null"}",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun AuditCard(audit: DeclarativeRunEvent.AuditEvent) {
    val df = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
    val time = df.format(Date(audit.timestamp))
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(audit.pluginId, fontWeight = FontWeight.SemiBold)
                Text(time, style = MaterialTheme.typography.bodySmall)
            }
            Text("[AUDIT] ${audit.type}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            if (audit.details.isNotEmpty()) {
                Text(
                    audit.details.entries.joinToString(" ") { "${it.key}=${it.value}" },
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/**
 * Small compat helper to avoid adding lifecycle compose dependency assumptions in this file.
 */
@Composable
private fun <T> StateFlow<T>.collectAsStateWithLifecycleCompat() =
    this.collectAsStateWithLifecycle()

