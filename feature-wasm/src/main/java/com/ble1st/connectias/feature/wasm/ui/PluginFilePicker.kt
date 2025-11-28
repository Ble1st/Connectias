package com.ble1st.connectias.feature.wasm.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import java.io.File

/**
 * File picker for plugin ZIP files.
 */
@Composable
fun rememberPluginFilePicker(
    onFileSelected: (Uri) -> Unit
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onFileSelected(it) }
    }
    
    return {
        launcher.launch("application/zip")
    }
}

/**
 * Convert URI to File (simplified - use ContentResolver in production).
 */
fun Uri.toFile(context: Context): File? {
    return try {
        // This is a simplified implementation
        // In production, use ContentResolver to read the file
        File(path ?: return null)
    } catch (e: Exception) {
        null
    }
}

