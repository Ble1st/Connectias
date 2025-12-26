package com.ble1st.connectias.feature.dvd.ui.components
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.feature.dvd.models.FileInfo
import timber.log.Timber

@Composable
fun FileBrowser(
    files: List<FileInfo>,
    onFileClick: (FileInfo) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (files.isEmpty()) {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No files found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            files.forEach { file ->
                FileItem(
                    file = file,
                    onClick = { onFileClick(file) }
                )
            }
        }
    }
}

@Composable
private fun FileItem(
    file: FileInfo,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = if (file.isDirectory) "Folder" else "File",
                tint = MaterialTheme.colorScheme.primary
            )
            
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleMedium
                )
                if (!file.isDirectory) {
                    Text(
                        text = formatFileSize(file.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    // Negative file sizes should be caught at FileInfo creation (FileSystemReader),
    // but handle defensively here as well. Negative values typically indicate:
    // - Special files (device files, pipes) that don't have a meaningful size
    // - File system errors or permission issues
    // - Edge cases on certain Android versions or file systems
    if (bytes < 0) {
        Timber.w("formatFileSize received negative size: $bytes, returning 'Unknown size'")
        return "Unknown size"
    }
    
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    
    return when {
        gb >= 1 -> "%.2f GB".format(gb)
        mb >= 1 -> "%.2f MB".format(mb)
        kb >= 1 -> "%.2f KB".format(kb)
        else -> "$bytes bytes"
    }
}
