package com.ble1st.connectias.feature.document.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.runtime.collectAsState
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.core.content.ContextCompat
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class DocumentScannerFragment : Fragment() {

    private val viewModel: DocumentScannerViewModel by viewModels()

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            bitmap?.let { viewModel.addPage(it) }
        }

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                runCatching {
                    requireContext().contentResolver.openInputStream(uri).use { input ->
                        if (input != null) {
                            val bmp = BitmapFactory.decodeStream(input)
                            viewModel.addPage(bmp)
                        }
                    }
                }.onFailure { Timber.e(it, "Failed to load image from gallery") }
            }
        }

    private val createPdfLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
            if (uri != null) {
                viewModel.exportPdf(requireContext(), uri, includeTextLayer = true)
            }
        }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                takePictureLauncher.launch(null)
            } else {
                Toast.makeText(requireContext(), "Kamerazugriff benötigt", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (view as ComposeView).setContent {
            ConnectiasTheme {
                val pages by viewModel.pages.collectAsState()
                val ocrPages by viewModel.ocrPages.collectAsState()
                val status by viewModel.status.collectAsState()
                val mode by viewModel.mode.collectAsState()

                DocumentScannerScreen(
                    state = DocumentScannerUiState(
                        pages = pages,
                        ocrPages = ocrPages,
                        status = status,
                        mode = mode
                    ),
                    onCapture = { handleCapture() },
                    onImport = { pickImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onRunOcr = { viewModel.runOcr() },
                    onExportPdf = { createPdfLauncher.launch("scan.pdf") },
                    onCopyText = { copyOcrToClipboard() },
                    onClearAll = { viewModel.clearAll() },
                    onRemovePage = { index -> viewModel.removePage(index) },
                    onModeChanged = { mode -> viewModel.setMode(mode) },
                    onDismissStatus = { viewModel.clearStatus() }
                )
            }
        }
    }

    private fun copyOcrToClipboard() {
        val text = viewModel.combinedOcrText()
        if (text.isBlank()) {
            Toast.makeText(requireContext(), "Kein OCR-Text vorhanden", Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("OCR", text))
        Toast.makeText(requireContext(), "Text kopiert", Toast.LENGTH_SHORT).show()
    }

    private fun handleCapture() {
        val hasPermission = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            takePictureLauncher.launch(null)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}
