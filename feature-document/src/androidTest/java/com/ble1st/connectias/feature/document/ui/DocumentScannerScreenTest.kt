package com.ble1st.connectias.feature.document.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import org.junit.Rule
import org.junit.Test

class DocumentScannerScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun headerAndEmptyStateAreVisible() {
        composeRule.setContent {
            ConnectiasTheme {
                DocumentScannerScreen(
                    state = DocumentScannerUiState(
                        pages = emptyList(),
                        ocrPages = emptyList(),
                        status = DocumentStatus.Idle,
                        mode = DocumentMode.SCAN_TO_PDF
                    ),
                    onCapture = {},
                    onImport = {},
                    onRunOcr = {},
                    onExportPdf = {},
                    onCopyText = {},
                    onClearAll = {},
                    onRemovePage = {},
                    onModeChanged = {},
                    onDismissStatus = {}
                )
            }
        }

        composeRule.onNodeWithText("Dokumente scannen & OCR").assertIsDisplayed()
        composeRule.onNodeWithText("Seiten (0)").assertIsDisplayed()
        composeRule.onNodeWithText("Füge eine Seite über Kamera oder Galerie hinzu.").assertIsDisplayed()
    }
}
