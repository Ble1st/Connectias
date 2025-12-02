package com.ble1st.connectias.feature.security.wifi

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ble1st.connectias.feature.security.wifi.models.EncryptionStrength
import com.ble1st.connectias.feature.security.wifi.models.RiskLevel
import com.ble1st.connectias.feature.security.wifi.models.WifiSecurityReport
import com.ble1st.connectias.feature.security.wifi.models.WifiVulnerability
import kotlinx.coroutines.launch

/**
 * WiFi Security Auditor screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiSecurityAuditorScreen(
    viewModel: WifiSecurityAuditorViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            scope.launch {
                snackbarHostState.showSnackbar(error)
                viewModel.clearError()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WiFi Security Audit") },
                actions = {
                    IconButton(
                        onClick = { viewModel.runFullScan() },
                        enabled = !uiState.isScanning
                    ) {
                        if (uiState.isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Current network card
            item {
                if (uiState.currentReport != null) {
                    NetworkSecurityCard(report = uiState.currentReport!!)
                } else if (!uiState.isAuditing) {
                    NoNetworkCard()
                }
            }

            // Encryption strength
            uiState.encryptionAssessment?.let { assessment ->
                item {
                    EncryptionCard(
                        strength = assessment.strength,
                        weaknesses = assessment.weaknesses
                    )
                }
            }

            // Vulnerabilities
            uiState.currentReport?.vulnerabilities?.let { vulnerabilities ->
                if (vulnerabilities.isNotEmpty()) {
                    item {
                        Text(
                            text = "Vulnerabilities (${vulnerabilities.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(vulnerabilities) { vulnerability ->
                        VulnerabilityCard(vulnerability = vulnerability)
                    }
                }
            }

            // Suspicious APs
            if (uiState.suspiciousAPs.isNotEmpty()) {
                item {
                    Text(
                        text = "Suspicious Access Points (${uiState.suspiciousAPs.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                items(uiState.suspiciousAPs) { ap ->
                    SuspiciousAPCard(
                        ssid = ap.ssid,
                        bssid = ap.bssid,
                        reason = ap.reason.name.replace("_", " "),
                        confidence = ap.confidence
                    )
                }
            }

            // Recommendations
            uiState.currentReport?.recommendations?.let { recommendations ->
                if (recommendations.isNotEmpty()) {
                    item {
                        RecommendationsCard(recommendations = recommendations)
                    }
                }
            }

            // Scan button
            if (uiState.currentReport == null && !uiState.isAuditing) {
                item {
                    Button(
                        onClick = { viewModel.runFullScan() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Run Security Scan")
                    }
                }
            }
        }
    }
}

@Composable
private fun NetworkSecurityCard(report: WifiSecurityReport) {
    val riskColor = when (report.overallRisk) {
        RiskLevel.CRITICAL -> Color(0xFFD32F2F)
        RiskLevel.HIGH -> Color(0xFFE64A19)
        RiskLevel.MEDIUM -> Color(0xFFF57C00)
        RiskLevel.LOW -> Color(0xFF4CAF50)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = riskColor.copy(alpha = 0.1f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(riskColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Wifi,
                        contentDescription = null,
                        tint = riskColor
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = report.ssid,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${report.securityType.name} • ${report.overallRisk.name} Risk",
                        style = MaterialTheme.typography.bodyMedium,
                        color = riskColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                InfoChip(label = "Signal", value = "${report.signalStrength} dBm")
                InfoChip(label = "Channel", value = report.channel.toString())
                InfoChip(label = "Encryption", value = report.encryptionStrength.name)
            }
        }
    }
}

@Composable
private fun NoNetworkCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Wifi,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Not connected to WiFi",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EncryptionCard(
    strength: EncryptionStrength,
    weaknesses: List<String>
) {
    val color = when (strength) {
        EncryptionStrength.VERY_STRONG -> Color(0xFF4CAF50)
        EncryptionStrength.STRONG -> Color(0xFF8BC34A)
        EncryptionStrength.MODERATE -> Color(0xFFF57C00)
        EncryptionStrength.WEAK -> Color(0xFFE64A19)
        EncryptionStrength.NONE -> Color(0xFFD32F2F)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = null,
                    tint = color
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Encryption: ${strength.name.replace("_", " ")}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (weaknesses.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                weaknesses.forEach { weakness ->
                    Row(modifier = Modifier.padding(vertical = 4.dp)) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = weakness,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VulnerabilityCard(vulnerability: WifiVulnerability) {
    val severityColor = when (vulnerability.severity) {
        RiskLevel.CRITICAL -> Color(0xFFD32F2F)
        RiskLevel.HIGH -> Color(0xFFE64A19)
        RiskLevel.MEDIUM -> Color(0xFFF57C00)
        RiskLevel.LOW -> Color(0xFFFBC02D)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = severityColor.copy(alpha = 0.1f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Error,
                    contentDescription = null,
                    tint = severityColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = vulnerability.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = vulnerability.severity.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = severityColor
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = vulnerability.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            vulnerability.mitigation?.let { mitigation ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Fix: $mitigation",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun SuspiciousAPCard(
    ssid: String,
    bssid: String,
    reason: String,
    confidence: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = ssid,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = bssid,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$reason (${(confidence * 100).toInt()}% confidence)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun RecommendationsCard(recommendations: List<String>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Recommendations",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            recommendations.forEach { recommendation ->
                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text("•", color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = recommendation,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
