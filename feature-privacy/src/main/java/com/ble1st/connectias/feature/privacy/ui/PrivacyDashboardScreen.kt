package com.ble1st.connectias.feature.privacy.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.feature.privacy.models.*

@Composable
fun PrivacyDashboardScreen(
    state: PrivacyDashboardState,
    onRefresh: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (state) {
            is PrivacyDashboardState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            is PrivacyDashboardState.Error -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Error: ${state.message}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Button(onClick = onRefresh, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Retry")
                    }
                }
            }
            is PrivacyDashboardState.Success -> {
                PrivacyDashboardContent(
                    uiState = state.data,
                    onRefresh = onRefresh
                )
            }
        }
    }
}

@Composable
private fun PrivacyDashboardContent(
    uiState: UiState,
    onRefresh: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Text(
                text = "Privacy Dashboard",
                style = MaterialTheme.typography.headlineMedium
            )
        }

        item {
            OverallStatusCard(uiState.overallStatus)
        }

        item {
            Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                Text("Refresh Privacy Status")
            }
        }

        item {
            PrivacyDetailsSection(uiState)
        }
        
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun OverallStatusCard(status: PrivacyStatus) {
    val (color, icon, text) = when (status.overallLevel) {
        PrivacyLevel.SECURE -> Triple(MaterialTheme.colorScheme.primaryContainer, Icons.Default.CheckCircle, "Secure")
        PrivacyLevel.WARNING -> Triple(MaterialTheme.colorScheme.tertiaryContainer, Icons.Default.Warning, "Warning")
        PrivacyLevel.CRITICAL -> Triple(MaterialTheme.colorScheme.errorContainer, Icons.Default.Error, "Critical")
        PrivacyLevel.UNKNOWN -> Triple(MaterialTheme.colorScheme.surfaceVariant, Icons.Default.Help, "Unknown")
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = color),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("Overall Status", style = MaterialTheme.typography.labelLarge)
                Text(text, style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

@Composable
private fun PrivacyDetailsSection(uiState: UiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Privacy Overview", style = MaterialTheme.typography.titleMedium)
            
            PrivacyCategory("Network", uiState.overallStatus.networkPrivacy) {
                InfoRow("DNS", uiState.networkPrivacy.dnsStatus.toString())
                InfoRow("VPN", if (uiState.networkPrivacy.vpnActive) "Active" else "Inactive")
                InfoRow("Type", uiState.networkPrivacy.networkType.toString())
            }
            
            PrivacyCategory("Sensor", uiState.overallStatus.sensorPrivacy) {
                InfoRow("Apps with Access", uiState.sensorPrivacy.totalAppsWithSensorAccess.toString())
            }

            PrivacyCategory("Location", uiState.overallStatus.locationPrivacy) {
                InfoRow("Mock Location", if (uiState.locationPrivacy.mockLocationEnabled) "Enabled" else "Disabled")
                InfoRow("Location Services", if (uiState.locationPrivacy.locationServicesEnabled) "Enabled" else "Disabled")
                InfoRow("Apps with Access", uiState.locationPrivacy.appsWithLocationAccess.size.toString())
            }

            PrivacyCategory("Permissions", uiState.overallStatus.permissionsPrivacy) {
                InfoRow("Total Apps", uiState.appPermissions.size.toString())
                val highRisk = uiState.appPermissions.count { it.riskLevel == PermissionRiskLevel.HIGH }
                InfoRow("High Risk Apps", highRisk.toString())
            }

            PrivacyCategory("Background", uiState.overallStatus.backgroundPrivacy) {
                InfoRow("Running Services", uiState.backgroundActivity.totalRunningServices.toString())
                InfoRow("Ignoring Battery Opt", uiState.backgroundActivity.appsIgnoringBatteryOptimization.size.toString())
            }

            PrivacyCategory("Storage", uiState.overallStatus.storagePrivacy) {
                InfoRow("Scoped Storage", if (uiState.storagePrivacy.scopedStorageEnabled) "Enabled" else "Disabled")
                InfoRow("Legacy Mode", if (uiState.storagePrivacy.legacyStorageMode) "Enabled" else "Disabled")
                InfoRow("Apps with Access", uiState.storagePrivacy.appsWithStorageAccess.size.toString())
            }
        }
    }
}

@Composable
private fun PrivacyCategory(
    title: String,
    level: PrivacyLevel,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            PrivacyLevelBadge(level)
        }
        Spacer(modifier = Modifier.height(4.dp))
        content()
        Divider(modifier = Modifier.padding(vertical = 8.dp))
    }
}

@Composable
private fun PrivacyLevelBadge(level: PrivacyLevel) {
    val color = when (level) {
        PrivacyLevel.SECURE -> Color.Green // Or appropriate theme color
        PrivacyLevel.WARNING -> Color.Yellow
        PrivacyLevel.CRITICAL -> Color.Red
        PrivacyLevel.UNKNOWN -> Color.Gray
    }
    // Simple text representation for now, could be a proper badge
    Text(
        text = level.name,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = "  - $label", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ToolButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(12.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text)
    }
}
