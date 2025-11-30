package com.ble1st.connectias.feature.utilities.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun UtilitiesDashboardScreen(
    onNavigateToHash: () -> Unit,
    onNavigateToEncoding: () -> Unit,
    onNavigateToQrCode: () -> Unit,
    onNavigateToText: () -> Unit,
    onNavigateToColor: () -> Unit,
    onNavigateToLog: () -> Unit,
    onNavigateToApiTester: () -> Unit,
    onNavigateToUsb: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Utilities",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        UtilityCard(
            title = "Hash Calculator",
            description = "Calculate and verify MD5, SHA-1, SHA-256, SHA-512 hashes",
            icon = Icons.Default.Fingerprint,
            onClick = onNavigateToHash
        )

        UtilityCard(
            title = "Encoding Tools",
            description = "Encode/decode Base64, Base32, Hex, URL, HTML Entity, Unicode",
            icon = Icons.Default.Code,
            onClick = onNavigateToEncoding
        )

        UtilityCard(
            title = "QR Code Generator",
            description = "Generate and scan QR codes for text, WiFi, contacts, URLs",
            icon = Icons.Default.QrCode,
            onClick = onNavigateToQrCode
        )

        UtilityCard(
            title = "Text Utilities",
            description = "Text case conversion, word/char counting, regex testing, JSON formatting",
            icon = Icons.Default.TextFields,
            onClick = onNavigateToText
        )

        UtilityCard(
            title = "Color Picker",
            description = "Pick and convert colors (Hex, RGB, CMYK)",
            icon = Icons.Default.Palette,
            onClick = onNavigateToColor
        )

        UtilityCard(
            title = "Log Viewer",
            description = "View and export application logs",
            icon = Icons.Default.ListAlt,
            onClick = onNavigateToLog
        )

        UtilityCard(
            title = "API Tester",
            description = "Test REST APIs with customizable requests",
            icon = Icons.Default.Http,
            onClick = onNavigateToApiTester
        )

        UtilityCard(
            title = "USB & DVD/CD",
            description = "USB device management and DVD/CD playback",
            icon = Icons.Default.Usb,
            onClick = onNavigateToUsb
        )
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun UtilityCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text(description, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
