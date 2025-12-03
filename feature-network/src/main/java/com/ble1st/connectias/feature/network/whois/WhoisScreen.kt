package com.ble1st.connectias.feature.network.whois

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Domain
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

/**
 * WHOIS Lookup screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhoisScreen(
    viewModel: WhoisViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    var lookupType by remember { mutableStateOf(0) } // 0 = Domain, 1 = IP
    var isLoading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<WhoisResult?>(null) }

    val whoisProvider = remember { WhoisProvider() }
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WHOIS Lookup") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero section
            item {
                WhoisHeroSection()
            }

            // Input section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Lookup type selector
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SegmentedButton(
                                selected = lookupType == 0,
                                onClick = { lookupType = 0 },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                            ) {
                                Text("Domain")
                            }
                            SegmentedButton(
                                selected = lookupType == 1,
                                onClick = { lookupType = 1 },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                            ) {
                                Text("IP Address")
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text(if (lookupType == 0) "Domain Name" else "IP Address") },
                            placeholder = { 
                                Text(if (lookupType == 0) "example.com" else "8.8.8.8") 
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            enabled = !isLoading
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        FilledTonalButton(
                            onClick = {
                                isLoading = true
                                scope.launch {
                                    result = if (lookupType == 0) {
                                        whoisProvider.lookupDomain(query)
                                    } else {
                                        whoisProvider.lookupIp(query)
                                    }
                                    isLoading = false
                                }
                            },
                            enabled = query.isNotBlank() && !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Search, contentDescription = null)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Lookup")
                        }
                    }
                }
            }

            // Results
            result?.let { whoisResult ->
                // Status card
                item {
                    WhoisStatusCard(result = whoisResult)
                }

                if (whoisResult.success) {
                    // Domain info
                    if (whoisResult.registrar != null) {
                        item {
                            WhoisInfoCard(
                                title = "Registration Info",
                                entries = listOfNotNull(
                                    whoisResult.registrar?.let { "Registrar" to it },
                                    whoisResult.registrant?.let { "Registrant" to it },
                                    whoisResult.registrantOrg?.let { "Organization" to it },
                                    whoisResult.creationDate?.let { "Created" to it },
                                    whoisResult.expirationDate?.let { "Expires" to it },
                                    whoisResult.updatedDate?.let { "Updated" to it },
                                    whoisResult.dnssec?.let { "DNSSEC" to it }
                                )
                            )
                        }
                    }

                    // IP info
                    if (whoisResult.ipRange != null) {
                        item {
                            WhoisInfoCard(
                                title = "Network Info",
                                entries = listOfNotNull(
                                    whoisResult.ipRange?.let { "IP Range" to it },
                                    whoisResult.netName?.let { "Network Name" to it },
                                    whoisResult.orgName?.let { "Organization" to it },
                                    whoisResult.country?.let { "Country" to it },
                                    whoisResult.abuseContact?.let { "Abuse Contact" to it }
                                )
                            )
                        }
                    }

                    // Name servers
                    if (whoisResult.nameServers.isNotEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Name Servers",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    whoisResult.nameServers.forEach { ns ->
                                        Text(
                                            text = ns,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Status
                    if (whoisResult.status.isNotEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Domain Status",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.horizontalScroll(rememberScrollState())
                                    ) {
                                        whoisResult.status.forEach { status ->
                                            AssistChip(
                                                onClick = { },
                                                label = { 
                                                    Text(
                                                        text = status.substringBefore(" "),
                                                        style = MaterialTheme.typography.labelSmall
                                                    ) 
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Raw data
                    whoisResult.rawData?.let { raw ->
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Raw Data",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        IconButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(raw))
                                            }
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp)
                                            .horizontalScroll(rememberScrollState())
                                    ) {
                                        Text(
                                            text = raw,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WhoisHeroSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF8B5CF6).copy(alpha = 0.2f),
                            Color(0xFFD946EF).copy(alpha = 0.2f)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF8B5CF6), Color(0xFFD946EF))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Domain,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = "WHOIS Lookup",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Look up domain and IP registration info",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun WhoisStatusCard(result: WhoisResult) {
    val backgroundColor = if (result.success) {
        Color(0xFF10B981).copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (result.success) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = if (result.success) Color(0xFF10B981) else MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = if (result.success) "Lookup Successful" else "Lookup Failed",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = result.query,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                result.error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun WhoisInfoCard(
    title: String,
    entries: List<Pair<String, String>>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            entries.forEach { (label, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

