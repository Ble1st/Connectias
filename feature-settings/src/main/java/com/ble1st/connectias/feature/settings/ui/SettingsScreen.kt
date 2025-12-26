package com.ble1st.connectias.feature.settings.ui
import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.common.ui.strings.getThemedString
import com.ble1st.connectias.feature.settings.R
import com.ble1st.connectias.common.ui.strings.LocalAppStrings

/**
 * Main Settings screen composable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit = {},
    onNavigateToLogViewer: () -> Unit // New parameter for navigation
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
                title = { Text(LocalAppStrings.current.settingsTitle) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = LocalAppStrings.current.actionCancel) // Changed to themed string
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
                SettingsSection(title = getThemedString(stringResource(R.string.settings_appearance))) { // This uses stringResource, keep it for now.
                    ThemeSelector(
                        currentTheme = uiState.theme,
                        onThemeSelected = { viewModel.setTheme(it) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    ThemeStyleSelector(
                        currentThemeStyle = uiState.themeStyle,
                        onThemeStyleSelected = { viewModel.setThemeStyle(it) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsSwitch(
                        title = getThemedString(stringResource(R.string.settings_dynamic_color)),
                        description = getThemedString(stringResource(R.string.settings_dynamic_color_description)),
                        checked = uiState.dynamicColor,
                        onCheckedChange = { viewModel.setDynamicColor(it) }
                    )
                }
            }

            // Security Section
            item {
                SettingsSection(title = LocalAppStrings.current.securityTitle) { // Changed to themed string
                    SettingsSwitch(
                        title = getThemedString(stringResource(R.string.settings_auto_lock)),
                        description = getThemedString(stringResource(R.string.settings_auto_lock_description)),
                        checked = uiState.autoLockEnabled,
                        onCheckedChange = { viewModel.setAutoLockEnabled(it) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsSwitch(
                        title = getThemedString(stringResource(R.string.settings_rasp_logging)),
                        description = getThemedString(stringResource(R.string.settings_rasp_logging_description)),
                        checked = uiState.raspLoggingEnabled,
                        onCheckedChange = { viewModel.setRaspLoggingEnabled(it) }
                    )
                }
            }

            // Privacy Section (Now consolidated into Security) - if any settings remain.
            // For now, removing this section as Privacy is integrated into Security logic.
            /*
            item {
                SettingsSection(title = getThemedString(stringResource(R.string.settings_privacy))) {
                    SettingsSwitch(
                        title = getThemedString(stringResource(R.string.settings_clipboard_auto_clear)),
                        description = getThemedString(stringResource(R.string.settings_clipboard_auto_clear_description)),
                        checked = uiState.clipboardAutoClear,
                        onCheckedChange = { viewModel.setClipboardAutoClear(it) }
                    )
                }
            }
            */

            // Advanced Section
            item {
                SettingsSection(title = getThemedString(stringResource(R.string.settings_advanced))) {
                    LoggingLevelSelector(
                        currentLevel = uiState.loggingLevel,
                        onLevelSelected = { viewModel.setLoggingLevel(it) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onNavigateToLogViewer, // Use the new navigation lambda
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Description, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(LocalAppStrings.current.logViewerTitle) // Themed string
                    }
                }
            }

            // Reset Section
            item {
                SettingsSection(title = getThemedString(stringResource(R.string.settings_reset))) {
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
        shape = MaterialTheme.shapes.medium,
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
 * Theme selector with radio buttons (light/dark/system).
 */
@Composable
fun ThemeSelector(
    currentTheme: String,
    onThemeSelected: (String) -> Unit
) {
    val themes = listOf("light", "dark", "system")

    Column {
        Text(
            text = getThemedString(stringResource(R.string.settings_theme)),
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
                    text = getThemedString(
                        when (theme) {
                            "light" -> stringResource(R.string.settings_theme_light)
                            "dark" -> stringResource(R.string.settings_theme_dark)
                            else -> stringResource(R.string.settings_theme_system)
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Theme style selector with radio buttons (standard/adeptus_mechanicus).
 */
@Composable
fun ThemeStyleSelector(
    currentThemeStyle: String,
    onThemeStyleSelected: (String) -> Unit
) {
    val themeStyles = listOf("standard", "adeptus_mechanicus")

    Column {
        Text(
            text = getThemedString(stringResource(R.string.settings_theme_style)),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        themeStyles.forEach { style ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = currentThemeStyle == style,
                    onClick = { onThemeStyleSelected(style) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = getThemedString(
                        when (style) {
                            "standard" -> stringResource(R.string.settings_theme_style_standard)
                            "adeptus_mechanicus" -> stringResource(R.string.settings_theme_style_adeptus_mechanicus)
                            else -> stringResource(R.string.settings_theme_style_standard)
                        }
                    ),
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
            text = getThemedString(stringResource(R.string.settings_logging_level)),
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
                Spacer(modifier = Modifier.size(8.dp))
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
            Spacer(modifier = Modifier.size(8.dp))
            Text(getThemedString(stringResource(R.string.settings_reset_all)))
        }

        if (showConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                title = { Text(getThemedString(stringResource(R.string.settings_reset_confirm_title))) },
                text = { Text(getThemedString(stringResource(R.string.settings_reset_confirm_message))) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onResetAll()
                            showConfirmDialog = false
                        }
                    ) {
                        Text(getThemedString(stringResource(R.string.settings_reset_confirm_button)), color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDialog = false }) {
                        Text(getThemedString(stringResource(R.string.settings_reset_cancel_button)))
                    }
                }
            )
        }
    }
}