package com.ble1st.connectias.feature.hardware.ui.nfc

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ble1st.connectias.feature.hardware.models.NdefRecordInfo
import com.ble1st.connectias.feature.hardware.models.NdefRecordType
import com.ble1st.connectias.feature.hardware.models.NfcTagInfo
import kotlinx.coroutines.launch

/**
 * NFC Tools screen for reading and writing NFC tags.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcToolsScreen(
    viewModel: NfcToolsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentTag by viewModel.currentTag.collectAsState()
    val isNfcEnabled by viewModel.isNfcEnabled.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showWriteSheet by remember { mutableStateOf(false) }
    val writeSheetState = rememberModalBottomSheetState()

    // Show snackbar messages
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            scope.launch {
                snackbarHostState.showSnackbar(message)
                viewModel.clearSnackbarMessage()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NFC Tools") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                !uiState.isNfcAvailable -> {
                    NfcNotAvailableState()
                }
                !isNfcEnabled -> {
                    NfcDisabledState()
                }
                currentTag != null -> {
                    TagDetailsContent(
                        tag = currentTag!!,
                        isWriting = uiState.isWriting,
                        onWrite = { showWriteSheet = true },
                        onFormat = { viewModel.formatTag() },
                        onMakeReadOnly = { viewModel.makeTagReadOnly() },
                        onClear = { viewModel.clearCurrentTag() }
                    )
                }
                else -> {
                    ScanningState(
                        isScanning = uiState.isScanning,
                        lastScannedTag = uiState.lastScannedTag
                    )
                }
            }
        }

        // Write bottom sheet
        if (showWriteSheet) {
            ModalBottomSheet(
                onDismissRequest = { showWriteSheet = false },
                sheetState = writeSheetState
            ) {
                WriteRecordSheet(
                    selectedType = uiState.selectedWriteType,
                    onTypeSelected = { viewModel.setSelectedRecordType(it) },
                    onWriteUri = { viewModel.writeUriRecord(it) },
                    onWriteText = { viewModel.writeTextRecord(it) },
                    onWriteWifi = { ssid, password, auth -> 
                        viewModel.writeWifiRecord(ssid, password, auth)
                    },
                    onWriteVCard = { name, phone, email, org ->
                        viewModel.writeVCardRecord(name, phone, email, org)
                    },
                    onDismiss = { showWriteSheet = false }
                )
            }
        }
    }
}

@Composable
private fun NfcNotAvailableState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Nfc,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "NFC Not Available",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "This device does not support NFC functionality",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NfcDisabledState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Nfc,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "NFC is Disabled",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Please enable NFC in your device settings to use this feature",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        FilledTonalButton(onClick = { /* Open NFC settings */ }) {
            Text("Open Settings")
        }
    }
}

@Composable
private fun ScanningState(
    isScanning: Boolean,
    lastScannedTag: NfcTagInfo?
) {
    val pulseScale by animateFloatAsState(
        targetValue = if (isScanning) 1.1f else 1f,
        animationSpec = tween(1000),
        label = "pulse"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hero scanning animation
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Animated rings
            if (isScanning) {
                repeat(3) { index ->
                    Box(
                        modifier = Modifier
                            .size((80 + index * 40).dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(
                                    alpha = 0.1f / (index + 1)
                                )
                            )
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(80.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 4.dp
                    )
                }
                Icon(
                    Icons.Default.Nfc,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = if (isScanning) "Ready to Scan" else "Hold Device Near NFC Tag",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Place your device near an NFC tag to read its contents",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        // Last scanned tag
        AnimatedVisibility(
            visible = lastScannedTag != null,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut()
        ) {
            lastScannedTag?.let { tag ->
                Spacer(modifier = Modifier.height(32.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Last Scanned Tag",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "ID: ${tag.id}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "Type: ${tag.type}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TagDetailsContent(
    tag: NfcTagInfo,
    isWriting: Boolean,
    onWrite: () -> Unit,
    onFormat: () -> Unit,
    onMakeReadOnly: () -> Unit,
    onClear: () -> Unit
) {
    var showMakeReadOnlyDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Tag info card
        item {
            TagInfoCard(tag = tag)
        }

        // NDEF Records
        if (tag.ndefRecords.isNotEmpty()) {
            item {
                Text(
                    text = "NDEF Records",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(tag.ndefRecords) { record ->
                NdefRecordCard(record = record)
            }
        }

        // Actions
        item {
            Text(
                text = "Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Write button
                if (tag.isWritable) {
                    Button(
                        onClick = onWrite,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isWriting
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Write to Tag")
                    }
                }

                // Format button
                if (tag.isWritable) {
                    FilledTonalButton(
                        onClick = onFormat,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isWriting
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Format Tag")
                    }
                }

                // Make read-only button
                if (tag.canMakeReadOnly && tag.isWritable) {
                    OutlinedButton(
                        onClick = { showMakeReadOnlyDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isWriting,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Make Read-Only (Permanent)")
                    }
                }

                // Clear button
                TextButton(
                    onClick = onClear,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan Another Tag")
                }
            }
        }
    }

    // Make read-only confirmation dialog
    if (showMakeReadOnlyDialog) {
        AlertDialog(
            onDismissRequest = { showMakeReadOnlyDialog = false },
            title = { Text("Make Tag Read-Only?") },
            text = {
                Text(
                    "This action is PERMANENT and cannot be undone. " +
                    "The tag will no longer be writable. Are you sure you want to continue?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onMakeReadOnly()
                        showMakeReadOnlyDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Make Read-Only")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMakeReadOnlyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun TagInfoCard(tag: NfcTagInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Nfc,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = tag.type.name.replace("_", " "),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "ID: ${tag.id}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TagInfoItem(
                    label = "Size",
                    value = "${tag.maxSize} bytes"
                )
                TagInfoItem(
                    label = "Writable",
                    value = if (tag.isWritable) "Yes" else "No"
                )
                TagInfoItem(
                    label = "Records",
                    value = tag.ndefRecords.size.toString()
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tech list
            Text(
                text = "Technologies",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                tag.techList.take(3).forEach { tech ->
                    FilterChip(
                        onClick = { },
                        label = { 
                            Text(
                                text = tech.substringAfterLast("."),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        selected = false
                    )
                }
                if (tag.techList.size > 3) {
                    FilterChip(
                        onClick = { },
                        label = { Text("+${tag.techList.size - 3}") },
                        selected = false
                    )
                }
            }
        }
    }
}

@Composable
private fun TagInfoItem(
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NdefRecordCard(record: NdefRecordInfo) {
    val icon = when (record.recordType) {
        NdefRecordType.URI -> Icons.Default.Link
        NdefRecordType.TEXT -> Icons.Default.TextFields
        else -> Icons.Default.TextFields
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.recordType.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = record.payload,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun WriteRecordSheet(
    selectedType: NdefRecordType,
    onTypeSelected: (NdefRecordType) -> Unit,
    onWriteUri: (String) -> Unit,
    onWriteText: (String) -> Unit,
    onWriteWifi: (String, String?, String) -> Unit,
    onWriteVCard: (String, String?, String?, String?) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "Write to Tag",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Type selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WriteTypeChip(
                icon = Icons.Default.Link,
                label = "URI",
                selected = selectedType == NdefRecordType.URI,
                onClick = { onTypeSelected(NdefRecordType.URI) }
            )
            WriteTypeChip(
                icon = Icons.Default.TextFields,
                label = "Text",
                selected = selectedType == NdefRecordType.TEXT,
                onClick = { onTypeSelected(NdefRecordType.TEXT) }
            )
            WriteTypeChip(
                icon = Icons.Default.Wifi,
                label = "WiFi",
                selected = selectedType == NdefRecordType.MIME,
                onClick = { onTypeSelected(NdefRecordType.MIME) }
            )
            WriteTypeChip(
                icon = Icons.Default.ContactPage,
                label = "vCard",
                selected = selectedType == NdefRecordType.EXTERNAL,
                onClick = { onTypeSelected(NdefRecordType.EXTERNAL) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Input fields based on selected type
        when (selectedType) {
            NdefRecordType.URI -> UriWriteForm(onWrite = onWriteUri)
            NdefRecordType.TEXT -> TextWriteForm(onWrite = onWriteText)
            NdefRecordType.MIME -> WifiWriteForm(onWrite = onWriteWifi)
            NdefRecordType.EXTERNAL -> VCardWriteForm(onWrite = onWriteVCard)
            else -> TextWriteForm(onWrite = onWriteText)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun WriteTypeChip(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        onClick = onClick,
        label = { Text(label) },
        selected = selected,
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    )
}

@Composable
private fun UriWriteForm(onWrite: (String) -> Unit) {
    var uri by remember { mutableStateOf("https://") }

    Column {
        OutlinedTextField(
            value = uri,
            onValueChange = { uri = it },
            label = { Text("URI") },
            placeholder = { Text("https://example.com") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onWrite(uri) },
            modifier = Modifier.fillMaxWidth(),
            enabled = uri.isNotBlank()
        ) {
            Text("Write URI")
        }
    }
}

@Composable
private fun TextWriteForm(onWrite: (String) -> Unit) {
    var text by remember { mutableStateOf("") }

    Column {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Text") },
            placeholder = { Text("Enter text to write") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onWrite(text) },
            modifier = Modifier.fillMaxWidth(),
            enabled = text.isNotBlank()
        ) {
            Text("Write Text")
        }
    }
}

@Composable
private fun WifiWriteForm(onWrite: (String, String?, String) -> Unit) {
    var ssid by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var authType by remember { mutableStateOf("WPA") }

    Column {
        OutlinedTextField(
            value = ssid,
            onValueChange = { ssid = it },
            label = { Text("Network Name (SSID)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("WPA", "WEP", "Open").forEach { type ->
                FilterChip(
                    onClick = { authType = type },
                    label = { Text(type) },
                    selected = authType == type
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onWrite(ssid, password.ifBlank { null }, authType) },
            modifier = Modifier.fillMaxWidth(),
            enabled = ssid.isNotBlank()
        ) {
            Text("Write WiFi Config")
        }
    }
}

@Composable
private fun VCardWriteForm(onWrite: (String, String?, String?, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var organization by remember { mutableStateOf("") }

    Column {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Full Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Phone (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = organization,
            onValueChange = { organization = it },
            label = { Text("Organization (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { 
                onWrite(
                    name, 
                    phone.ifBlank { null }, 
                    email.ifBlank { null },
                    organization.ifBlank { null }
                ) 
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = name.isNotBlank()
        ) {
            Text("Write Contact")
        }
    }
}

