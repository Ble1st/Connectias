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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.common.ui.strings.getThemedString
import com.ble1st.connectias.feature.security.R
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
                text = getThemedString(stringResource(R.string.certificate_analyzer_title)),
                style = MaterialTheme.typography.headlineMedium
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(getThemedString(stringResource(R.string.enter_url))) },
                        placeholder = { Text(getThemedString(stringResource(R.string.url_hint))) },
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
                            Text(getThemedString(stringResource(R.string.analyze)))
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
                        Text(getThemedString(stringResource(R.string.certificate_details)), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        StatusBadge(info)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        InfoRow(getThemedString(stringResource(R.string.subject)), info.subject)
                        InfoRow(getThemedString(stringResource(R.string.issuer)), info.issuer)
                        InfoRow(getThemedString(stringResource(R.string.valid_from)), SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(info.notBefore))
                        InfoRow(getThemedString(stringResource(R.string.valid_to)), SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(info.notAfter))
                        InfoRow(getThemedString(stringResource(R.string.days_remaining)), "${info.daysUntilExpiry}")
                        InfoRow(getThemedString(stringResource(R.string.self_signed)), if (info.isSelfSigned) getThemedString(stringResource(R.string.yes)) else getThemedString(stringResource(R.string.no)))
                        InfoRow(getThemedString(stringResource(R.string.signature_algo)), info.signatureAlgorithm)
                    }
                }
            }
        } else if (state is CertificateState.Error) {
            item {
                Text(
                    text = getThemedString(stringResource(R.string.error_prefix, state.message)),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun StatusBadge(info: CertificateInfo) {
    val (color, text) = when {
        info.isExpired -> MaterialTheme.colorScheme.error to getThemedString(stringResource(R.string.expired))
        info.isNotYetValid -> Color(0xFFFF9800) to getThemedString(stringResource(R.string.not_yet_valid))
        info.daysUntilExpiry <= 30 -> Color(0xFFFF9800) to getThemedString(stringResource(R.string.expiring_soon))
        else -> Color(0xFF4CAF50) to getThemedString(stringResource(R.string.valid))
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
