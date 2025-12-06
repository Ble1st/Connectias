package com.ble1st.connectias.feature.security.encryption

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.common.ui.strings.getThemedString
import com.ble1st.connectias.feature.security.R

enum class EncryptionOperation(val labelKey: String) {
    ENCRYPT("encrypt"),
    DECRYPT("decrypt"),
    GENERATE_KEY("generate_key")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncryptionScreen(
    state: EncryptionState,
    onEncrypt: (String, String) -> Unit,
    onDecrypt: (String, String, String, String) -> Unit,
    onGenerateKey: () -> Unit,
    onReset: () -> Unit
) {
    var selectedOperation by remember { mutableStateOf(EncryptionOperation.ENCRYPT) }
    var input by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var iv by remember { mutableStateOf("") }
    var salt by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }

    // Reset result text when operation changes
    LaunchedEffect(selectedOperation) {
        onReset()
    }

    // Update input fields if operation succeeds (optional UX improvement)
    LaunchedEffect(state) {
        if (state is EncryptionState.Encrypted) {
            // Optionally copy output to clipboard or show success
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = getThemedString(stringResource(R.string.encryption_tools_title)),
            style = MaterialTheme.typography.headlineMedium
        )

        // Operation Selector
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                readOnly = true,
                value = when (selectedOperation) {
                    EncryptionOperation.ENCRYPT -> getThemedString(stringResource(R.string.encrypt))
                    EncryptionOperation.DECRYPT -> getThemedString(stringResource(R.string.decrypt))
                    EncryptionOperation.GENERATE_KEY -> getThemedString(stringResource(R.string.generate_key))
                },
                onValueChange = {},
                label = { Text(getThemedString(stringResource(R.string.operation))) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                EncryptionOperation.values().forEach { operation ->
                    DropdownMenuItem(
                        text = { 
                            Text(when (operation) {
                                EncryptionOperation.ENCRYPT -> getThemedString(stringResource(R.string.encrypt))
                                EncryptionOperation.DECRYPT -> getThemedString(stringResource(R.string.decrypt))
                                EncryptionOperation.GENERATE_KEY -> getThemedString(stringResource(R.string.generate_key))
                            })
                        },
                        onClick = {
                            selectedOperation = operation
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }

        if (selectedOperation != EncryptionOperation.GENERATE_KEY) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text(if (selectedOperation == EncryptionOperation.ENCRYPT) getThemedString(stringResource(R.string.text_to_encrypt)) else getThemedString(stringResource(R.string.encrypted_data_base64))) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(getThemedString(stringResource(R.string.password_label))) },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    val description = if (passwordVisible) "Hide password" else "Show password"
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(imageVector = image, contentDescription = description)
                    }
                }
            )
        }

        if (selectedOperation == EncryptionOperation.DECRYPT) {
            OutlinedTextField(
                value = iv,
                onValueChange = { iv = it },
                label = { Text(getThemedString(stringResource(R.string.iv_initialization_vector))) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = salt,
                onValueChange = { salt = it },
                label = { Text(getThemedString(stringResource(R.string.salt))) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Button(
            onClick = {
                when (selectedOperation) {
                    EncryptionOperation.ENCRYPT -> onEncrypt(input, password)
                    EncryptionOperation.DECRYPT -> onDecrypt(input, iv, salt, password)
                    EncryptionOperation.GENERATE_KEY -> onGenerateKey()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = state !is EncryptionState.Processing
        ) {
            if (state is EncryptionState.Processing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(getThemedString(stringResource(R.string.execute)))
            }
        }

        if (state !is EncryptionState.Idle && state !is EncryptionState.Processing) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(getThemedString(stringResource(R.string.result)), style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    when (state) {
                        is EncryptionState.Encrypted -> {
                            ResultRow(getThemedString(stringResource(R.string.encrypted_data)), state.encryptedData)
                            ResultRow(getThemedString(stringResource(R.string.iv)), state.iv)
                            ResultRow(getThemedString(stringResource(R.string.salt)), state.salt)
                        }
                        is EncryptionState.Decrypted -> {
                            ResultRow(getThemedString(stringResource(R.string.plaintext)), state.plaintext)
                        }
                        is EncryptionState.KeyGenerated -> {
                            ResultRow(getThemedString(stringResource(R.string.generated_key)), state.key)
                        }
                        is EncryptionState.Error -> {
                            Text(
                                text = getThemedString(stringResource(R.string.error_prefix, state.message)),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
