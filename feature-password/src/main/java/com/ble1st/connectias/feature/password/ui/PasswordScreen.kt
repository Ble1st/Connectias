package com.ble1st.connectias.feature.password.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.feature.password.R
import com.ble1st.connectias.feature.password.data.PasswordCheckResult
import com.ble1st.connectias.feature.password.data.PasswordHistoryEntity
import com.ble1st.connectias.feature.password.data.PasswordStrength
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordScreen(
    viewModel: PasswordViewModel
) {
    val uiState by viewModel.state.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Generate", "Passphrase", "Evaluate", "History")

    // Sync Tab with Mode (only for generation modes)
    LaunchedEffect(selectedTab) {
        if (selectedTab <= 1) {
            viewModel.setMode(selectedTab == 1)
        }
    }

    ConnectiasTheme {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(R.string.password_title), style = MaterialTheme.typography.headlineSmall) }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = {}
                ) {
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
                    2 -> EvaluateContent(uiState, viewModel)
                    3 -> HistoryContent(uiState.history, viewModel::clearHistory, viewModel::deleteHistoryItem)
                }
            }
        }
    }
}

@Composable
private fun EvaluateContent(
    uiState: PasswordUiState,
    viewModel: PasswordViewModel
) {
    var passwordVisible by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = "Check Password Strength",
            style = MaterialTheme.typography.headlineSmall
        )

        OutlinedTextField(
            value = uiState.passwordInput,
            onValueChange = viewModel::onPasswordInputChanged,
            label = { Text("Enter Password") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    )
                }
            }
        )

        if (uiState.passwordInput.isNotEmpty() && uiState.passwordCheck != null) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    StrengthIndicator(uiState.passwordCheck)
                }
            }
        } else {
            Text(
                text = "Enter a password to see how strong it is.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Options", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = config.includeLowercase, onCheckedChange = { viewModel.onGeneratorConfigChanged(config.copy(includeLowercase = it)) })
                    Text("Lowercase (a-z)", style = MaterialTheme.typography.bodyLarge)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = config.includeUppercase, onCheckedChange = { viewModel.onGeneratorConfigChanged(config.copy(includeUppercase = it)) })
                    Text("Uppercase (A-Z)", style = MaterialTheme.typography.bodyLarge)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = config.includeDigits, onCheckedChange = { viewModel.onGeneratorConfigChanged(config.copy(includeDigits = it)) })
                    Text("Digits (0-9)", style = MaterialTheme.typography.bodyLarge)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = config.includeSymbols, onCheckedChange = { viewModel.onGeneratorConfigChanged(config.copy(includeSymbols = it)) })
                    Text("Symbols (!@#)", style = MaterialTheme.typography.bodyLarge)
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("Length: ${config.length}", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = config.length.toFloat(),
                    onValueChange = { viewModel.onGeneratorConfigChanged(config.copy(length = it.toInt())) },
                    valueRange = 8f..64f,
                    steps = 56
                )
            }
        }
        
        FilledTonalButton(
            onClick = viewModel::generatePassword, 
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
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
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Options", style = MaterialTheme.typography.titleLarge)
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("Words: ${uiState.passphraseWordCount}", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = uiState.passphraseWordCount.toFloat(),
                    onValueChange = { viewModel.updatePassphraseWordCount(it.toInt()) },
                    valueRange = 2f..10f,
                    steps = 8
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.passphraseSeparator,
                    onValueChange = viewModel::updatePassphraseSeparator,
                    label = { Text("Separator") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }
        
        FilledTonalButton(
            onClick = viewModel::generatePassword, 
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Generate Passphrase")
        }
        
        ResultCard(uiState.generatedPassword, uiState.passwordCheck)
    }
}

@Composable
private fun ResultCard(password: String, check: PasswordCheckResult?) {
    if (password.isNotEmpty()) {
        val context = LocalContext.current
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Result", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = password,
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { copyToClipboard(context, password) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                    }
                }
                
                if (check != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    StrengthIndicator(check)
                }
            }
        }
    }
}

@Composable
private fun StrengthIndicator(check: PasswordCheckResult) {
    val color = when (check.strength) {
        PasswordStrength.WEAK -> MaterialTheme.colorScheme.error
        PasswordStrength.MEDIUM -> Color(0xFFFFA000) // Amber 700
        PasswordStrength.STRONG -> Color(0xFF00C853) // Green A700
        PasswordStrength.VERY_STRONG -> MaterialTheme.colorScheme.primary
    }
    
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Strength: ${check.strength.name}", color = color, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Score: ${check.score}/100", style = MaterialTheme.typography.bodyMedium)
        }
        
        // Progress bar for score
        LinearProgressIndicator(
            progress = { check.score / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .height(8.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        
        Text("Entropy: ${String.format("%.1f", check.entropy)} bits", style = MaterialTheme.typography.labelMedium)
        if (check.feedback.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            check.feedback.forEach { 
                Text("• $it", style = MaterialTheme.typography.bodySmall)
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
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("History", style = MaterialTheme.typography.headlineSmall)
            if (history.isNotEmpty()) {
                TextButton(onClick = onClearAll) {
                    Text("Clear All")
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (history.isEmpty()) {
            Text("No history yet.", style = MaterialTheme.typography.bodyLarge)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
    
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.password, style = MaterialTheme.typography.titleMedium)
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