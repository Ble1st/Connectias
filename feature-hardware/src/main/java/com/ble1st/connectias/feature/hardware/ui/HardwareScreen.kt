package com.ble1st.connectias.feature.hardware.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Hardware Dashboard screen showing available hardware features.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HardwareScreen(
    viewModel: HardwareViewModel = hiltViewModel(),
    onNavigateToNfcTools: () -> Unit = {},
    onNavigateToBluetoothAnalyzer: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hardware Tools") },
                actions = {
                    IconButton(onClick = { viewModel.refreshHardwareState() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
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
                HardwareHeroSection()
            }

            // NFC Tools Card
            item {
                HardwareFeatureCard(
                    icon = Icons.Default.Nfc,
                    title = "NFC Tools",
                    description = "Read, write, and format NFC tags. Create URI, text, WiFi, and contact records.",
                    isAvailable = uiState.isNfcAvailable,
                    isEnabled = uiState.isNfcEnabled,
                    statusText = when {
                        !uiState.isNfcAvailable -> "Not Available"
                        !uiState.isNfcEnabled -> "NFC Disabled"
                        else -> "Ready"
                    },
                    gradientColors = listOf(
                        Color(0xFF6366F1),
                        Color(0xFF8B5CF6)
                    ),
                    onClick = onNavigateToNfcTools
                )
            }

            // Bluetooth Analyzer Card
            item {
                HardwareFeatureCard(
                    icon = Icons.Default.Bluetooth,
                    title = "Bluetooth Analyzer",
                    description = "Scan for BLE devices, detect beacons, and analyze GATT services.",
                    isAvailable = uiState.isBluetoothAvailable,
                    isEnabled = uiState.isBluetoothEnabled,
                    statusText = when {
                        !uiState.isBluetoothAvailable -> "Not Available"
                        !uiState.isBluetoothEnabled -> "Bluetooth Disabled"
                        else -> "Ready"
                    },
                    additionalInfo = if (uiState.isBleSupported) "BLE Supported" else "BLE Not Supported",
                    gradientColors = listOf(
                        Color(0xFF0EA5E9),
                        Color(0xFF06B6D4)
                    ),
                    onClick = onNavigateToBluetoothAnalyzer
                )
            }

            // Hardware Status Section
            item {
                HardwareStatusSection(
                    nfcAvailable = uiState.isNfcAvailable,
                    nfcEnabled = uiState.isNfcEnabled,
                    bluetoothAvailable = uiState.isBluetoothAvailable,
                    bluetoothEnabled = uiState.isBluetoothEnabled,
                    bleSupported = uiState.isBleSupported
                )
            }
        }
    }
}

@Composable
private fun HardwareHeroSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(28.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                        )
                    )
                )
                .padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Memory,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(20.dp))

                Column {
                    Text(
                        text = "Hardware Tools",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Interact with device hardware components",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun HardwareFeatureCard(
    icon: ImageVector,
    title: String,
    description: String,
    isAvailable: Boolean,
    isEnabled: Boolean,
    statusText: String,
    additionalInfo: String? = null,
    gradientColors: List<Color>,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isAvailable && isEnabled) 1f else 0.98f,
        animationSpec = tween(200),
        label = "scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(enabled = isAvailable) { onClick() },
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isAvailable && isEnabled) 4.dp else 1.dp
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column {
            // Gradient header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = if (isAvailable && isEnabled) {
                                gradientColors
                            } else {
                                listOf(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = if (isAvailable && isEnabled) {
                        Color.White
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    }
                )
            }

            // Content
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    // Status chip
                    StatusChip(
                        text = statusText,
                        isPositive = isAvailable && isEnabled
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                additionalInfo?.let { info ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = info,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (isAvailable) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Open",
                            tint = if (isEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(
    text: String,
    isPositive: Boolean
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isPositive) {
                    Color(0xFF4CAF50).copy(alpha = 0.15f)
                } else {
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                }
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                if (isPositive) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (isPositive) {
                    Color(0xFF4CAF50)
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = if (isPositive) {
                    Color(0xFF4CAF50)
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
    }
}

@Composable
private fun HardwareStatusSection(
    nfcAvailable: Boolean,
    nfcEnabled: Boolean,
    bluetoothAvailable: Boolean,
    bluetoothEnabled: Boolean,
    bleSupported: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Sensors,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Hardware Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            HardwareStatusRow("NFC Hardware", nfcAvailable)
            HardwareStatusRow("NFC Enabled", nfcEnabled)
            HardwareStatusRow("Bluetooth Hardware", bluetoothAvailable)
            HardwareStatusRow("Bluetooth Enabled", bluetoothEnabled)
            HardwareStatusRow("BLE Support", bleSupported)
        }
    }
}

@Composable
private fun HardwareStatusRow(
    label: String,
    isActive: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (isActive) Color(0xFF4CAF50) else Color(0xFFBDBDBD)
                )
        )
    }
}
