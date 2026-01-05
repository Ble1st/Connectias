package com.ble1st.connectias.feature.barcode.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.VibrationEffect
import android.os.VibratorManager
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.feature.barcode.R
import com.ble1st.connectias.feature.barcode.data.BarcodeAnalyzer
import com.ble1st.connectias.feature.barcode.data.BarcodeType
import com.ble1st.connectias.feature.barcode.data.ScannedBarcode
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun BarcodeScreen(
    viewModel: BarcodeViewModel
) {
    val uiState by viewModel.state.collectAsState()
    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)
    val context = LocalContext.current

    LaunchedEffect(cameraPermission.status.isGranted) {
        viewModel.setCameraPermission(cameraPermission.status.isGranted)
    }

    // Vibration Logic
    LaunchedEffect(uiState.scannedCodes.size) {
        if (uiState.scannedCodes.isNotEmpty()) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val vibrator = vibratorManager.defaultVibrator

            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    ConnectiasTheme {
        Scaffold { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(text = stringResource(R.string.barcode_title), style = MaterialTheme.typography.headlineSmall)

                if (!uiState.hasCameraPermission) {
                    Button(onClick = { cameraPermission.launchPermissionRequest() }) {
                        Text(stringResource(R.string.barcode_request_permission))
                    }
                } else {
                    ScanSection(
                        uiState = uiState,
                        onCodesDetected = viewModel::onCodesDetected,
                        onToggleTorch = viewModel::toggleTorch,
                        onZoomChange = viewModel::setZoom,
                        onMaxZoomChange = viewModel::setMaxZoom,
                        onClearHistory = viewModel::clearHistory
                    )
                }

                GenerateSection(
                    uiState = uiState,
                    onInputChange = viewModel::onGeneratorInputChanged,
                    onBatchChange = viewModel::onBatchInputChanged,
                    onWifiSsidChange = viewModel::updateWifiSsid,
                    onWifiPasswordChange = viewModel::updateWifiPassword,
                    onContactNameChange = viewModel::updateContactName,
                    onContactPhoneChange = viewModel::updateContactPhone,
                    onGeneratorTypeSelected = viewModel::setGeneratorType,
                    onGenerateSingle = viewModel::generateSingle,
                    onGenerateBatch = viewModel::generateBatch
                )

                if (uiState.errorMessage != null) {
                    Text(text = uiState.errorMessage ?: "", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun ScanSection(
    uiState: BarcodeUiState,
    onCodesDetected: (List<String>) -> Unit,
    onToggleTorch: () -> Unit,
    onZoomChange: (Float) -> Unit,
    onMaxZoomChange: (Float) -> Unit,
    onClearHistory: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(R.string.barcode_scan_title), style = MaterialTheme.typography.titleMedium)
            
            Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                BarcodeCameraPreview(
                    onCodesDetected = onCodesDetected,
                    isTorchOn = uiState.isTorchOn,
                    zoomRatio = uiState.zoomRatio,
                    onMaxZoomChanged = onMaxZoomChange
                )
                
                // Camera Controls Overlay
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.5f), shape = MaterialTheme.shapes.medium)
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(
                        onClick = onToggleTorch,
                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.White)
                    ) {
                        Icon(
                            imageVector = if (uiState.isTorchOn) Icons.Default.FlashOff else Icons.Default.FlashOn,
                            contentDescription = "Toggle Torch"
                        )
                    }
                }
                
                // Zoom Slider at bottom center
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp, start = 8.dp, end = 60.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("1x", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = uiState.zoomRatio,
                        onValueChange = onZoomChange,
                        valueRange = 1f..uiState.maxZoomRatio,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.5f)
                        )
                    )
                    Text("${uiState.maxZoomRatio.toInt()}x", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
            
            if (uiState.scannedCodes.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = stringResource(R.string.barcode_scan_results), style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = onClearHistory) {
                        Text("Clear History")
                    }
                }
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.scannedCodes.reversed().forEach { code ->
                        ScannedCodeItem(code = code)
                    }
                }
            }
        }
    }
}

@Composable
private fun BarcodeCameraPreview(
    onCodesDetected: (List<String>) -> Unit,
    isTorchOn: Boolean,
    zoomRatio: Float,
    onMaxZoomChanged: (Float) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val previewView = remember { PreviewView(context) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    // React to Torch State
    LaunchedEffect(camera, isTorchOn) {
        try {
            camera?.cameraControl?.enableTorch(isTorchOn)
        } catch (e: Exception) {
            // Ignore errors
        }
    }

    // React to Zoom State
    LaunchedEffect(camera, zoomRatio) {
        try {
            camera?.cameraControl?.setZoomRatio(zoomRatio)
        } catch (e: Exception) {
            // Ignore errors
        }
    }

    LaunchedEffect(cameraProviderFuture) {
        val cameraProvider = cameraProviderFuture.get()
        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }
        val selector = CameraSelector.DEFAULT_BACK_CAMERA
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(
                    ContextCompat.getMainExecutor(context),
                    BarcodeAnalyzer(onCodesDetected)
                )
            }
        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
            
            // Observe Zoom State
            camera?.cameraInfo?.zoomState?.observe(lifecycleOwner) { state ->
                onMaxZoomChanged(state.maxZoomRatio)
            }
        } catch (t: Throwable) {
            // Swallow binding issues but log for debugging
        }
    }

    androidx.compose.ui.viewinterop.AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun ScannedCodeItem(code: ScannedBarcode) {
    val context = LocalContext.current
    
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (code.type) {
                        BarcodeType.URL -> Icons.Default.Link
                        BarcodeType.WIFI -> Icons.Default.Wifi
                        else -> Icons.Default.ContentCopy
                    },
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = code.type.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = code.content, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { copyToClipboard(context, code.content) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Copy")
                }
                
                if (code.type == BarcodeType.URL) {
                    Button(
                        onClick = { openUrl(context, code.content) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open")
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Scanned Barcode", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}

private fun openUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun GenerateSection(
    uiState: BarcodeUiState,
    onInputChange: (String) -> Unit,
    onBatchChange: (String) -> Unit,
    onWifiSsidChange: (String) -> Unit,
    onWifiPasswordChange: (String) -> Unit,
    onContactNameChange: (String) -> Unit,
    onContactPhoneChange: (String) -> Unit,
    onGeneratorTypeSelected: (GeneratorType) -> Unit,
    onGenerateSingle: () -> Unit,
    onGenerateBatch: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = stringResource(R.string.barcode_generate_title), style = MaterialTheme.typography.titleMedium)
            
            // Tabs
            PrimaryTabRow(selectedTabIndex = uiState.selectedGeneratorType.ordinal) {
                GeneratorType.entries.forEach { type ->
                    Tab(
                        selected = uiState.selectedGeneratorType == type,
                        onClick = { onGeneratorTypeSelected(type) },
                        text = { 
                            Text(
                                text = type.name.lowercase().capitalize(),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            ) 
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            when (uiState.selectedGeneratorType) {
                GeneratorType.TEXT -> {
                    OutlinedTextField(
                        value = uiState.generatorInput,
                        onValueChange = onInputChange,
                        label = { Text("Content") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = onGenerateSingle, modifier = Modifier.fillMaxWidth()) {
                        Text("Generate QR")
                    }
                }
                GeneratorType.WIFI -> {
                    OutlinedTextField(
                        value = uiState.wifiSsid,
                        onValueChange = onWifiSsidChange,
                        label = { Text("SSID (Network Name)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = uiState.wifiPassword,
                        onValueChange = onWifiPasswordChange,
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = onGenerateSingle, modifier = Modifier.fillMaxWidth()) {
                        Text("Generate WiFi QR")
                    }
                }
                GeneratorType.CONTACT -> {
                    OutlinedTextField(
                        value = uiState.contactName,
                        onValueChange = onContactNameChange,
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = uiState.contactPhone,
                        onValueChange = onContactPhoneChange,
                        label = { Text("Phone") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = onGenerateSingle, modifier = Modifier.fillMaxWidth()) {
                        Text("Generate Contact QR")
                    }
                }
                GeneratorType.BATCH -> {
                    OutlinedTextField(
                        value = uiState.batchInput,
                        onValueChange = onBatchChange,
                        label = { Text(stringResource(R.string.barcode_batch_label)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                    )
                    Button(onClick = onGenerateBatch, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.barcode_generate_batch))
                    }
                }
            }

            if (uiState.generatedCodes.isNotEmpty()) {
                Text(text = stringResource(R.string.barcode_generated_codes), style = MaterialTheme.typography.labelLarge)
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                ) {
                    items(uiState.generatedCodes) { code ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = code.content, style = MaterialTheme.typography.bodySmall)
                            Image(bitmap = code.bitmap, contentDescription = code.content, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

private fun String.capitalize(): String {
    return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
}
