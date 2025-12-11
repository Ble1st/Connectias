package com.ble1st.connectias.feature.dnstools.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.feature.dnstools.R
import com.ble1st.connectias.feature.dnstools.data.DnsHistoryEntity
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DnsToolsScreen(
    viewModel: DnsToolsViewModel
) {
    val uiState by viewModel.state.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Tools", "History")

    ConnectiasTheme {
        Scaffold { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }

                when (selectedTab) {
                    0 -> ToolsContent(uiState, viewModel)
                    1 -> HistoryContent(
                        history = uiState.history,
                        onClearAll = viewModel::clearHistory,
                        onDelete = viewModel::deleteHistoryItem
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolsContent(
    uiState: DnsToolsUiState,
    viewModel: DnsToolsViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (uiState.dnsLoading || uiState.subnetLoading || uiState.pingLoading || uiState.captivePortalLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        DnsLookupSection(uiState, viewModel)
        SubnetPingSection(uiState, viewModel)
        CaptivePortalSection(uiState, viewModel)
    }
}

@Composable
private fun HistoryContent(
    history: List<DnsHistoryEntity>,
    onClearAll: () -> Unit,
    onDelete: (DnsHistoryEntity) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Recent Queries", style = MaterialTheme.typography.titleMedium)
            if (history.isNotEmpty()) {
                TextButton(onClick = onClearAll) {
                    Text("Clear All")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (history.isEmpty()) {
            Text("No history yet.", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(history) { item ->
                    HistoryItem(item, onDelete)
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(
    item: DnsHistoryEntity,
    onDelete: (DnsHistoryEntity) -> Unit
) {
    val context = LocalContext.current
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault()) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${item.toolType.name}: ${item.query}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = dateFormat.format(Date(item.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onDelete(item) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(text = item.resultSummary, style = MaterialTheme.typography.bodySmall, maxLines = 3)
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Button(
                onClick = { copyToClipboard(context, item.fullResult) },
                modifier = Modifier.align(Alignment.End).height(32.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy Result", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun DnsLookupSection(
    uiState: DnsToolsUiState,
    viewModel: DnsToolsViewModel
) {
    SectionCard(title = stringResource(R.string.dns_section_title)) {
        OutlinedTextField(
            value = uiState.dnsQuery,
            onValueChange = viewModel::onDnsQueryChanged,
            label = { Text(stringResource(R.string.dns_domain_label)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        // Record Type Selection
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DnsRecordType.values().forEach { type ->
                FilterChip(
                    selected = uiState.selectedRecordType == type,
                    onClick = { viewModel.onRecordTypeSelected(type) },
                    label = { Text(type.label) }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = viewModel::resolveDns) {
                Text(stringResource(R.string.dns_lookup))
            }
            Button(onClick = viewModel::fetchDmarc) {
                Text("DMARC")
            }
            Button(onClick = viewModel::fetchWhois) {
                Text(stringResource(R.string.dns_whois))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        
        uiState.dnsResult?.let { result ->
            ResultBlock(
                title = stringResource(R.string.dns_result_title, result.type),
                content = if (result.error != null) result.error else result.records.joinToString("\n")
            )
        }
    }
}

@Composable
private fun SubnetPingSection(
    uiState: DnsToolsUiState,
    viewModel: DnsToolsViewModel
) {
    SectionCard(title = stringResource(R.string.net_section_title)) {
        OutlinedTextField(
            value = uiState.subnetCidr,
            onValueChange = viewModel::onSubnetCidrChanged,
            label = { Text(stringResource(R.string.subnet_label)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = viewModel::calculateSubnet) {
            Text(stringResource(R.string.subnet_calculate))
        }
        uiState.subnetResult?.let { result ->
            ResultBlock(
                title = stringResource(R.string.subnet_result_title),
                content = result.error ?: listOfNotNull(
                    result.networkAddress?.let { "Network: $it" },
                    result.broadcastAddress?.let { "Broadcast: $it" },
                    result.firstHost?.let { "First host: $it" },
                    result.lastHost?.let { "Last host: $it" },
                    result.usableHosts?.let { "Usable hosts: $it" }
                ).joinToString("\n")
            )
        }
        Divider(modifier = Modifier.padding(vertical = 12.dp))
        OutlinedTextField(
            value = uiState.pingHost,
            onValueChange = viewModel::onPingHostChanged,
            label = { Text(stringResource(R.string.ping_label)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = viewModel::pingHost) {
            Text(stringResource(R.string.ping_action))
        }
        uiState.pingResult?.let { result ->
            val content = if (result.error != null) {
                result.error
            } else {
                if (result.success) {
                    "Success (${result.latencyMs} ms)"
                } else {
                    "Unreachable"
                }
            }
            ResultBlock(title = stringResource(R.string.ping_result_title), content = content ?: "")
        }
    }
}

@Composable
private fun CaptivePortalSection(
    uiState: DnsToolsUiState,
    viewModel: DnsToolsViewModel
) {
    SectionCard(title = stringResource(R.string.captive_title)) {
        Button(onClick = viewModel::checkCaptivePortal) {
            Text(stringResource(R.string.captive_check))
        }
        uiState.captivePortalResult?.let { result ->
            val statusText = when (result.status) {
                com.ble1st.connectias.feature.dnstools.data.CaptivePortalStatus.OPEN -> stringResource(R.string.captive_status_open)
                com.ble1st.connectias.feature.dnstools.data.CaptivePortalStatus.CAPTIVE -> stringResource(R.string.captive_status_captive, result.httpCode ?: 0)
                com.ble1st.connectias.feature.dnstools.data.CaptivePortalStatus.UNKNOWN -> stringResource(R.string.captive_status_unknown)
            }
            ResultBlock(
                title = stringResource(R.string.captive_result_title),
                content = result.error ?: statusText
            )
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun ResultBlock(
    title: String,
    content: String
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = title, style = MaterialTheme.typography.labelLarge)
            IconButton(onClick = { copyToClipboard(context, content) }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = MaterialTheme.colorScheme.primary)
            }
        }
        Text(
            text = content,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.background(MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.small).padding(8.dp).fillMaxWidth()
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Result", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}
