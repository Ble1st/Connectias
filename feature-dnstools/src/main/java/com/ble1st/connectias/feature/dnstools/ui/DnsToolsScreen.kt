package com.ble1st.connectias.feature.dnstools.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
                PrimaryTabRow(selectedTabIndex = selectedTab) {
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
        if (uiState.dnsLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        DnsLookupSection(uiState, viewModel)
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
            DnsRecordType.entries.forEach { type ->
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
        }
        Spacer(modifier = Modifier.height(8.dp))
        
        uiState.dnsResult?.let { result ->
            ResultBlock(
                title = stringResource(R.string.dns_result_title, result.type),
                content = result.error ?: result.records.joinToString("\n")
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
