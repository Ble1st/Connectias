package com.ble1st.connectias.feature.deviceinfo.storage

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.common.ui.strings.getThemedString
import com.ble1st.connectias.feature.deviceinfo.R
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun StorageAnalyzerScreen(
    state: StorageState,
    onRefresh: () -> Unit,
    onFindLargeFiles: (Int) -> Unit
) {
    var minSizeText by remember { mutableStateOf("10") }
    
    Box(modifier = Modifier.fillMaxSize()) {
        if (state is StorageState.Loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = getThemedString(stringResource(R.string.storage_analyzer_title)),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    
                    Button(
                        onClick = onRefresh,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(getThemedString(stringResource(R.string.refresh_storage_info)))
                    }
                }

                if (state is StorageState.Info) {
                    item {
                        StorageCard(
                            title = getThemedString(stringResource(R.string.internal_storage)),
                            storageInfo = state.info.internalStorage
                        )
                    }
                    
                    if (state.info.externalStorage != null) {
                         item {
                            StorageCard(
                                title = getThemedString(stringResource(R.string.external_storage)),
                                storageInfo = state.info.externalStorage!!
                            )
                        }
                    }
                }

                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                         Column(modifier = Modifier.padding(16.dp)) {
                             Text(getThemedString(stringResource(R.string.find_large_files)), style = MaterialTheme.typography.titleMedium)
                             Spacer(modifier = Modifier.height(8.dp))
                             
                             Row(
                                 modifier = Modifier.fillMaxWidth(),
                                 verticalAlignment = Alignment.CenterVertically,
                                 horizontalArrangement = Arrangement.spacedBy(8.dp)
                             ) {
                                 OutlinedTextField(
                                     value = minSizeText,
                                     onValueChange = { minSizeText = it },
                                     label = { Text(getThemedString(stringResource(R.string.min_size_mb))) },
                                     keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                     modifier = Modifier.weight(1f)
                                 )
                                 
                                 Button(
                                     onClick = { 
                                         minSizeText.toIntOrNull()?.let { onFindLargeFiles(it) } 
                                     }
                                 ) {
                                     Text(getThemedString(stringResource(R.string.find)))
                                 }
                             }
                         }
                    }
                }

                if (state is StorageState.LargeFiles) {
                    item {
                         Text(
                            getThemedString(stringResource(R.string.found_large_files, state.files.size)),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    
                    items(state.files) { file ->
                        LargeFileItem(file)
                    }
                }
            }
        }
    }
}

@Composable
private fun StorageCard(title: String, storageInfo: StorageStats) {
     Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            
            LinearProgressIndicator(
                progress = { (storageInfo.usedBytes.toFloat() / storageInfo.totalBytes.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${getThemedString(stringResource(R.string.used))}: ${formatBytes(storageInfo.usedBytes)}")
                Text("${getThemedString(stringResource(R.string.free))}: ${formatBytes(storageInfo.freeBytes)}")
            }
             Text(
                "${getThemedString(stringResource(R.string.total))}: ${formatBytes(storageInfo.totalBytes)}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
private fun LargeFileItem(file: LargeFile) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
             Icon(
                Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(file.name, style = MaterialTheme.typography.titleMedium)
                Text(file.path, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                     Text(formatBytes(file.size), style = MaterialTheme.typography.bodyMedium)
                     Text(
                         SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(file.lastModified)),
                         style = MaterialTheme.typography.bodySmall
                     )
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> String.format(Locale.getDefault(), "%.2f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> String.format(Locale.getDefault(), "%.2f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format(Locale.getDefault(), "%.2f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }
}
