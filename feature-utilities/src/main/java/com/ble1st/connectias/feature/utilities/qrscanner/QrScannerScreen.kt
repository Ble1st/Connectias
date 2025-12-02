package com.ble1st.connectias.feature.utilities.qrscanner

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.ble1st.connectias.feature.utilities.qrscanner.models.ParsedContent
import com.ble1st.connectias.feature.utilities.qrscanner.models.ScanHistoryEntry
import com.ble1st.connectias.feature.utilities.qrscanner.models.ScanResult
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * QR Scanner screen with camera preview and scan result handling.
 */
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    viewModel: QrScannerViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val uiState by viewModel.uiState.collectAsState()
    val scanHistory by viewModel.scanHistory.collectAsState()

    var showHistory by remember { mutableStateOf(false) }
    val historySheetState = rememberModalBottomSheetState()

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.scanFromGallery(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QR Scanner") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleFlash() }) {
                        Icon(
                            if (uiState.isFlashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Toggle Flash"
                        )
                    }
                    IconButton(onClick = { galleryLauncher.launch("image/*") }) {
                        Icon(Icons.Default.Image, contentDescription = "Gallery")
                    }
                    IconButton(onClick = { showHistory = true }) {
                        Icon(Icons.Default.History, contentDescription = "History")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                !cameraPermissionState.status.isGranted -> {
                    CameraPermissionRequest(
                        shouldShowRationale = cameraPermissionState.status.shouldShowRationale,
                        onRequestPermission = { cameraPermissionState.launchPermissionRequest() },
                        onOpenSettings = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }
                    )
                }

                else -> {
                    CameraPreview(
                        isFlashEnabled = uiState.isFlashEnabled,
                        onImageCaptured = { imageProxy ->
                            @androidx.camera.core.ExperimentalGetImage
                            viewModel.processImage(imageProxy)
                        }
                    )

                    // Scan overlay
                    ScanOverlay()

                    // Result card
                    AnimatedVisibility(
                        visible = uiState.showResult,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut(),
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        uiState.lastScanResult?.let { result ->
                            ScanResultCard(
                                result = result,
                                onAction = { action ->
                                    handleAction(context, action)
                                },
                                onDismiss = { viewModel.resumeScanning() }
                            )
                        }
                    }
                }
            }

            // Error snackbar
            uiState.error?.let { error ->
                LaunchedEffect(error) {
                    Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                    viewModel.clearError()
                }
            }
        }

        // History bottom sheet
        if (showHistory) {
            ModalBottomSheet(
                onDismissRequest = { showHistory = false },
                sheetState = historySheetState
            ) {
                ScanHistorySheet(
                    history = scanHistory,
                    onItemClick = { entry ->
                        scope.launch {
                            historySheetState.hide()
                            showHistory = false
                        }
                    },
                    onClear = { viewModel.clearHistory() }
                )
            }
        }
    }
}

@Composable
private fun CameraPreview(
    isFlashEnabled: Boolean,
    onImageCaptured: (androidx.camera.core.ImageProxy) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    LaunchedEffect(isFlashEnabled) {
        camera?.cameraControl?.enableTorch(isFlashEnabled)
    }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { previewView ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor) { imageProxy ->
                            onImageCaptured(imageProxy)
                        }
                    }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    // Handle error
                }
            }, ContextCompat.getMainExecutor(context))
        }
    )
}

@Composable
private fun ScanOverlay() {
    Box(modifier = Modifier.fillMaxSize()) {
        // Semi-transparent overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
        )

        // Scanning area indicator
        Box(
            modifier = Modifier
                .size(250.dp)
                .align(Alignment.Center)
                .border(
                    width = 3.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(16.dp)
                )
                .background(Color.Transparent)
        )

        // Instructions
        Text(
            text = "Point camera at QR code",
            color = Color.White,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 300.dp)
        )
    }
}

@Composable
private fun ScanResultCard(
    result: ScanResult.Success,
    onAction: (ScanResultAction) -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.QrCodeScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = result.format.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Content preview
            Text(
                text = result.rawValue,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons based on content type
            ScanResultActions(
                content = result.parsedContent,
                rawValue = result.rawValue,
                onAction = onAction
            )
        }
    }
}

@Composable
private fun ScanResultActions(
    content: ParsedContent,
    rawValue: String,
    onAction: (ScanResultAction) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Copy button (always available)
        OutlinedButton(
            onClick = { onAction(ScanResultAction.CopyToClipboard(rawValue)) },
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Copy")
        }

        // Share button (always available)
        OutlinedButton(
            onClick = { onAction(ScanResultAction.Share(rawValue)) },
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Share")
        }

        // Context-specific action
        when (content) {
            is ParsedContent.Url -> {
                Button(
                    onClick = { onAction(ScanResultAction.OpenUrl(content.url)) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Open")
                }
            }

            is ParsedContent.Wifi -> {
                Button(
                    onClick = {
                        onAction(
                            ScanResultAction.ConnectWifi(
                                content.ssid,
                                content.password,
                                content.encryptionType.name
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Connect")
                }
            }

            else -> {
                // No specific action
            }
        }
    }
}

@Composable
private fun CameraPermissionRequest(
    shouldShowRationale: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.QrCodeScanner,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Camera Permission Required",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (shouldShowRationale) {
                "Camera access is needed to scan QR codes and barcodes. Please grant the permission."
            } else {
                "Camera permission was denied. Please enable it in Settings to use the scanner."
            },
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (shouldShowRationale) {
            Button(onClick = onRequestPermission) {
                Text("Grant Permission")
            }
        } else {
            Button(onClick = onOpenSettings) {
                Text("Open Settings")
            }
        }
    }
}

@Composable
private fun ScanHistorySheet(
    history: List<ScanHistoryEntry>,
    onItemClick: (ScanHistoryEntry) -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Scan History",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (history.isNotEmpty()) {
                OutlinedButton(onClick = onClear) {
                    Text("Clear")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (history.isEmpty()) {
            Text(
                text = "No scans yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 32.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.height(400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(history) { entry ->
                    HistoryItem(
                        entry = entry,
                        onClick = { onItemClick(entry) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(
    entry: ScanHistoryEntry,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.rawValue,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${entry.contentType} • ${formatTimestamp(entry.timestamp)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val formatter = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

private fun handleAction(context: Context, action: ScanResultAction) {
    when (action) {
        is ScanResultAction.OpenUrl -> {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(action.url))
            context.startActivity(intent)
        }

        is ScanResultAction.CopyToClipboard -> {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Scan Result", action.text))
            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }

        is ScanResultAction.Share -> {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, action.text)
            }
            context.startActivity(Intent.createChooser(intent, "Share"))
        }

        is ScanResultAction.ConnectWifi -> {
            Toast.makeText(
                context,
                "WiFi: ${action.ssid}\nPassword: ${action.password ?: "Open network"}",
                Toast.LENGTH_LONG
            ).show()
        }

        is ScanResultAction.DialPhone -> {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${action.number}"))
            context.startActivity(intent)
        }

        is ScanResultAction.SendEmail -> {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:${action.address}")
                action.subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
                action.body?.let { putExtra(Intent.EXTRA_TEXT, it) }
            }
            context.startActivity(intent)
        }

        is ScanResultAction.SendSms -> {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:${action.number}")
                action.message?.let { putExtra("sms_body", it) }
            }
            context.startActivity(intent)
        }

        is ScanResultAction.OpenMap -> {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("geo:${action.latitude},${action.longitude}")
            )
            context.startActivity(intent)
        }

        is ScanResultAction.AddContact -> {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                type = ContactsContract.Contacts.CONTENT_TYPE
                action.content.name?.let {
                    putExtra(ContactsContract.Intents.Insert.NAME, it)
                }
                action.content.phones.firstOrNull()?.let {
                    putExtra(ContactsContract.Intents.Insert.PHONE, it.number)
                }
                action.content.emails.firstOrNull()?.let {
                    putExtra(ContactsContract.Intents.Insert.EMAIL, it.address)
                }
            }
            context.startActivity(intent)
        }

        is ScanResultAction.AddCalendarEvent -> {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                data = android.provider.CalendarContract.Events.CONTENT_URI
                action.content.summary?.let {
                    putExtra(android.provider.CalendarContract.Events.TITLE, it)
                }
                action.content.description?.let {
                    putExtra(android.provider.CalendarContract.Events.DESCRIPTION, it)
                }
                action.content.location?.let {
                    putExtra(android.provider.CalendarContract.Events.EVENT_LOCATION, it)
                }
                action.content.start?.let {
                    putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, it)
                }
                action.content.end?.let {
                    putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, it)
                }
            }
            context.startActivity(intent)
        }

        is ScanResultAction.SearchProduct -> {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/search?q=${action.code}")
            )
            context.startActivity(intent)
        }
    }
}
