package com.ble1st.connectias.ui.plugin.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ble1st.connectias.plugin.security.EnhancedPluginNetworkPolicy

/**
 * Dialog for adding new domains to allow/block lists
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDomainDialog(
    onDismiss: () -> Unit,
    onAddDomain: (String, Boolean) -> Unit
) {
    var domainText by remember { mutableStateOf("") }
    var isAllowed by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Add Domain",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = domainText,
                    onValueChange = { 
                        domainText = it.lowercase().trim()
                        error = null
                    },
                    label = { Text("Domain (e.g., api.example.com)") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = error != null,
                    supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Action",
                    style = MaterialTheme.typography.labelLarge
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .selectable(
                                selected = isAllowed,
                                onClick = { isAllowed = true }
                            )
                            .weight(1f)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isAllowed,
                            onClick = { isAllowed = true }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color.Green,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Allow")
                    }
                    
                    Row(
                        modifier = Modifier
                            .selectable(
                                selected = !isAllowed,
                                onClick = { isAllowed = false }
                            )
                            .weight(1f)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !isAllowed,
                            onClick = { isAllowed = false }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Block,
                            contentDescription = null,
                            tint = Color.Red,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Block")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(
                        onClick = {
                            if (validateDomain(domainText)) {
                                onAddDomain(domainText, isAllowed)
                            } else {
                                error = "Invalid domain format"
                            }
                        },
                        enabled = domainText.isNotBlank()
                    ) {
                        Text("Add")
                    }
                }
            }
        }
    }
}

/**
 * Dialog for adding new ports to allow/block lists
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPortDialog(
    onDismiss: () -> Unit,
    onAddPort: (Int, Boolean) -> Unit
) {
    var portText by remember { mutableStateOf("") }
    var isAllowed by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Add Port",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = portText,
                    onValueChange = { 
                        portText = it.filter { char -> char.isDigit() }
                        error = null
                    },
                    label = { Text("Port Number (1-65535)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    isError = error != null,
                    supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) }
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Action",
                    style = MaterialTheme.typography.labelLarge
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .selectable(
                                selected = isAllowed,
                                onClick = { isAllowed = true }
                            )
                            .weight(1f)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isAllowed,
                            onClick = { isAllowed = true }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color.Green,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Allow")
                    }
                    
                    Row(
                        modifier = Modifier
                            .selectable(
                                selected = !isAllowed,
                                onClick = { isAllowed = false }
                            )
                            .weight(1f)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !isAllowed,
                            onClick = { isAllowed = false }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Block,
                            contentDescription = null,
                            tint = Color.Red,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Block")
                    }
                }
                
                // Port recommendations
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Common ports: 80 (HTTP), 443 (HTTPS), 8080 (Alt HTTP), 8443 (Alt HTTPS)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(
                        onClick = {
                            val port = portText.toIntOrNull()
                            if (port != null && port in 1..65535) {
                                onAddPort(port, isAllowed)
                            } else {
                                error = "Port must be between 1 and 65535"
                            }
                        },
                        enabled = portText.isNotBlank()
                    ) {
                        Text("Add")
                    }
                }
            }
        }
    }
}

/**
 * Dialog for applying policy templates
 */
@Composable
fun PolicyTemplateDialog(
    onDismiss: () -> Unit,
    onApplyTemplate: (PolicyTemplate) -> Unit
) {
    var selectedTemplate by remember { mutableStateOf<PolicyTemplate?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Policy Templates",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = "Apply predefined security templates",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                PolicyTemplate.values().forEach { template ->
                    PolicyTemplateCard(
                        template = template,
                        isSelected = selectedTemplate == template,
                        onSelect = { selectedTemplate = template }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(
                        onClick = {
                            selectedTemplate?.let { onApplyTemplate(it) }
                        },
                        enabled = selectedTemplate != null
                    ) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}

@Composable
private fun PolicyTemplateCard(
    template: PolicyTemplate,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val (title, description, icon, color) = when (template) {
        PolicyTemplate.STRICT -> Tuple4(
            "Strict Security",
            "HTTPS only (443), 1MB/s limit, minimal access",
            Icons.Default.Shield,
            MaterialTheme.colorScheme.error
        )
        PolicyTemplate.NORMAL -> Tuple4(
            "Balanced Security",
            "HTTP/HTTPS (80,443,8080,8443), 10MB/s limit",
            Icons.Default.Security,
            MaterialTheme.colorScheme.primary
        )
        PolicyTemplate.PERMISSIVE -> Tuple4(
            "Development Mode",
            "All ports, unlimited bandwidth - USE WITH CAUTION",
            Icons.Default.Warning,
            MaterialTheme.colorScheme.tertiary
        )
    }
    
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) color.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) CardDefaults.outlinedCardBorder(enabled = true) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = color
                )
            }
        }
    }
}

/**
 * Dialog for testing network policies
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PolicyTestDialog(
    @Suppress("UNUSED_PARAMETER") policy: EnhancedPluginNetworkPolicy.NetworkPolicyConfig,
    networkPolicy: EnhancedPluginNetworkPolicy,
    pluginId: String,
    onDismiss: () -> Unit
) {
    var testUrl by remember { mutableStateOf("https://api.example.com") }
    var isTelemetry by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<EnhancedPluginNetworkPolicy.NetworkPolicyResult?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Test Network Policy",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = "Test if a URL would be allowed by current policy",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedTextField(
                    value = testUrl,
                    onValueChange = { testUrl = it },
                    label = { Text("URL to test") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) }
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isTelemetry,
                        onCheckedChange = { isTelemetry = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Mark as telemetry request")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = {
                        testResult = try {
                            networkPolicy.isRequestAllowed(pluginId, testUrl, isTelemetry)
                        } catch (e: Exception) {
                            EnhancedPluginNetworkPolicy.NetworkPolicyResult.BLOCKED("Test failed: ${e.message}")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Test URL")
                }
                
                testResult?.let { result ->
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (result.allowed) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (result.allowed) Icons.Default.CheckCircle else Icons.Default.Block,
                                    contentDescription = null,
                                    tint = if (result.allowed) Color.Green else Color.Red
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (result.allowed) "ALLOWED" else "BLOCKED",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = result.reason,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

// Helper functions
private fun validateDomain(domain: String): Boolean {
    if (domain.isBlank()) return false
    
    // Basic domain validation
    val domainRegex = Regex("^([a-zA-Z0-9-]+\\.)*[a-zA-Z0-9-]+\\.[a-zA-Z]{2,}$")
    return domain.matches(domainRegex) || domain == "localhost"
}

// Helper data class for tuples
private data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
