package com.ble1st.connectias.ui.plugin.security

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ble1st.connectias.plugin.security.SecurityAuditManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityAuditDashboardScreen(
    auditManager: SecurityAuditManager,
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val auditEvents by auditManager.recentEvents.collectAsStateWithLifecycle(emptyList())
    val securityStats by auditManager.securityStats.collectAsStateWithLifecycle(SecurityAuditManager.SecurityStatistics())
    
    var selectedSeverity by remember { mutableStateOf<SecurityAuditManager.SecuritySeverity?>(null) }
    var selectedEventType by remember { mutableStateOf<SecurityAuditManager.SecurityEventType?>(null) }
    var selectedPluginId by remember { mutableStateOf<String?>(null) }
    
    // Filter events based on selections
    val filteredEvents = remember(auditEvents, selectedSeverity, selectedEventType, selectedPluginId) {
        auditEvents.filter { event ->
            (selectedSeverity == null || event.severity == selectedSeverity) &&
            (selectedEventType == null || event.eventType == selectedEventType) &&
            (selectedPluginId == null || event.pluginId == selectedPluginId)
        }
    }
    
    val dateFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header mit Statistics
        SecurityAuditHeader(
            stats = securityStats,
            onNavigateBack = onNavigateBack
        )
        
        // Filter Row
        SecurityFilterRow(
            events = auditEvents,
            selectedSeverity = selectedSeverity,
            selectedEventType = selectedEventType,
            selectedPluginId = selectedPluginId,
            onSeverityChange = { selectedSeverity = it },
            onEventTypeChange = { selectedEventType = it },
            onPluginIdChange = { selectedPluginId = it }
        )
        
        // Events List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (filteredEvents.isEmpty()) {
                item {
                    SecurityEmptyState(
                        hasFilters = selectedSeverity != null || selectedEventType != null || selectedPluginId != null
                    )
                }
            } else {
                items(
                    items = filteredEvents,
                    key = { it.id }
                ) { event ->
                    SecurityAuditEventCard(
                        event = event,
                        dateFormatter = dateFormatter,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecurityAuditHeader(
    stats: SecurityAuditManager.SecurityStatistics,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Security Audit Dashboard",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Live Indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (stats.totalEvents > 0) Color.Green else Color.Gray,
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "LIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Statistics Cards
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                item {
                    StatisticCard(
                        title = "Total Events",
                        value = stats.totalEvents.toString(),
                        icon = Icons.Default.EventNote,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                item {
                    StatisticCard(
                        title = "Critical",
                        value = stats.criticalEvents.toString(),
                        icon = Icons.Default.Warning,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                item {
                    StatisticCard(
                        title = "High Severity",
                        value = stats.highSeverityEvents.toString(),
                        icon = Icons.Default.PriorityHigh,
                        color = Color(0xFFFF6B35)
                    )
                }
                item {
                    StatisticCard(
                        title = "Plugin Violations",
                        value = stats.pluginViolations.toString(),
                        icon = Icons.Default.Extension,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                item {
                    StatisticCard(
                        title = "Network Issues",
                        value = stats.networkViolations.toString(),
                        icon = Icons.Default.NetworkCheck,
                        color = Color(0xFF4CAF50)
                    )
                }
                item {
                    StatisticCard(
                        title = "Resource Issues",
                        value = stats.resourceViolations.toString(),
                        icon = Icons.Default.Memory,
                        color = Color(0xFF9C27B0)
                    )
                }
                item {
                    StatisticCard(
                        title = "Spoofing Attempts",
                        value = stats.spoofingAttempts.toString(),
                        icon = Icons.Default.Security,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun StatisticCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.width(120.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecurityFilterRow(
    events: List<SecurityAuditManager.SecurityAuditEvent>,
    selectedSeverity: SecurityAuditManager.SecuritySeverity?,
    selectedEventType: SecurityAuditManager.SecurityEventType?,
    selectedPluginId: String?,
    onSeverityChange: (SecurityAuditManager.SecuritySeverity?) -> Unit,
    onEventTypeChange: (SecurityAuditManager.SecurityEventType?) -> Unit,
    onPluginIdChange: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val severities = remember(events) {
        events.map { it.severity }.distinct().sorted()
    }
    
    val eventTypes = remember(events) {
        events.map { it.eventType }.distinct().sortedBy { it.name }
    }
    
    val pluginIds = remember(events) {
        events.mapNotNull { it.pluginId }.distinct().sorted()
    }
    
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Severity Filter
        item {
            FilterChip(
                selected = selectedSeverity != null,
                onClick = { 
                    onSeverityChange(if (selectedSeverity != null) null else severities.firstOrNull())
                },
                label = { 
                    Text(selectedSeverity?.name ?: "Severity") 
                },
                leadingIcon = if (selectedSeverity != null) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = when (selectedSeverity) {
                        SecurityAuditManager.SecuritySeverity.CRITICAL -> MaterialTheme.colorScheme.errorContainer
                        SecurityAuditManager.SecuritySeverity.HIGH -> Color(0xFFFFE0B2)
                        else -> MaterialTheme.colorScheme.primaryContainer
                    }
                )
            )
        }
        
        // Event Type Filter
        item {
            FilterChip(
                selected = selectedEventType != null,
                onClick = { 
                    onEventTypeChange(if (selectedEventType != null) null else eventTypes.firstOrNull())
                },
                label = { 
                    Text(selectedEventType?.name?.replace("_", " ")?.take(15) ?: "Event Type") 
                },
                leadingIcon = if (selectedEventType != null) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else null
            )
        }
        
        // Plugin Filter
        if (pluginIds.isNotEmpty()) {
            item {
                FilterChip(
                    selected = selectedPluginId != null,
                    onClick = { 
                        onPluginIdChange(if (selectedPluginId != null) null else pluginIds.firstOrNull())
                    },
                    label = { 
                        Text(selectedPluginId?.take(20) ?: "Plugin") 
                    },
                    leadingIcon = if (selectedPluginId != null) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null
                )
            }
        }
        
        // Clear All
        if (selectedSeverity != null || selectedEventType != null || selectedPluginId != null) {
            item {
                OutlinedButton(
                    onClick = {
                        onSeverityChange(null)
                        onEventTypeChange(null)
                        onPluginIdChange(null)
                    },
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear filters",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear")
                }
            }
        }
    }
}

@Composable
private fun SecurityAuditEventCard(
    event: SecurityAuditManager.SecurityAuditEvent,
    dateFormatter: SimpleDateFormat,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = modifier
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = when (event.severity) {
                SecurityAuditManager.SecuritySeverity.CRITICAL -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                SecurityAuditManager.SecuritySeverity.HIGH -> Color(0xFFFFE0B2).copy(alpha = 0.3f)
                SecurityAuditManager.SecuritySeverity.MEDIUM -> Color(0xFFFFF3E0).copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = when (event.severity) {
            SecurityAuditManager.SecuritySeverity.CRITICAL -> BorderStroke(2.dp, MaterialTheme.colorScheme.error)
            SecurityAuditManager.SecuritySeverity.HIGH -> BorderStroke(1.dp, Color(0xFFFF6B35))
            else -> null
        }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Event Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Severity Indicator
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                color = when (event.severity) {
                                    SecurityAuditManager.SecuritySeverity.CRITICAL -> MaterialTheme.colorScheme.error
                                    SecurityAuditManager.SecuritySeverity.HIGH -> Color(0xFFFF6B35)
                                    SecurityAuditManager.SecuritySeverity.MEDIUM -> Color(0xFFFFC107)
                                    SecurityAuditManager.SecuritySeverity.LOW -> Color(0xFF4CAF50)
                                    SecurityAuditManager.SecuritySeverity.INFO -> MaterialTheme.colorScheme.primary
                                },
                                shape = CircleShape
                            )
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Text(
                        text = event.severity.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = when (event.severity) {
                            SecurityAuditManager.SecuritySeverity.CRITICAL -> MaterialTheme.colorScheme.error
                            SecurityAuditManager.SecuritySeverity.HIGH -> Color(0xFFFF6B35)
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Event Type
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = event.eventType.name.replace("_", " "),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                // Timestamp
                Text(
                    text = dateFormatter.format(Date(event.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Event Message
            Text(
                text = event.message,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis
            )
            
            // Plugin ID
            if (!event.pluginId.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = event.pluginId,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Expanded Details
            if (expanded && event.details.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Details:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                
                event.details.forEach { (key, value) ->
                    Row(
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "$key: ",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            
            // Stack Trace
            if (expanded && !event.stackTrace.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Stack Trace:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = event.stackTrace.take(500) + if (event.stackTrace.length > 500) "..." else "",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(8.dp),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun SecurityEmptyState(
    hasFilters: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (hasFilters) Icons.Default.FilterList else Icons.Default.Shield,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = if (hasFilters) "No events match your filters" else "No security events recorded",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Text(
            text = if (hasFilters) 
                "Try adjusting your filter criteria to see more results." 
            else 
                "Security events will appear here as they are detected by the system.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
