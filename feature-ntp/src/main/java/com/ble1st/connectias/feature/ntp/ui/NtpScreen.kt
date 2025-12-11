package com.ble1st.connectias.feature.ntp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.feature.ntp.R

@Composable
fun NtpScreen(
    viewModel: NtpViewModel
) {
    val uiState by viewModel.state.collectAsState()

    ConnectiasTheme {
        Scaffold { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(text = stringResource(R.string.ntp_title), style = MaterialTheme.typography.headlineSmall)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = uiState.server,
                            onValueChange = viewModel::updateServer,
                            label = { Text(stringResource(R.string.ntp_server_label)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(onClick = viewModel::checkNtp, enabled = !uiState.isLoading) {
                            Text(text = stringResource(R.string.ntp_check))
                        }
                        uiState.result?.let { result ->
                            val content = if (result.error != null) {
                                result.error
                            } else {
                                "Offset: ${result.offsetMs} ms\nDelay: ${result.delayMs} ms"
                            }
                            Text(text = content ?: "", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            text = stringResource(R.string.ntp_hint),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                uiState.errorMessage?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
