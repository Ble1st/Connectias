package com.ble1st.connectias.ui.plugin.security

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.plugin.security.*
import kotlinx.coroutines.flow.*

/**
 * Security Dashboard UI for monitoring plugin security status
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginSecurityDashboard(
    pluginId: String,
    modifier: Modifier = Modifier,
    zeroTrustVerifier: ZeroTrustVerifier,
    resourceLimiter: PluginResourceLimiter,
    behaviorAnalyzer: PluginBehaviorAnalyzer
) {
    var securityStatus by remember { mutableStateOf<SecurityStatus>(SecurityStatus.Loading) }
    val anomalies by behaviorAnalyzer.anomalies
        .map { it }
        .scan(emptyList<PluginBehaviorAnalyzer.Anomaly>()) { acc, anomaly ->
            if (anomaly.pluginId == pluginId) (acc + anomaly).takeLast(50) else acc
        }
        .collectAsState(initial = emptyList())
    val resourceUsage by resourceLimiter.resourceUsage
        .map { it[pluginId] }
        .collectAsState(initial = null)
    
    LaunchedEffect(pluginId) {
        val result = zeroTrustVerifier.verifyOnExecution(pluginId)
        securityStatus = when (result) {
            is ZeroTrustVerifier.VerificationResult.Success -> SecurityStatus.Secure()
            is ZeroTrustVerifier.VerificationResult.Suspicious -> SecurityStatus.Warning("Suspicious behavior detected")
            is ZeroTrustVerifier.VerificationResult.Failed -> SecurityStatus.Critical("Verification failed: ${result.reason}")
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Security Dashboard") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Security Status Card
            item {
                SecurityStatusCard(securityStatus)
            }
            
            // Resource Usage Card
            item {
                resourceUsage?.let { usage ->
                    ResourceUsageCard(usage)
                }
            }
            
            // Anomalies Section
            item {
                Text(
                    text = "Security Alerts",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            
            if (anomalies.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("No anomalies detected")
                        }
                    }
                }
            } else {
                items(anomalies) { anomaly ->
                    AnomalyCard(anomaly)
                }
            }
        }
    }
}

@Composable
private fun SecurityStatusCard(status: SecurityStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (status) {
                is SecurityStatus.Secure -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                is SecurityStatus.Warning -> Color(0xFFFFC107).copy(alpha = 0.1f)
                is SecurityStatus.Critical -> Color(0xFFF44336).copy(alpha = 0.1f)
                is SecurityStatus.Loading -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (status) {
                    is SecurityStatus.Secure -> Icons.Default.Security
                    is SecurityStatus.Warning -> Icons.Default.Warning
                    is SecurityStatus.Critical -> Icons.Default.Error
                    is SecurityStatus.Loading -> Icons.Default.Refresh
                },
                contentDescription = null,
                tint = when (status) {
                    is SecurityStatus.Secure -> Color(0xFF4CAF50)
                    is SecurityStatus.Warning -> Color(0xFFFFC107)
                    is SecurityStatus.Critical -> Color(0xFFF44336)
                    is SecurityStatus.Loading -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(48.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column {
                Text(
                    text = when (status) {
                        is SecurityStatus.Secure -> "Secure"
                        is SecurityStatus.Warning -> "Warning"
                        is SecurityStatus.Critical -> "Critical"
                        is SecurityStatus.Loading -> "Loading..."
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = when (status) {
                        is SecurityStatus.Secure -> status.message
                        is SecurityStatus.Warning -> status.message
                        is SecurityStatus.Critical -> status.message
                        is SecurityStatus.Loading -> "Analyzing plugin security..."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ResourceUsageCard(usage: PluginResourceLimiter.ResourceUsage) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Resource Usage",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            ResourceMetric(
                label = "Memory",
                value = "${usage.memoryUsedMB} MB",
                progress = usage.memoryUsedMB / 100f
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            ResourceMetric(
                label = "CPU",
                value = "${usage.cpuPercent.toInt()}%",
                progress = usage.cpuPercent / 100f
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            ResourceMetric(
                label = "Threads",
                value = usage.activeThreads.toString(),
                progress = usage.activeThreads / 10f
            )
        }
    }
}

@Composable
private fun ResourceMetric(label: String, value: String, progress: Float) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = when {
                progress < 0.5f -> Color(0xFF4CAF50)
                progress < 0.8f -> Color(0xFFFFC107)
                else -> Color(0xFFF44336)
            },
        )
    }
}

@Composable
private fun AnomalyCard(anomaly: PluginBehaviorAnalyzer.Anomaly) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (anomaly.severity) {
                PluginBehaviorAnalyzer.Severity.LOW -> MaterialTheme.colorScheme.surfaceVariant
                PluginBehaviorAnalyzer.Severity.MEDIUM -> Color(0xFFFFC107).copy(alpha = 0.1f)
                PluginBehaviorAnalyzer.Severity.HIGH -> Color(0xFFFF9800).copy(alpha = 0.1f)
                PluginBehaviorAnalyzer.Severity.CRITICAL -> Color(0xFFF44336).copy(alpha = 0.1f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = when (anomaly.severity) {
                    PluginBehaviorAnalyzer.Severity.LOW -> Icons.Default.Info
                    PluginBehaviorAnalyzer.Severity.MEDIUM -> Icons.Default.Warning
                    PluginBehaviorAnalyzer.Severity.HIGH -> Icons.Default.Error
                    PluginBehaviorAnalyzer.Severity.CRITICAL -> Icons.Default.Cancel
                },
                contentDescription = null,
                tint = when (anomaly.severity) {
                    PluginBehaviorAnalyzer.Severity.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
                    PluginBehaviorAnalyzer.Severity.MEDIUM -> Color(0xFFFFC107)
                    PluginBehaviorAnalyzer.Severity.HIGH -> Color(0xFFFF9800)
                    PluginBehaviorAnalyzer.Severity.CRITICAL -> Color(0xFFF44336)
                }
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column {
                Text(
                    text = anomaly.type.name.replace("_", " "),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = anomaly.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "Detected ${formatTimestamp(anomaly.detectedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "just now"
        diff < 3600_000 -> "${diff / 60_000} minutes ago"
        diff < 86400_000 -> "${diff / 3600_000} hours ago"
        else -> "${diff / 86400_000} days ago"
    }
}

sealed class SecurityStatus {
    object Loading : SecurityStatus()
    data class Secure(val message: String = "All security checks passed") : SecurityStatus()
    data class Warning(val message: String) : SecurityStatus()
    data class Critical(val message: String) : SecurityStatus()
}
