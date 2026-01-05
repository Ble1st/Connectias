package com.ble1st.connectias.feature.ntp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.feature.ntp.R
import com.ble1st.connectias.feature.ntp.data.NtpHistoryEntity
import com.ble1st.connectias.feature.ntp.data.NtpResult
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NtpScreen(
    viewModel: NtpViewModel
) {
    val uiState by viewModel.state.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Check", "History")

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
                            text = { Text(title) },
                            icon = {
                                Icon(
                                    if (index == 0) Icons.Default.Schedule else Icons.Default.History,
                                    contentDescription = null
                                )
                            }
                        )
                    }
                }

                when (selectedTab) {
                    0 -> CheckSection(uiState, viewModel)
                    1 -> HistorySection(uiState.history, viewModel)
                }
            }
        }
    }
}

@Composable
private fun CheckSection(
    uiState: NtpUiState,
    viewModel: NtpViewModel
) {
    val commonServers = listOf("pool.ntp.org", "time.google.com", "time.windows.com", "time.apple.com")
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = stringResource(R.string.ntp_title), style = MaterialTheme.typography.headlineSmall)
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = uiState.server,
                    onValueChange = viewModel::updateServer,
                    label = { Text(stringResource(R.string.ntp_server_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Server Suggestions
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    commonServers.take(3).forEach { server ->
                        SuggestionChip(
                            onClick = { viewModel.updateServer(server) },
                            label = { Text(server) }
                        )
                    }
                }

                Button(
                    onClick = viewModel::checkNtp,
                    enabled = !uiState.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.ntp_check_action))
                }
            }
        }

        uiState.result?.let { result ->
            ResultCard(result)
        }
        
        uiState.errorMessage?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ResultCard(result: NtpResult) {
    if (result.error != null) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = "Result for ${result.server}", style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()
            ResultRow("Offset", "${result.offsetMs} ms")
            ResultRow("Delay", "${result.delayMs} ms")
            ResultRow("Stratum", "${result.stratum}")
            ResultRow("Reference ID", result.referenceId)
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun HistorySection(
    history: List<NtpHistoryEntity>,
    viewModel: NtpViewModel
) {
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM, Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Recent Checks", style = MaterialTheme.typography.titleMedium)
            if (history.isNotEmpty()) {
                TextButton(onClick = viewModel::clearHistory) {
                    Text("Clear All")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))

        if (history.isEmpty()) {
            Text("No history yet.", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(history) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = item.server, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Offset: ${item.offsetMs}ms | Stratum: ${item.stratum}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = dateFormat.format(Date(item.timestamp)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { viewModel.deleteHistoryItem(item) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

