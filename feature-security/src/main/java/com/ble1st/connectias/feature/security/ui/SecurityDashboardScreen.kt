package com.ble1st.connectias.feature.security.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.core.security.models.SecurityCheckResult
import com.ble1st.connectias.core.security.models.SecurityThreat

@Composable
fun SecurityDashboardScreen(
    state: SecurityState,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Security Dashboard",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        when (state) {
            is SecurityState.Loading -> {
                CircularProgressIndicator()
                Text("Checking security...", modifier = Modifier.padding(top = 16.dp))
            }
            is SecurityState.Error -> {
                Text(
                    text = "Error: ${state.message}",
                    color = MaterialTheme.colorScheme.error
                )
                Button(onClick = onRefresh, modifier = Modifier.padding(top = 16.dp)) {
                    Text("Retry")
                }
            }
            is SecurityState.Success -> {
                SecurityStatusContent(state.result)
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(onClick = onRefresh) {
                    Text("Refresh Status")
                }
            }
        }
    }
}

@Composable
fun SecurityStatusContent(result: SecurityCheckResult) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (result.isSecure) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (result.isSecure) "✓ Secure" else "⚠ Threats Detected",
                style = MaterialTheme.typography.titleMedium,
                color = if (result.isSecure) 
                    MaterialTheme.colorScheme.onPrimaryContainer 
                else 
                    MaterialTheme.colorScheme.onErrorContainer
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (result.threats.isNotEmpty()) {
                Text("Threats found:", style = MaterialTheme.typography.labelLarge)
                result.threats.forEach { threat ->
                    Text("• ${formatThreat(threat)}")
                }
            } else {
                Text("No threats detected. System is secure.")
            }
        }
    }
}

@Composable
fun ToolButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(text)
    }
}

private fun formatThreat(threat: SecurityThreat): String {
    return when (threat) {
        is SecurityThreat.RootDetected -> "Root Detected (${threat.method})"
        is SecurityThreat.DebuggerDetected -> "Debugger Detected (${threat.method})"
        is SecurityThreat.EmulatorDetected -> "Emulator Detected (${threat.method})"
        is SecurityThreat.TamperDetected -> "Tamper Detected (${threat.method})"
        is SecurityThreat.HookDetected -> "Hook Detected (${threat.method})"
    }
}