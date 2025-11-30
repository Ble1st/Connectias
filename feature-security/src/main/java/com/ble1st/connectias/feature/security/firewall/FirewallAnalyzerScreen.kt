package com.ble1st.connectias.feature.security.firewall

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun FirewallAnalyzerScreen(
    state: FirewallState,
    onAnalyze: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (state is FirewallState.Loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            FirewallAnalyzerContent(
                state = state,
                onAnalyze = onAnalyze
            )
        }
    }
}

@Composable
private fun FirewallAnalyzerContent(
    state: FirewallState,
    onAnalyze: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Firewall Analyzer",
                style = MaterialTheme.typography.headlineMedium
            )
            
            Button(
                onClick = onAnalyze,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Icon(Icons.Default.Shield, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Analyze Network Permissions")
            }
        }

        if (state is FirewallState.Success) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Analysis Summary",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Total apps with network access: ${state.apps.size}")
                        Text("Risky apps identified: ${state.riskyApps.size}")
                    }
                }
            }

            if (state.riskyApps.isNotEmpty()) {
                item {
                    Text("Risky Apps", style = MaterialTheme.typography.titleLarge)
                }
                
                items(state.riskyApps) { riskyApp ->
                    RiskyAppItem(riskyApp)
                }
            }
        } else if (state is FirewallState.Idle) {
            item {
                Text(
                    text = "Tap 'Analyze Network Permissions' to scan installed apps for network access risks.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (state is FirewallState.Error) {
            item {
                Text(
                    text = "Error: ${state.message}",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun RiskyAppItem(riskyApp: RiskyApp) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = riskyApp.appInfo.appName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                RiskBadge(riskyApp.riskLevel)
            }
            
            Text(
                text = riskyApp.appInfo.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Risk Factors:",
                style = MaterialTheme.typography.labelMedium
            )
            riskyApp.reasons.forEach { reason ->
                Row(verticalAlignment = Alignment.Top) {
                    Text("• ", style = MaterialTheme.typography.bodySmall)
                    Text(reason, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun RiskBadge(level: RiskLevel) {
    val (color, text) = when (level) {
        RiskLevel.HIGH -> MaterialTheme.colorScheme.error to "High Risk"
        RiskLevel.MEDIUM -> Color(0xFFFF9800) to "Medium Risk"
        RiskLevel.LOW -> Color(0xFF4CAF50) to "Low Risk"
    }
    
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.border(1.dp, color, MaterialTheme.shapes.small)
    ) {
        Text(
            text = text,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}
