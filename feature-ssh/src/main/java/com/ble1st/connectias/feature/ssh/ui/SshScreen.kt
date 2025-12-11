package com.ble1st.connectias.feature.ssh.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.feature.ssh.R
import com.ble1st.connectias.feature.ssh.data.AuthMode
import com.ble1st.connectias.feature.ssh.data.SshProfile

@Composable
fun SshScreen(
    viewModel: SshViewModel,
    terminalViewModel: SshTerminalViewModel = hiltViewModel()
) {
    val uiState by viewModel.state.collectAsState()
    var activeProfile by remember { mutableStateOf<SshProfile?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadProfiles()
    }

    ConnectiasTheme {
        if (activeProfile != null) {
            BackHandler {
                activeProfile = null
            }
            SshTerminalScreen(
                viewModel = terminalViewModel,
                profile = activeProfile!!,
                onBack = { activeProfile = null }
            )
        } else {
            Scaffold { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(text = stringResource(R.string.ssh_title), style = MaterialTheme.typography.headlineSmall)
                    ProfileForm(uiState = uiState, viewModel = viewModel)
                    ProfilesList(
                        uiState = uiState, 
                        onTest = { viewModel.testConnection(it) },
                        onConnect = { activeProfile = it },
                        onDelete = { viewModel.deleteProfile(it) }
                    )
                    uiState.connectionResult?.let {
                        Text(text = it.message, color = if (it.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    }
                    uiState.errorMessage?.let {
                        Text(text = it, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileForm(
    uiState: SshUiState,
    viewModel: SshViewModel
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::updateName,
                label = { Text(stringResource(R.string.ssh_profile_name)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = uiState.host,
                onValueChange = viewModel::updateHost,
                label = { Text(stringResource(R.string.ssh_host_label)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = uiState.port.toString(),
                onValueChange = { viewModel.updatePort(it.toIntOrNull() ?: 22) },
                label = { Text(stringResource(R.string.ssh_port_label)) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            OutlinedTextField(
                value = uiState.username,
                onValueChange = viewModel::updateUsername,
                label = { Text(stringResource(R.string.ssh_user_label)) },
                modifier = Modifier.fillMaxWidth()
            )
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AuthMode.values().forEach { mode ->
                    FilterChip(
                        selected = uiState.authMode == mode,
                        onClick = { viewModel.updateAuthMode(mode) },
                        label = { Text(mode.name) }
                    )
                }
            }

            if (uiState.authMode == AuthMode.PASSWORD) {
                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = viewModel::updatePassword,
                    label = { Text(stringResource(R.string.ssh_password_label)) },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                OutlinedTextField(
                    value = uiState.privateKeyPath,
                    onValueChange = viewModel::updatePrivateKeyPath,
                    label = { Text("Private Key Path") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = uiState.keyPassword,
                    onValueChange = viewModel::updateKeyPassword,
                    label = { Text("Key Password (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = viewModel::saveProfile, enabled = !uiState.isLoading) {
                    Text(text = stringResource(R.string.ssh_save_profile))
                }
                Button(onClick = { viewModel.testConnection() }, enabled = !uiState.isLoading) {
                    Text(text = stringResource(R.string.ssh_test_connection))
                }
            }
        }
    }
}

@Composable
private fun ProfilesList(
    uiState: SshUiState,
    onTest: (SshProfile) -> Unit,
    onConnect: (SshProfile) -> Unit,
    onDelete: (SshProfile) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(R.string.ssh_profiles_title), style = MaterialTheme.typography.titleMedium)
            if (uiState.profiles.isEmpty()) {
                Text(text = stringResource(R.string.ssh_profiles_empty))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.profiles) { profile ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = profile.name, style = MaterialTheme.typography.bodyLarge)
                            Text(text = "${profile.username}@${profile.host}:${profile.port}", style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onConnect(profile) }) {
                                    Text("Connect")
                                }
                                Button(onClick = { onTest(profile) }) {
                                    Text("Test")
                                }
                                Button(onClick = { onDelete(profile) }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                                    Text("Delete")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

