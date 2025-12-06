package com.ble1st.connectias.feature.network.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.common.ui.strings.getThemedString
import com.ble1st.connectias.feature.network.R
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DhcpLeaseScreen(
    state: DhcpLeaseState,
    onAnalyzeLeases: () -> Unit,
    onResetState: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = getThemedString(stringResource(R.string.dhcp_lease_viewer_title)),
            style = MaterialTheme.typography.headlineMedium
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = getThemedString(stringResource(R.string.dhcp_lease_info)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        when (state) {
            is DhcpLeaseState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is DhcpLeaseState.Success -> {
                if (state.reservedIps.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = getThemedString(stringResource(R.string.reserved_ips, state.reservedIps.size)),
                                style = MaterialTheme.typography.titleMedium
                            )
                            state.reservedIps.forEach { ip ->
                                Text(
                                    text = "  • $ip",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                Text(
                    text = getThemedString(stringResource(R.string.dhcp_leases, state.leases.size)),
                    style = MaterialTheme.typography.titleLarge
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = state.leases,
                        key = { "${it.ipAddress}_${it.macAddress ?: ""}" }
                    ) { lease ->
                        DhcpLeaseCard(lease)
                    }
                }
            }
            is DhcpLeaseState.Error -> {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = getThemedString(stringResource(R.string.error_prefix, state.message)),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = onResetState,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(getThemedString(stringResource(R.string.reset)))
                            }
                            Button(
                                onClick = onAnalyzeLeases,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(getThemedString(stringResource(R.string.hypervisor_retry)))
                            }
                        }
                    }
                }
            }
            is DhcpLeaseState.Idle -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = getThemedString(stringResource(R.string.dhcp_lease_analysis_requires_devices)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = getThemedString(stringResource(R.string.use_network_dashboard_first)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onAnalyzeLeases,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(getThemedString(stringResource(R.string.analyze_leases)))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DhcpLeaseCard(lease: com.ble1st.connectias.feature.network.models.DhcpLease) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = lease.hostname,
                    style = MaterialTheme.typography.titleMedium
                )
                if (lease.isStatic) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "Static",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            InfoRow("IP Address", lease.ipAddress)
            lease.macAddress?.let { InfoRow("MAC Address", it) }
            lease.leaseStartTime?.let {
                InfoRow("Lease Start", formatTimestamp(it))
            }
            lease.leaseExpiryTime?.let {
                InfoRow("Lease Expiry", formatTimestamp(it))
            }
            InfoRow("Last Seen", formatTimestamp(lease.lastSeen))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
