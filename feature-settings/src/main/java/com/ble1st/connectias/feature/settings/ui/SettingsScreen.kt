package com.ble1st.connectias.feature.settings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Main Settings screen composable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    // Show snackbar for error/success messages
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearErrorMessage()
        }
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearSuccessMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Appearance Section
            item {
                SettingsSection(title = "Appearance") {
                    ThemeSelector(
                        currentTheme = uiState.theme,
                        onThemeSelected = { viewModel.setTheme(it) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsSwitch(
                        title = "Dynamic Color",
                        description = "Use Material You dynamic colors (Android 12+)",
                        checked = uiState.dynamicColor,
                        onCheckedChange = { viewModel.setDynamicColor(it) }
                    )
                }
            }

            // Security Section
            item {
                SettingsSection(title = "Security") {
                    SettingsSwitch(
                        title = "Auto Lock",
                        description = "Automatically lock the app after inactivity",
                        checked = uiState.autoLockEnabled,
                        onCheckedChange = { viewModel.setAutoLockEnabled(it) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsSwitch(
                        title = "RASP Logging",
                        description = "Enable logging for Runtime Application Self-Protection",
                        checked = uiState.raspLoggingEnabled,
                        onCheckedChange = { viewModel.setRaspLoggingEnabled(it) }
                    )
                }
            }

            // Network Section
            item {
                SettingsSection(title = "Network") {
                    DnsServerInput(
                        value = uiState.dnsServer,
                        onValueChange = { viewModel.setDnsServer(it) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ScanTimeoutSlider(
                        value = uiState.scanTimeout,
                        onValueChange = { viewModel.setScanTimeout(it) }
                    )
                }
            }

            // Privacy Section
            item {
                SettingsSection(title = "Privacy") {
                    SettingsSwitch(
                        title = "Clipboard Auto-Clear",
                        description = "Automatically clear clipboard after a delay",
                        checked = uiState.clipboardAutoClear,
                        onCheckedChange = { viewModel.setClipboardAutoClear(it) }
                    )
                }
            }

            // Advanced Section
            item {
                SettingsSection(title = "Advanced") {
                    LoggingLevelSelector(
                        currentLevel = uiState.loggingLevel,
                        onLevelSelected = { viewModel.setLoggingLevel(it) }
                    )
                }
            }

            // Reset Section
            item {
                SettingsSection(title = "Reset") {
                    ResetSettingsButton(
                        onResetAll = {
                            viewModel.resetAllSettings(
                                resetPlainSettings = true,
                                resetEncryptedSettings = true
                            )
                        }
                    )
                }
            }
        }
    }
}

/**
 * Settings section card wrapper.
 */
@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}

/**
 * Theme selector with radio buttons.
 */
@Composable
fun ThemeSelector(
    currentTheme: String,
    onThemeSelected: (String) -> Unit
) {
    val themes = listOf("light", "dark", "system")
    val themeLabels = mapOf(
        "light" to "Light",
        "dark" to "Dark",
        "system" to "System Default"
    )

    Column {
        Text(
            text = "Theme",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        themes.forEach { theme ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = currentTheme == theme,
                    onClick = { onThemeSelected(theme) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = themeLabels[theme] ?: theme,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Settings switch item.
 */
@Composable
fun SettingsSwitch(
    title: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * DNS server input field.
 */
@Composable
fun DnsServerInput(
    value: String,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("DNS Server") },
        placeholder = { Text("e.g., 8.8.8.8") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        leadingIcon = {
            Icon(Icons.Default.Dns, contentDescription = null)
        }
    )
}

/**
 * Scan timeout slider.
 */
@Composable
fun ScanTimeoutSlider(
    value: Int,
    onValueChange: (Int) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Scan Timeout",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "${value}ms",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 1000f..30000f,
            steps = 28 // Steps of 1000ms
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "1s",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Text(
                text = "30s",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * Logging level selector.
 */
@Composable
fun LoggingLevelSelector(
    currentLevel: String,
    onLevelSelected: (String) -> Unit
) {
    val levels = listOf("VERBOSE", "DEBUG", "INFO", "WARN", "ERROR")
    
    Column {
        Text(
            text = "Logging Level",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        levels.forEach { level ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = currentLevel == level,
                    onClick = { onLevelSelected(level) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = level,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Reset settings button.
 */
@Composable
fun ResetSettingsButton(
    onResetAll: () -> Unit
) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    Column {
        Button(
            onClick = { showConfirmDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        ) {
            Icon(Icons.Default.RestartAlt, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Reset All Settings")
        }

        if (showConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                title = { Text("Reset Settings?") },
                text = { Text("This will reset all settings to their default values. This action cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onResetAll()
                            showConfirmDialog = false
                        }
                    ) {
                        Text("Reset", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

