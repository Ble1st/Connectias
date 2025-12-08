package com.ble1st.connectias.feature.document.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.feature.document.model.DocumentPage
import com.ble1st.connectias.feature.document.model.OcrPageResult

data class DocumentScannerUiState(
    val pages: List<DocumentPage>,
    val ocrPages: List<OcrPageResult?>,
    val status: DocumentStatus,
    val mode: DocumentMode
)

@Composable
fun DocumentScannerScreen(
    state: DocumentScannerUiState,
    onCapture: () -> Unit,
    onImport: () -> Unit,
    onRunOcr: () -> Unit,
    onExportPdf: () -> Unit,
    onCopyText: () -> Unit,
    onClearAll: () -> Unit,
    onRemovePage: (Int) -> Unit,
    onModeChanged: (DocumentMode) -> Unit,
    onDismissStatus: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Header(state.mode, onModeChanged)

            ActionRow(
                onCapture = onCapture,
                onImport = onImport,
                onRunOcr = onRunOcr,
                onExportPdf = onExportPdf,
                onCopyText = onCopyText
            )

            PageStrip(
                pages = state.pages,
                onRemovePage = onRemovePage
            )

            StatusCard(
                status = state.status,
                ocrCompleted = state.ocrPages.any { it != null },
                onDismiss = onDismissStatus,
                onClearAll = onClearAll
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun Header(mode: DocumentMode, onModeChanged: (DocumentMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Dokumente scannen & OCR",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        SingleChoiceSegmentedButtonRow {
            SegmentedButton(
                selected = mode == DocumentMode.SCAN_TO_PDF,
                onClick = { onModeChanged(DocumentMode.SCAN_TO_PDF) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) {
                Text("Scan → PDF")
            }
            SegmentedButton(
                selected = mode == DocumentMode.OCR_ONLY,
                onClick = { onModeChanged(DocumentMode.OCR_ONLY) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) {
                Text("Nur OCR")
            }
        }
    }
}

@Composable
private fun ActionRow(
    onCapture: () -> Unit,
    onImport: () -> Unit,
    onRunOcr: () -> Unit,
    onExportPdf: () -> Unit,
    onCopyText: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(
                icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                label = "Kamera",
                onClick = onCapture
            )
            ActionButton(
                icon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                label = "Galerie",
                onClick = onImport
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(
                icon = { Icon(Icons.Default.Description, contentDescription = null) },
                label = "OCR ausführen",
                onClick = onRunOcr
            )
            ActionButton(
                icon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) },
                label = "PDF speichern",
                onClick = onExportPdf
            )
            ActionButton(
                icon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                label = "Text kopieren",
                onClick = onCopyText
            )
        }
    }
}

@Composable
private fun PageStrip(
    pages: List<DocumentPage>,
    onRemovePage: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Seiten (${pages.size})",
            style = MaterialTheme.typography.titleMedium
        )
        if (pages.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = "Füge eine Seite über Kamera oder Galerie hinzu.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(pages) { index, page ->
                    Card(
                        modifier = Modifier
                            .size(width = 140.dp, height = 200.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                bitmap = page.bitmap.asImageBitmap(),
                                contentDescription = "Page preview",
                                modifier = Modifier
                                    .padding(8.dp)
                                    .weight(1f)
                            )
                            AssistChip(
                                onClick = { onRemovePage(index) },
                                label = { Text("Entfernen") },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                modifier = Modifier
                                    .padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    status: DocumentStatus,
    ocrCompleted: Boolean,
    onDismiss: () -> Unit,
    onClearAll: () -> Unit
) {
    val background = when (status) {
        is DocumentStatus.Error -> MaterialTheme.colorScheme.errorContainer
        is DocumentStatus.Success -> MaterialTheme.colorScheme.secondaryContainer
        DocumentStatus.OcrRunning,
        DocumentStatus.Exporting,
        DocumentStatus.Scanning -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (status) {
                DocumentStatus.Scanning -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                DocumentStatus.OcrRunning -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                DocumentStatus.Exporting -> CircularProgressIndicator(modifier = Modifier.size(24.dp))
                else -> {}
            }

            val text = when (status) {
                DocumentStatus.Idle -> if (ocrCompleted) "OCR abgeschlossen" else "Bereit"
                DocumentStatus.Scanning -> "Scanne & erkenne Ränder..."
                DocumentStatus.OcrRunning -> "OCR läuft..."
                DocumentStatus.Exporting -> "Exportiere PDF..."
                is DocumentStatus.Error -> status.message
                is DocumentStatus.Success -> status.message
            }
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onClearAll, label = { Text("Leeren") })
                AssistChip(onClick = onDismiss, label = { Text("OK") })
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit
) {
    Button(onClick = onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            icon()
            Text(label)
        }
    }
}
