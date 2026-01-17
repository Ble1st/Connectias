package com.ble1st.connectias.ui.plugin.security

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ble1st.connectias.plugin.security.EnhancedPluginNetworkPolicy
import timber.log.Timber

/**
 * Network Policy Configuration UI
 * 
 * Provides comprehensive network policy management for plugins including:
 * - Domain allow/block lists
 * - Port configuration
 * - Telemetry-only mode
 * - Bandwidth limits
 * - Policy templates
 * - Live testing
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkPolicyConfigurationScreen(
    pluginId: String,
    networkPolicy: EnhancedPluginNetworkPolicy,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var policy by remember { mutableStateOf(networkPolicy.getPolicy(pluginId)) }
    var isTelemetryOnly by remember { mutableStateOf(networkPolicy.isTelemetryOnlyMode(pluginId)) }
    var showAddDomainDialog by remember { mutableStateOf(false) }
    var showAddPortDialog by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }
    var showTestDialog by remember { mutableStateOf(false) }
    var hasUnsavedChanges by remember { mutableStateOf(false) }
    
    // Refresh policy when it changes
    LaunchedEffect(pluginId) {
        policy = networkPolicy.getPolicy(pluginId)
        isTelemetryOnly = networkPolicy.isTelemetryOnlyMode(pluginId)
    }
    
    if (policy == null) {
        // Plugin not registered - show registration UI
        NoPolicyFoundScreen(
            pluginId = pluginId,
            onRegisterPlugin = { telemetryOnlyMode ->
                networkPolicy.registerPlugin(pluginId, telemetryOnlyMode)
                policy = networkPolicy.getPolicy(pluginId)
                isTelemetryOnly = telemetryOnlyMode
            },
            onNavigateBack = onNavigateBack
        )
        return
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Network Policy: $pluginId") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Policy Templates
                    IconButton(onClick = { showTemplateDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Templates")
                    }
                    // Test Policy
                    IconButton(onClick = { showTestDialog = true }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Test")
                    }
                    // Save Changes
                    IconButton(
                        onClick = {
                            saveChanges(networkPolicy, pluginId, isTelemetryOnly)
                            hasUnsavedChanges = false
                        },
                        enabled = hasUnsavedChanges
                    ) {
                        Icon(
                            Icons.Default.Save, 
                            contentDescription = "Save",
                            tint = if (hasUnsavedChanges) Color.Green else LocalContentColor.current
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Policy Status Card
            item {
                PolicyStatusCard(
                    policy = policy!!,
                    isTelemetryOnly = isTelemetryOnly,
                    onTelemetryModeChanged = { enabled ->
                        isTelemetryOnly = enabled
                        hasUnsavedChanges = true
                    }
                )
            }
            
            // Bandwidth & Limits Card
            item {
                BandwidthLimitsCard(
                    policy = policy!!,
                    onBandwidthChanged = { hasUnsavedChanges = true },
                    onConnectionLimitChanged = { hasUnsavedChanges = true }
                )
            }
            
            // Domain Management Card
            item {
                DomainManagementCard(
                    policy = policy!!,
                    onAddDomain = { showAddDomainDialog = true },
                    onRemoveDomain = { domain, isAllowed ->
                        if (isAllowed) {
                            policy!!.allowedDomains.remove(domain)
                        } else {
                            policy!!.blockedDomains.remove(domain)
                        }
                        hasUnsavedChanges = true
                    }
                )
            }
            
            // Port Management Card
            item {
                PortManagementCard(
                    policy = policy!!,
                    onAddPort = { showAddPortDialog = true },
                    onRemovePort = { port, isAllowed ->
                        if (isAllowed) {
                            policy!!.allowedPorts.remove(port)
                        } else {
                            policy!!.blockedPorts.remove(port)
                        }
                        hasUnsavedChanges = true
                    }
                )
            }
            
            // Security Recommendations
            item {
                SecurityRecommendationsCard(policy = policy!!)
            }
        }
    }
    
    // Dialogs
    if (showAddDomainDialog) {
        AddDomainDialog(
            onDismiss = { showAddDomainDialog = false },
            onAddDomain = { domain, isAllowed ->
                if (isAllowed) {
                    policy!!.allowedDomains.add(domain)
                } else {
                    policy!!.blockedDomains.add(domain)
                }
                hasUnsavedChanges = true
                showAddDomainDialog = false
            }
        )
    }
    
    if (showAddPortDialog) {
        AddPortDialog(
            onDismiss = { showAddPortDialog = false },
            onAddPort = { port, isAllowed ->
                if (isAllowed) {
                    policy!!.allowedPorts.add(port)
                } else {
                    policy!!.blockedPorts.add(port)
                }
                hasUnsavedChanges = true
                showAddPortDialog = false
            }
        )
    }
    
    if (showTemplateDialog) {
        PolicyTemplateDialog(
            onDismiss = { showTemplateDialog = false },
            onApplyTemplate = { template ->
                applyPolicyTemplate(policy!!, template)
                hasUnsavedChanges = true
                showTemplateDialog = false
            }
        )
    }
    
    if (showTestDialog) {
        PolicyTestDialog(
            policy = policy!!,
            networkPolicy = networkPolicy,
            pluginId = pluginId,
            onDismiss = { showTestDialog = false }
        )
    }
}

@Composable
private fun NoPolicyFoundScreen(
    pluginId: String,
    onRegisterPlugin: (Boolean) -> Unit,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "No Network Policy Found",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Plugin $pluginId is not registered for network policy management.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = { onRegisterPlugin(false) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Register with Full Network Access")
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedButton(
            onClick = { onRegisterPlugin(true) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Security, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Register as Telemetry-Only")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        TextButton(onClick = onNavigateBack) {
            Text("Go Back")
        }
    }
}

@Composable
private fun PolicyStatusCard(
    policy: EnhancedPluginNetworkPolicy.NetworkPolicyConfig,
    isTelemetryOnly: Boolean,
    onTelemetryModeChanged: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (policy.enabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Policy Status",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Icon(
                    imageVector = if (policy.enabled) Icons.Default.CheckCircle else Icons.Default.Error,
                    contentDescription = null,
                    tint = if (policy.enabled) Color.Green else Color.Red
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Mode: ${if (isTelemetryOnly) "Telemetry-Only" else "Full Network Access"}",
                style = MaterialTheme.typography.bodyLarge
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Telemetry-Only Mode")
                Switch(
                    checked = isTelemetryOnly,
                    onCheckedChange = onTelemetryModeChanged
                )
            }
            
            if (isTelemetryOnly) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "ℹ️ Only known telemetry domains are allowed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun BandwidthLimitsCard(
    policy: EnhancedPluginNetworkPolicy.NetworkPolicyConfig,
    onBandwidthChanged: () -> Unit,
    onConnectionLimitChanged: () -> Unit
) {
    var bandwidthMBps by remember { 
        mutableStateOf(
            if (policy.maxBandwidthBytesPerSecond.get() == 0L) "0" 
            else "${policy.maxBandwidthBytesPerSecond.get() / (1024 * 1024)}"
        )
    }
    var connectionLimit by remember { mutableStateOf(policy.maxConnectionsPerMinute.toString()) }
    
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Bandwidth & Connection Limits",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = bandwidthMBps,
                onValueChange = { 
                    bandwidthMBps = it
                    val mbps = it.toLongOrNull() ?: 0L
                    policy.maxBandwidthBytesPerSecond.set(mbps * 1024 * 1024)
                    onBandwidthChanged()
                },
                label = { Text("Max Bandwidth (MB/s, 0 = unlimited)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Speed, contentDescription = null) }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            OutlinedTextField(
                value = connectionLimit,
                onValueChange = { 
                    connectionLimit = it
                    onConnectionLimitChanged()
                },
                label = { Text("Max Connections per Minute") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) }
            )
        }
    }
}

@Composable
private fun DomainManagementCard(
    policy: EnhancedPluginNetworkPolicy.NetworkPolicyConfig,
    onAddDomain: () -> Unit,
    onRemoveDomain: (String, Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Domain Management",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                IconButton(onClick = onAddDomain) {
                    Icon(Icons.Default.Add, contentDescription = "Add Domain")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Allowed Domains
            Text(
                text = "Allowed Domains (${policy.allowedDomains.size})",
                style = MaterialTheme.typography.labelLarge,
                color = Color.Green
            )
            
            if (policy.allowedDomains.isEmpty()) {
                Text(
                    text = "No domains specifically allowed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            } else {
                policy.allowedDomains.take(5).forEach { domain ->
                    DomainChip(
                        domain = domain,
                        isAllowed = true,
                        onRemove = { onRemoveDomain(domain, true) }
                    )
                }
                if (policy.allowedDomains.size > 5) {
                    Text(
                        text = "... and ${policy.allowedDomains.size - 5} more",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Blocked Domains
            Text(
                text = "Blocked Domains (${policy.blockedDomains.size})",
                style = MaterialTheme.typography.labelLarge,
                color = Color.Red
            )
            
            policy.blockedDomains.take(3).forEach { domain ->
                DomainChip(
                    domain = domain,
                    isAllowed = false,
                    onRemove = { onRemoveDomain(domain, false) }
                )
            }
            if (policy.blockedDomains.size > 3) {
                Text(
                    text = "... and ${policy.blockedDomains.size - 3} more",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun DomainChip(
    domain: String,
    isAllowed: Boolean,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isAllowed) Icons.Default.CheckCircle else Icons.Default.Block,
                contentDescription = null,
                tint = if (isAllowed) Color.Green else Color.Red,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = domain,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove",
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun PortManagementCard(
    policy: EnhancedPluginNetworkPolicy.NetworkPolicyConfig,
    onAddPort: () -> Unit,
    onRemovePort: (Int, Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Port Management",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                IconButton(onClick = onAddPort) {
                    Icon(Icons.Default.Add, contentDescription = "Add Port")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Allowed: ${policy.allowedPorts.joinToString(", ")}",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "Blocked: ${policy.blockedPorts.joinToString(", ")}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun SecurityRecommendationsCard(
    policy: EnhancedPluginNetworkPolicy.NetworkPolicyConfig
) {
    val recommendations = mutableListOf<String>()
    
    // Generate recommendations
    if (policy.allowedDomains.isEmpty()) {
        recommendations.add("Consider restricting allowed domains for better security")
    }
    if (policy.maxBandwidthBytesPerSecond.get() == 0L) {
        recommendations.add("Set bandwidth limits to prevent abuse")
    }
    if (policy.allowedPorts.contains(22) || policy.allowedPorts.contains(23)) {
        recommendations.add("SSH/Telnet ports are suspicious for plugins")
    }
    
    if (recommendations.isNotEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Security Recommendations",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                recommendations.forEach { recommendation ->
                    Text(
                        text = "• $recommendation",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

// Helper functions
private fun saveChanges(
    networkPolicy: EnhancedPluginNetworkPolicy,
    pluginId: String,
    isTelemetryOnly: Boolean
) {
    try {
        networkPolicy.setTelemetryOnlyMode(pluginId, isTelemetryOnly)
        Timber.i("[NETWORK POLICY UI] Changes saved for plugin: $pluginId")
    } catch (e: Exception) {
        Timber.e(e, "[NETWORK POLICY UI] Failed to save changes for plugin: $pluginId")
    }
}

private fun applyPolicyTemplate(
    policy: EnhancedPluginNetworkPolicy.NetworkPolicyConfig,
    template: PolicyTemplate
) {
    when (template) {
        PolicyTemplate.STRICT -> {
            policy.allowedDomains.clear()
            policy.allowedPorts.clear()
            policy.allowedPorts.addAll(setOf(443)) // HTTPS only
            policy.maxBandwidthBytesPerSecond.set(1024 * 1024) // 1MB/s
        }
        PolicyTemplate.NORMAL -> {
            policy.allowedPorts.clear()
            policy.allowedPorts.addAll(setOf(80, 443, 8080, 8443))
            policy.maxBandwidthBytesPerSecond.set(10 * 1024 * 1024) // 10MB/s
        }
        PolicyTemplate.PERMISSIVE -> {
            policy.allowedPorts.clear()
            policy.maxBandwidthBytesPerSecond.set(0) // Unlimited
        }
    }
}

enum class PolicyTemplate {
    STRICT, NORMAL, PERMISSIVE
}
