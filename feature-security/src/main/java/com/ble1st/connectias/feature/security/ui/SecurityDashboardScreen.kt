package com.ble1st.connectias.feature.security.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.common.ui.strings.getThemedString
import com.ble1st.connectias.feature.security.R
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
            text = getThemedString(stringResource(R.string.security_dashboard_title)),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        when (state) {
            is SecurityState.Loading -> {
                CircularProgressIndicator()
                Text(getThemedString(stringResource(R.string.checking_security)), modifier = Modifier.padding(top = 16.dp))
            }
            is SecurityState.Error -> {
                Text(
                    text = getThemedString(stringResource(R.string.error_prefix, state.message)),
                    color = MaterialTheme.colorScheme.error
                )
                Button(onClick = onRefresh, modifier = Modifier.padding(top = 16.dp)) {
                    Text(getThemedString(stringResource(R.string.retry)))
                }
            }
            is SecurityState.Success -> {
                SecurityStatusContent(state.result)
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(onClick = onRefresh) {
                    Text(getThemedString(stringResource(R.string.refresh_status)))
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
                Text(getThemedString(stringResource(R.string.threats_found)), style = MaterialTheme.typography.labelLarge)
                result.threats.forEach { threat ->
                    Text("• ${formatThreat(threat)}")
                }
            } else {
                Text(getThemedString(stringResource(R.string.no_threats_detected)))
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