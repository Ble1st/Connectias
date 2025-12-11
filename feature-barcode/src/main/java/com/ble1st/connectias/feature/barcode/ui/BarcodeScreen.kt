package com.ble1st.connectias.feature.barcode.ui

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.feature.barcode.R
import com.ble1st.connectias.feature.barcode.data.BarcodeAnalyzer
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

    LaunchedEffect(cameraPermission.status.isGranted) {
        viewModel.setCameraPermission(cameraPermission.status.isGranted)
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
                    ScanSection(uiState = uiState, onCodesDetected = viewModel::onCodesDetected)
                }

                GenerateSection(
                    uiState = uiState,
                    onInputChange = viewModel::onGeneratorInputChanged,
                    onBatchChange = viewModel::onBatchInputChanged,
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
    onCodesDetected: (List<String>) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(R.string.barcode_scan_title), style = MaterialTheme.typography.titleMedium)
            BarcodeCameraPreview(onCodesDetected = onCodesDetected)
            if (uiState.scannedCodes.isNotEmpty()) {
                Text(text = stringResource(R.string.barcode_scan_results), style = MaterialTheme.typography.labelLarge)
                uiState.scannedCodes.forEach { code ->
                    Text(text = code, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun BarcodeCameraPreview(
    onCodesDetected: (List<String>) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val previewView = remember { PreviewView(context) }
    var cameraProvider: ProcessCameraProvider? by remember { mutableStateOf(null) }

    DisposableEffect(Unit) {
        onDispose {
            cameraProvider?.unbindAll()
        }
    }

    LaunchedEffect(cameraProviderFuture) {
        cameraProvider = cameraProviderFuture.get()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
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
            cameraProvider?.unbindAll()
            cameraProvider?.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
        } catch (t: Throwable) {
            // Swallow binding issues but log for debugging
        }
    }

    androidx.compose.ui.viewinterop.AndroidView(
        factory = { previewView },
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
    )
}

@Composable
private fun GenerateSection(
    uiState: BarcodeUiState,
    onInputChange: (String) -> Unit,
    onBatchChange: (String) -> Unit,
    onGenerateSingle: () -> Unit,
    onGenerateBatch: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = stringResource(R.string.barcode_generate_title), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = uiState.generatorInput,
                onValueChange = onInputChange,
                label = { Text(stringResource(R.string.barcode_single_label)) },
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = onGenerateSingle) {
                Text(stringResource(R.string.barcode_generate_single))
            }
            OutlinedTextField(
                value = uiState.batchInput,
                onValueChange = onBatchChange,
                label = { Text(stringResource(R.string.barcode_batch_label)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
            Button(onClick = onGenerateBatch) {
                Text(stringResource(R.string.barcode_generate_batch))
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
