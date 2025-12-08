package com.ble1st.connectias.core.logging.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.ble1st.connectias.common.ui.strings.LocalAppStrings
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.core.logging.LogEntryViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Locale

@AndroidEntryPoint
class LogViewerFragment : Fragment() {

    private val viewModel: LogEntryViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                ConnectiasTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        val logs by viewModel.logs.collectAsState(initial = emptyList())

                        val levelOptions = listOf("ALL", "VERBOSE", "DEBUG", "INFO", "WARN", "ERROR")
                        var selectedLevel by remember { mutableStateOf("ALL") }
                        var searchText by remember { mutableStateOf("") }
                        val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()) }
                        val listState = rememberLazyListState()

                        fun levelThreshold(): Int? {
                            return when (selectedLevel) {
                                "VERBOSE" -> Log.VERBOSE
                                "DEBUG" -> Log.DEBUG
                                "INFO" -> Log.INFO
                                "WARN" -> Log.WARN
                                "ERROR" -> Log.ERROR
                                else -> null
                            }
                        }

                        fun levelLabel(level: Int): String {
                            return when (level) {
                                Log.VERBOSE -> "VERBOSE"
                                Log.DEBUG -> "DEBUG"
                                Log.INFO -> "INFO"
                                Log.WARN -> "WARN"
                                Log.ERROR -> "ERROR"
                                Log.ASSERT -> "ASSERT"
                                else -> "UNKNOWN"
                            }
                        }

                        val colorScheme = MaterialTheme.colorScheme

                        fun levelColor(level: Int) = when (level) {
                            Log.ERROR, Log.ASSERT -> colorScheme.error
                            Log.WARN -> colorScheme.tertiary
                            Log.INFO -> colorScheme.primary
                            else -> colorScheme.onSurfaceVariant
                        }

                        val filteredLogs = remember(logs, selectedLevel, searchText) {
                            val threshold = levelThreshold()
                            logs.filter { entry ->
                                val matchesLevel = threshold?.let { entry.level >= it } ?: true
                                val matchesSearch = searchText.isBlank() ||
                                    entry.message.contains(searchText, ignoreCase = true) ||
                                    (entry.tag?.contains(searchText, ignoreCase = true) == true)
                                matchesLevel && matchesSearch
                            }
                        }

                        var isLevelMenuExpanded by remember { mutableStateOf(false) }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = LocalAppStrings.current.logViewerTitle,
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    TextButton(
                                        onClick = { isLevelMenuExpanded = true }
                                    ) {
                                        Text("Level: $selectedLevel")
                                    }
                                    DropdownMenu(
                                        expanded = isLevelMenuExpanded,
                                        onDismissRequest = { isLevelMenuExpanded = false }
                                    ) {
                                        levelOptions.forEach { option ->
                                            DropdownMenuItem(
                                                text = { Text(option) },
                                                onClick = {
                                                    selectedLevel = option
                                                    isLevelMenuExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                OutlinedTextField(
                                    value = searchText,
                                    onValueChange = { searchText = it },
                                    label = { Text("Search") },
                                    modifier = Modifier.weight(2f),
                                    singleLine = true
                                )
                            }

                            if (filteredLogs.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (logs.isEmpty()) "No logs yet" else "No logs match the current filter",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                LazyColumn(
                                    state = listState,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    items(filteredLogs) { logEntry ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = dateFormatter.format(logEntry.timestamp),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = levelLabel(logEntry.level),
                                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = levelColor(logEntry.level)
                                                )
                                            }
                                            Text(
                                                text = (logEntry.tag ?: "App") + ": " + logEntry.message,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 4,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            logEntry.exceptionTrace?.let {
                                                Spacer(modifier = Modifier.size(6.dp))
                                                Text(
                                                    text = it,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
