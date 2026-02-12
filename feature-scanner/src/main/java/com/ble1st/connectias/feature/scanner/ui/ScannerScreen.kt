package com.ble1st.connectias.feature.scanner.ui
import androidx.compose.material.icons.automirrored.filled.RotateRight

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Scanner
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ble1st.connectias.feature.scanner.data.ScanSource
import com.ble1st.connectias.feature.scanner.data.ScannerDevice
import com.ble1st.connectias.feature.scanner.domain.EnhancementMode
import com.ble1st.connectias.feature.scanner.utils.GmsUtils
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    rememberCoroutineScope()
    val isGmsAvailable = remember(context) { GmsUtils.isGmsAvailable(context) }
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedPageModel by remember { mutableStateOf<Int?>(null) }

    // Fallback Camera Launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempPhotoUri != null) {
            val bitmap = try {
                context.contentResolver.openInputStream(tempPhotoUri!!)?.use { 
                    BitmapFactory.decodeStream(it)
                }
            } catch (e: Exception) { null }
            
            if (bitmap != null) {
                viewModel.onPagesCaptured(listOf(bitmap))
            }
        }
    }

    // Permission launcher for Camera
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted, launch camera
            try {
                val file = File(context.cacheDir, "scan_tmp_${System.currentTimeMillis()}.jpg")
                val uri = FileProvider.getUriForFile(
                    context,
                    "com.ble1st.connectias.feature.scanner.fileprovider",
                    file
                )
                tempPhotoUri = uri
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                viewModel.setError("Camera Error: ${e.message}")
            }
        } else {
            // Permission denied
            viewModel.setError("Camera permission denied. Cannot use camera.")
        }
    }

    // Storage Picker Launcher
    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        uri?.let { viewModel.saveDocumentToUri(it, context) }
    }

    // ML Kit removed - using camera fallback only

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Scanner & OCR", style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    IconButton(onClick = { viewModel.startDiscovery() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Scanners")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Action Buttons Row
            item(key = "action_buttons") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                FilledTonalButton(
                    onClick = {
                        // ML Kit removed - using camera only
                        when {
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.CAMERA
                                ) == PackageManager.PERMISSION_GRANTED -> {
                                    // Permission already granted, launch camera
                                    try {
                                        val file = File(context.cacheDir, "scan_tmp_${System.currentTimeMillis()}.jpg")
                                        val uri = FileProvider.getUriForFile(
                                            context,
                                            "com.ble1st.connectias.feature.scanner.fileprovider",
                                            file
                                        )
                                        tempPhotoUri = uri
                                        cameraLauncher.launch(uri)
                                    } catch (e: Exception) {
                                        viewModel.setError("Camera Error: ${e.message}")
                                    }
                                }
                            else -> {
                                // Request permission
                                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Camera Scan")
                }
                
                Button(
                    onClick = { 
                        saveLauncher.launch("Scan_${System.currentTimeMillis()}.pdf")
                    },
                    enabled = uiState.processedPages.isNotEmpty(),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save PDF")
                }
            }
            }

            // Error Display
            if (uiState.error != null) {
                item(key = "error_display") {
                    ElevatedCard(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = "Error", tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = uiState.error!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearError() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
                }
            }

            // Success Display
            if (uiState.savedFilePath != null) {
                item(key = "success_display") {
                    ElevatedCard(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Success", tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                             Text("Document Saved", style = MaterialTheme.typography.titleMedium)
                             Text(uiState.savedFilePath!!, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                }
            }

            // Network Scanner Selection
            if (uiState.devices.isNotEmpty()) {
                item(key = "device_selector") {
                    DeviceSelector(
                    devices = uiState.devices,
                    selectedDevice = uiState.selectedDevice,
                    onDeviceSelected = viewModel::selectDevice
                )
                }
            } else if (uiState.isDiscoveryActive) {
                item(key = "discovery_progress") {
                    Column {
                    LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
                    Text("Searching for printers...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Network Scan Controls
            if (uiState.selectedDevice != null) {
                item(key = "scan_mode_selector") {
                    ScanModeSelector(
                    currentSource = uiState.scanSource,
                    onSourceSelected = viewModel::setScanSource
                )
                }
                item(key = "start_scan_button") {
                    Button(
                    onClick = { viewModel.startScan() },
                    enabled = !uiState.isScanning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (uiState.isScanning) Text("Scanning via Network...") else Text("Start Network Scan")
                }
                }
            }
            
            // Image Enhancement Controls
            if (uiState.processedPages.isNotEmpty()) {
                item(key = "enhancement_title") {
                    Text("Enhancement", style = MaterialTheme.typography.titleLarge)
                }
                item(key = "enhancement_selector") {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        EnhancementMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = uiState.enhancementMode == mode,
                                onClick = { viewModel.setEnhancementMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = EnhancementMode.entries.size),
                                modifier = Modifier.weight(1f),
                                label = { 
                                    Text(
                                        text = mode.name.take(1) + mode.name.drop(1).lowercase().replace("_", " "),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall
                                    ) 
                                }
                            )
                        }
                    }
                }

                // Preview
                item(key = "pages_title") {
                    Text("Pages", style = MaterialTheme.typography.headlineSmall)
                }
                item(key = "pages_preview") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        itemsIndexed(uiState.processedPages) { index, bitmap ->
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Scanned Page",
                                modifier = Modifier
                                    .height(320.dp)
                                    .aspectRatio(0.7f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                                    .clickable { selectedPageModel = index },
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }

        // Full Screen Image Dialog
        if (selectedPageModel != null && selectedPageModel!! < uiState.processedPages.size) {
            val pageIndex = selectedPageModel!!
            val bitmap = uiState.processedPages[pageIndex]

            Dialog(onDismissRequest = { }) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Black,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Full Screen Scan",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            contentScale = ContentScale.Fit
                        )

                        // Close Button
                        IconButton(
                            onClick = { },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50))
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }

                        // Action Buttons Row
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(32.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Rotate Button
                            IconButton(
                                onClick = { viewModel.rotatePage(pageIndex) },
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50))
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.RotateRight,
                                    contentDescription = "Rotate",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            
                            // Delete Button
                            IconButton(
                                onClick = { 
                                    viewModel.deletePage(pageIndex)
                                },
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(50))
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceSelector(
    devices: List<ScannerDevice>,
    selectedDevice: ScannerDevice?,
    onDeviceSelected: (ScannerDevice) -> Unit
) {
    Text("Select Network Scanner", style = MaterialTheme.typography.titleLarge)
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        LazyColumn {
            items(devices) { device ->
                val isSelected = device == selectedDevice
                ListItem(
                    headlineContent = { Text(device.name) },
                    supportingContent = { Text(device.address) },
                    leadingContent = { 
                        Icon(
                            Icons.Default.Print, 
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        ) 
                    },
                    modifier = Modifier
                        .clickable { onDeviceSelected(device) }
                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent),
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanModeSelector(
    currentSource: ScanSource,
    onSourceSelected: (ScanSource) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = currentSource == ScanSource.FLATBED,
            onClick = { onSourceSelected(ScanSource.FLATBED) },
            label = { Text("Flatbed") },
            leadingIcon = { Icon(Icons.Default.Scanner, null) }
        )
        FilterChip(
            selected = currentSource == ScanSource.ADF,
            onClick = { onSourceSelected(ScanSource.ADF) },
            label = { Text("ADF") },
            leadingIcon = { Icon(Icons.Default.Description, null) }
        )
    }
}
