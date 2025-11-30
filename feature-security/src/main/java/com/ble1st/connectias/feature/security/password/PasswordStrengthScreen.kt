package com.ble1st.connectias.feature.security.password

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun PasswordStrengthScreen(
    state: PasswordState,
    onAnalyze: (String) -> Unit,
    onGenerate: (Int, Boolean) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var lengthText by remember { mutableStateOf("16") }
    var includeSpecial by remember { mutableStateOf(true) }

    // Update password field when generated
    LaunchedEffect(state) {
        if (state is PasswordState.Generated) {
            password = state.password
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Password Strength",
                style = MaterialTheme.typography.headlineMedium
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
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
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(
                        onClick = { onAnalyze(password) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = password.isNotEmpty()
                    ) {
                        Text("Check Strength")
                    }
                }
            }
        }

        if (state is PasswordState.Analyzed) {
            item {
                StrengthResultCard(state.strength)
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Password Generator", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = lengthText,
                            onValueChange = { lengthText = it },
                            label = { Text("Length") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Checkbox(
                                checked = includeSpecial,
                                onCheckedChange = { includeSpecial = it }
                            )
                            Text("Special Chars")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(
                        onClick = { 
                            val length = lengthText.toIntOrNull() ?: 16
                            onGenerate(length, includeSpecial) 
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("Generate Secure Password")
                    }
                }
            }
        }
    }
}

@Composable
private fun StrengthResultCard(strength: PasswordStrength) {
    val (color, text) = when (strength.strength) {
        Strength.VERY_WEAK -> Color.Red to "Very Weak"
        Strength.WEAK -> Color(0xFFFF9800) to "Weak"
        Strength.MODERATE -> Color(0xFFFFC107) to "Moderate"
        Strength.STRONG -> Color(0xFF4CAF50) to "Strong"
        Strength.VERY_STRONG -> Color(0xFF2E7D32) to "Very Strong"
    }

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
                Text("Score: ${strength.score}/10", style = MaterialTheme.typography.titleMedium, color = color)
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
            
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { (strength.score / 10f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = color
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            Text("Entropy: ${String.format("%.2f", strength.entropy)} bits", style = MaterialTheme.typography.bodyMedium)
            
            if (strength.feedback.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Feedback:", style = MaterialTheme.typography.labelMedium)
                strength.feedback.forEach { feedback ->
                    Text("• $feedback", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
