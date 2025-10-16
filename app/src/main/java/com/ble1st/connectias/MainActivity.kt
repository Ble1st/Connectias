package com.ble1st.connectias

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import com.ble1st.connectias.ui.plugin.PluginListScreen
import com.ble1st.connectias.ui.theme.ConnectiasTheme
import com.ble1st.connectias.plugin.PluginManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.android.AndroidEntryPoint
import android.net.Uri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ble1st.connectias.ui.plugin.PluginInstallationViewModel
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import timber.log.Timber
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ConnectiasTheme {
                ConnectiasApp()
            }
        }
    }
}

@PreviewScreenSizes
@Composable
fun ConnectiasApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    val pluginViewModel: PluginInstallationViewModel = viewModel()
    val pluginUiState by pluginViewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // File picker für Plugin-Installation
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { pluginUri ->
            Timber.d("MainActivity: Plugin file selected: $pluginUri")
            // Plugin installieren
            pluginViewModel.installPlugin(pluginUri)
        } ?: run {
            Timber.w("MainActivity: No file selected")
        }
    }
    
    // Feedback anzeigen
    LaunchedEffect(pluginUiState.installResult, pluginUiState.error) {
        pluginUiState.installResult?.let {
            Timber.i("MainActivity: Plugin installation result: $it")
            snackbarHostState.showSnackbar(
                message = "✅ Plugin erfolgreich installiert!",
                withDismissAction = true
            )
            pluginViewModel.clearError()
        }
        pluginUiState.error?.let { error ->
            Timber.e("MainActivity: Plugin installation error: $error")
            snackbarHostState.showSnackbar(
                message = "❌ Fehler: $error",
                withDismissAction = true
            )
            pluginViewModel.clearError()
        }
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            it.icon,
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { innerPadding ->
            when (currentDestination) {
                AppDestinations.HOME -> Greeting(
                    name = "Android",
                    modifier = Modifier.padding(innerPadding)
                )
                AppDestinations.PLUGINS -> PluginListScreen(
                    plugins = emptyList(), // TODO: Load from pluginManager
                    onPluginClick = { /* TODO: Navigate to plugin detail */ },
                    onInstallPlugin = { 
                        // File picker öffnen
                        filePickerLauncher.launch("application/zip")
                    },
                    modifier = Modifier.padding(innerPadding)
                )
                AppDestinations.FAVORITES -> Greeting(
                    name = "Favorites",
                    modifier = Modifier.padding(innerPadding)
                )
                AppDestinations.PROFILE -> Greeting(
                    name = "Profile",
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Home", Icons.Filled.Home),
    PLUGINS("Plugins", Icons.Filled.Extension),
    FAVORITES("Favorites", Icons.Filled.Favorite),
    PROFILE("Profile", Icons.Filled.AccountBox),
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ConnectiasTheme {
        Greeting("Android")
    }
}