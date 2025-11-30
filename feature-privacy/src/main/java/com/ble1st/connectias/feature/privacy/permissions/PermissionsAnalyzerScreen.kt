package com.ble1st.connectias.feature.privacy.permissions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PermissionsAnalyzerScreen(
    state: PermissionsState,
    onAnalyze: () -> Unit,
    onAppClick: (AppPermissions) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (state is PermissionsState.Loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            PermissionsAnalyzerContent(
                state = state,
                onAnalyze = onAnalyze,
                onAppClick = onAppClick
            )
        }
    }
}

@Composable
private fun PermissionsAnalyzerContent(
    state: PermissionsState,
    onAnalyze: () -> Unit,
    onAppClick: (AppPermissions) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Permissions Analyzer",
                style = MaterialTheme.typography.headlineMedium
            )
            
            Button(
                onClick = onAnalyze,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Analyze Permissions")
            }
        }

        when (state) {
            is PermissionsState.Success -> {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Summary",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Total apps scanned: ${state.allApps.size}")
                            Text("Apps with risky permissions: ${state.riskyApps.size}")
                        }
                    }
                }

                item {
                    Text("Risky Apps", style = MaterialTheme.typography.titleLarge)
                }

                items(state.riskyApps) { app ->
                    RiskyAppItem(app, onClick = { onAppClick(app) })
                }
            }
            is PermissionsState.Recommendations -> {
                 item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                         Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Recommendations",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            state.recommendations.forEach { rec ->
                                 Text(
                                    "${rec.permission}: ${rec.recommendation} (Risk: ${rec.riskLevel})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                         }
                    }
                    
                    Button(
                        onClick = onAnalyze, // Go back to list by re-analyzing or resetting state ideally
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        Text("Back to List")
                    }
                 }
            }
            is PermissionsState.Idle -> {
                 item {
                     Text(
                        text = "Tap 'Analyze Permissions' to scan installed apps for risky permissions.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                 }
            }
            is PermissionsState.Error -> {
                 item {
                     Text(
                        text = "Error: ${state.message}",
                        color = MaterialTheme.colorScheme.error
                    )
                 }
            }
            else -> {}
        }
    }
}

@Composable
private fun RiskyAppItem(app: AppPermissions, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
             Icon(
                Icons.Default.Warning, 
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(app.appName, style = MaterialTheme.typography.titleMedium)
                Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Risky: ${app.riskyPermissions.joinToString(", ")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
