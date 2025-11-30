package com.ble1st.connectias.feature.security.certificate

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun CertificateAnalyzerScreen(
    state: CertificateState,
    onAnalyze: (String) -> Unit
) {
    var url by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Certificate Analyzer",
                style = MaterialTheme.typography.headlineMedium
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Enter URL") },
                        placeholder = { Text("https://example.com") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { onAnalyze(url) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state !is CertificateState.Loading && url.isNotBlank()
                    ) {
                        if (state is CertificateState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Analyze")
                        }
                    }
                }
            }
        }

        if (state is CertificateState.Success) {
            val info = state.info
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Certificate Details", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        StatusBadge(info)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        InfoRow("Subject", info.subject)
                        InfoRow("Issuer", info.issuer)
                        InfoRow("Valid From", SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(info.notBefore))
                        InfoRow("Valid To", SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(info.notAfter))
                        InfoRow("Days Remaining", "${info.daysUntilExpiry}")
                        InfoRow("Self-Signed", if (info.isSelfSigned) "Yes" else "No")
                        InfoRow("Signature Algo", info.signatureAlgorithm)
                    }
                }
            }
        } else if (state is CertificateState.Error) {
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
private fun StatusBadge(info: CertificateInfo) {
    val (color, text) = when {
        info.isExpired -> MaterialTheme.colorScheme.error to "EXPIRED"
        info.isNotYetValid -> Color(0xFFFF9800) to "NOT YET VALID"
        info.daysUntilExpiry <= 30 -> Color(0xFFFF9800) to "EXPIRING SOON"
        else -> Color(0xFF4CAF50) to "VALID"
    }
    
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.border(1.dp, color, MaterialTheme.shapes.small)
    ) {
        Text(
            text = text,
            color = color,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
