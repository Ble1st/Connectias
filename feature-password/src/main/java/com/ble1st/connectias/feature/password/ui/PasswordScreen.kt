package com.ble1st.connectias.feature.password.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.feature.password.R
import kotlin.math.roundToInt

@Composable
fun PasswordScreen(
    viewModel: PasswordViewModel
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
                Text(text = stringResource(R.string.password_title), style = MaterialTheme.typography.headlineSmall)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = uiState.passwordInput,
                            onValueChange = viewModel::onPasswordInputChanged,
                            label = { Text(stringResource(R.string.password_input_label)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        uiState.passwordCheck?.let { result ->
                            Text(
                                text = stringResource(R.string.password_strength_title, result.strength.name),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = buildString {
                                    append("Length: ${result.length}\n")
                                    append("Entropy: ${"%.2f".format(result.entropy)} bits\n")
                                    if (result.feedback.isNotEmpty()) {
                                        append(result.feedback.joinToString("\n"))
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(text = stringResource(R.string.generator_title), style = MaterialTheme.typography.titleMedium)
                        val length = uiState.generatorConfig.length.toFloat()
                        Slider(
                            value = length,
                            onValueChange = { value ->
                                viewModel.onGeneratorConfigChanged(
                                    uiState.generatorConfig.copy(length = value.roundToInt())
                                )
                            },
                            valueRange = 8f..64f,
                            steps = 7
                        )
                        Text(text = stringResource(R.string.generator_length, uiState.generatorConfig.length))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ToggleButton(
                                label = stringResource(R.string.generator_lower),
                                enabled = uiState.generatorConfig.includeLowercase
                            ) {
                                viewModel.onGeneratorConfigChanged(uiState.generatorConfig.copy(includeLowercase = !uiState.generatorConfig.includeLowercase))
                            }
                            ToggleButton(
                                label = stringResource(R.string.generator_upper),
                                enabled = uiState.generatorConfig.includeUppercase
                            ) {
                                viewModel.onGeneratorConfigChanged(uiState.generatorConfig.copy(includeUppercase = !uiState.generatorConfig.includeUppercase))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ToggleButton(
                                label = stringResource(R.string.generator_digits),
                                enabled = uiState.generatorConfig.includeDigits
                            ) {
                                viewModel.onGeneratorConfigChanged(uiState.generatorConfig.copy(includeDigits = !uiState.generatorConfig.includeDigits))
                            }
                            ToggleButton(
                                label = stringResource(R.string.generator_symbols),
                                enabled = uiState.generatorConfig.includeSymbols
                            ) {
                                viewModel.onGeneratorConfigChanged(uiState.generatorConfig.copy(includeSymbols = !uiState.generatorConfig.includeSymbols))
                            }
                        }
                        Button(onClick = viewModel::generatePassword) {
                            Text(text = stringResource(R.string.generator_action))
                        }
                        if (uiState.generatedPassword.isNotEmpty()) {
                            Text(text = stringResource(R.string.generator_result_title), style = MaterialTheme.typography.labelLarge)
                            OutlinedTextField(
                                value = uiState.generatedPassword,
                                onValueChange = {},
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp),
                                readOnly = true,
                                keyboardOptions = KeyboardType.Password.let { KeyboardOptions(keyboardType = it) }
                            )
                        }
                    }
                }

                uiState.errorMessage?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun ToggleButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(onClick = onClick) {
        Text(text = if (enabled) "[x] $label" else "[ ] $label")
    }
}
