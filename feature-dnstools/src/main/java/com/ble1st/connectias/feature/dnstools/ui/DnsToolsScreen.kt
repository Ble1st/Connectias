package com.ble1st.connectias.feature.dnstools.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.feature.dnstools.R
import kotlin.math.roundToInt

@Composable
fun DnsToolsScreen(
    viewModel: DnsToolsViewModel
) {
    val uiState by viewModel.state.collectAsState()

    ConnectiasTheme {
        Scaffold { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.dns_tools_title),
                    style = MaterialTheme.typography.headlineSmall
                )

                if (uiState.errorMessage != null) {
                    Text(
                        text = uiState.errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                DnsLookupSection(uiState, viewModel)
                SubnetPingSection(uiState, viewModel)
                CaptivePortalSection(uiState, viewModel)
                MarkdownSection(uiState, viewModel)
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
            value = uiState.domainInput,
            onValueChange = viewModel::onDomainChanged,
            label = { Text(stringResource(R.string.dns_domain_label)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DnsRecordType.values().forEach { type ->
                FilterChip(
                    selected = uiState.recordType == type,
                    onClick = { viewModel.onRecordTypeChanged(type) },
                    label = { Text(type.label) }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = viewModel::resolveDns, enabled = !uiState.isLoading) {
                Text(stringResource(R.string.dns_lookup))
            }
            Button(onClick = viewModel::fetchDmarc, enabled = !uiState.isLoading) {
                Text(stringResource(R.string.dns_dmarc))
            }
            Button(onClick = viewModel::fetchWhois, enabled = !uiState.isLoading) {
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
        uiState.dmarcResult?.let { result ->
            ResultBlock(
                title = stringResource(R.string.dmarc_result_title),
                content = if (result.error != null) result.error else result.records.joinToString("\n")
            )
        }
        uiState.whoisResult?.let { result ->
            ResultBlock(
                title = stringResource(R.string.whois_result_title),
                content = result.error ?: result.raw
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
            value = uiState.subnetInput,
            onValueChange = viewModel::onSubnetInputChanged,
            label = { Text(stringResource(R.string.subnet_label)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = viewModel::calculateSubnet, enabled = !uiState.isLoading) {
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
        Button(onClick = viewModel::pingHost, enabled = !uiState.isLoading) {
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
        Button(onClick = viewModel::detectCaptivePortal, enabled = !uiState.isLoading) {
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
private fun MarkdownSection(
    uiState: DnsToolsUiState,
    viewModel: DnsToolsViewModel
) {
    SectionCard(title = stringResource(R.string.markdown_title)) {
        OutlinedTextField(
            value = uiState.markdownDocument.name ?: "",
            onValueChange = viewModel::onMarkdownNameChanged,
            label = { Text(stringResource(R.string.markdown_name)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.markdownDocument.content,
            onValueChange = viewModel::onMarkdownChanged,
            label = { Text(stringResource(R.string.markdown_content)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        )
        Text(
            text = stringResource(R.string.markdown_hint),
            style = MaterialTheme.typography.bodySmall
        )
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, style = MaterialTheme.typography.labelLarge)
        Text(
            text = content,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun toggleLabel(enabled: Boolean, label: String): String {
    return if (enabled) "[x] $label" else "[ ] $label"
}
