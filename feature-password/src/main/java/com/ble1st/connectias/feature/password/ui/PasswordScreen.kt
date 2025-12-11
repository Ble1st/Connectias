package com.ble1st.connectias.feature.password.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.feature.password.R
import com.ble1st.connectias.feature.password.data.PasswordCheckResult
import com.ble1st.connectias.feature.password.data.PasswordHistoryEntity
import com.ble1st.connectias.feature.password.data.PasswordStrength
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PasswordScreen(
    viewModel: PasswordViewModel
) {
    val uiState by viewModel.state.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Password", "Passphrase", "History")

    // Sync Tab with Mode
    LaunchedEffect(selectedTab) {
        viewModel.setMode(selectedTab == 1)
    }

    ConnectiasTheme {
        Scaffold { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Text(
                    text = stringResource(R.string.password_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(16.dp)
                )
                
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }

                when (selectedTab) {
                    0 -> PasswordGeneratorContent(uiState, viewModel)
                    1 -> PassphraseGeneratorContent(uiState, viewModel)
                    2 -> HistoryContent(uiState.history, viewModel::clearHistory, viewModel::deleteHistoryItem)
                }
            }
        }
    }
}

@Composable
private fun PasswordGeneratorContent(
    uiState: PasswordUiState,
    viewModel: PasswordViewModel
) {
    val config = uiState.generatorConfig
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Options", style = MaterialTheme.typography.titleMedium)
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = config.includeLowercase, onCheckedChange = { viewModel.onGeneratorConfigChanged(config.copy(includeLowercase = it)) })
                    Text("Lowercase (a-z)")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = config.includeUppercase, onCheckedChange = { viewModel.onGeneratorConfigChanged(config.copy(includeUppercase = it)) })
                    Text("Uppercase (A-Z)")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = config.includeDigits, onCheckedChange = { viewModel.onGeneratorConfigChanged(config.copy(includeDigits = it)) })
                    Text("Digits (0-9)")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = config.includeSymbols, onCheckedChange = { viewModel.onGeneratorConfigChanged(config.copy(includeSymbols = it)) })
                    Text("Symbols (!@#)")
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Text("Length: ${config.length}")
                Slider(
                    value = config.length.toFloat(),
                    onValueChange = { viewModel.onGeneratorConfigChanged(config.copy(length = it.toInt())) },
                    valueRange = 8f..64f,
                    steps = 56
                )
            }
        }
        
        Button(onClick = viewModel::generatePassword, modifier = Modifier.fillMaxWidth()) {
            Text("Generate Password")
        }
        
        ResultCard(uiState.generatedPassword, uiState.passwordCheck)
    }
}

@Composable
private fun PassphraseGeneratorContent(
    uiState: PasswordUiState,
    viewModel: PasswordViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Options", style = MaterialTheme.typography.titleMedium)
                
                Spacer(modifier = Modifier.height(8.dp))
                Text("Words: ${uiState.passphraseWordCount}")
                Slider(
                    value = uiState.passphraseWordCount.toFloat(),
                    onValueChange = { viewModel.updatePassphraseWordCount(it.toInt()) },
                    valueRange = 2f..10f,
                    steps = 8
                )
                
                OutlinedTextField(
                    value = uiState.passphraseSeparator,
                    onValueChange = viewModel::updatePassphraseSeparator,
                    label = { Text("Separator") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        Button(onClick = viewModel::generatePassword, modifier = Modifier.fillMaxWidth()) {
            Text("Generate Passphrase")
        }
        
        ResultCard(uiState.generatedPassword, uiState.passwordCheck)
    }
}

@Composable
private fun ResultCard(password: String, check: PasswordCheckResult?) {
    if (password.isNotEmpty()) {
        val context = LocalContext.current
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Result", style = MaterialTheme.typography.labelSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = password,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { copyToClipboard(context, password) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                    }
                }
                
                if (check != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    StrengthIndicator(check)
                }
            }
        }
    }
}

@Composable
private fun StrengthIndicator(check: PasswordCheckResult) {
    val color = when (check.strength) {
        PasswordStrength.WEAK -> Color.Red
        PasswordStrength.MEDIUM -> Color.Yellow
        PasswordStrength.STRONG -> Color.Green
    }
    
    Column {
        Text("Strength: ${check.strength.name}", color = color, style = MaterialTheme.typography.labelLarge)
        Text("Entropy: ${String.format("%.1f", check.entropy)} bits", style = MaterialTheme.typography.bodySmall)
        if (check.feedback.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            check.feedback.forEach { 
                Text("- $it", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun HistoryContent(
    history: List<PasswordHistoryEntity>,
    onClearAll: () -> Unit,
    onDelete: (PasswordHistoryEntity) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("History", style = MaterialTheme.typography.titleMedium)
            if (history.isNotEmpty()) {
                TextButton(onClick = onClearAll) {
                    Text("Clear All")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        if (history.isEmpty()) {
            Text("No history yet.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(history) { item ->
                    HistoryItem(item, onDelete)
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(
    item: PasswordHistoryEntity,
    onDelete: (PasswordHistoryEntity) -> Unit
) {
    val context = LocalContext.current
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault()) }
    
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.password, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "${item.type} | ${item.strength} | ${dateFormat.format(Date(item.timestamp))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row {
                IconButton(onClick = { copyToClipboard(context, item.password) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                }
                IconButton(onClick = { onDelete(item) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Password", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}