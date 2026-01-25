package com.ble1st.connectias.ui.plugin.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private enum class BuilderAction { EXPORT, INSTALL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeclarativePluginBuilderScreen(
    onNavigateBack: () -> Unit,
    onExport: suspend (BuilderForm) -> Result<String>,
    onInstall: suspend (exportPath: String) -> Result<String>,
) {
    var pluginId by remember { mutableStateOf("demo.counter") }
    var pluginName by remember { mutableStateOf("Counter Demo") }
    var versionName by remember { mutableStateOf("1.0.0") }
    var versionCode by remember { mutableStateOf("1") }
    var developerId by remember { mutableStateOf("local.dev") }
    var threshold by remember { mutableStateOf("3") }
    var toastMessage by remember { mutableStateOf("Reached {{counter}}") }
    var enableNetworkTools by remember { mutableStateOf(true) }
    var curlUrl by remember { mutableStateOf("https://example.com") }
    var pingHost by remember { mutableStateOf("example.com") }
    var pingPort by remember { mutableStateOf("443") }
    var pingTimeoutMs by remember { mutableStateOf("1500") }

    var lastExportPath by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var action by remember { mutableStateOf<BuilderAction?>(null) }
    val busy = action != null

    val scroll = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Declarative Plugin Builder (MVP)") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scroll)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Plugin", style = MaterialTheme.typography.titleMedium)

                    OutlinedTextField(
                        value = pluginId,
                        onValueChange = { pluginId = it },
                        label = { Text("pluginId (z.B. demo.counter)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = pluginName,
                        onValueChange = { pluginName = it },
                        label = { Text("pluginName") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = versionName,
                            onValueChange = { versionName = it },
                            label = { Text("versionName") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = versionCode,
                            onValueChange = { versionCode = it },
                            label = { Text("versionCode") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = developerId,
                        onValueChange = { developerId = it },
                        label = { Text("developerId (Truststore-Key)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Workflow (Demo)", style = MaterialTheme.typography.titleMedium)
                    Text("Button-Klick: If counter >= threshold → Toast, sonst counter++ + PersistState.")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = threshold,
                            onValueChange = { threshold = it },
                            label = { Text("threshold") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = toastMessage,
                            onValueChange = { toastMessage = it },
                            label = { Text("toast message") },
                            modifier = Modifier.weight(2f)
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Network (curl/ping)", style = MaterialTheme.typography.titleMedium)
                    Text("Optionaler Demo-Block: Buttons für TCP-Ping und HTTP GET (curl-ähnlich).")

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            enabled = !busy,
                            onClick = { enableNetworkTools = !enableNetworkTools },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (enableNetworkTools) "Netzwerk: AN" else "Netzwerk: AUS")
                        }
                    }

                    OutlinedTextField(
                        value = curlUrl,
                        onValueChange = { curlUrl = it },
                        label = { Text("curl URL (https://...)") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = enableNetworkTools && !busy
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = pingHost,
                            onValueChange = { pingHost = it },
                            label = { Text("ping host") },
                            modifier = Modifier.weight(2f),
                            enabled = enableNetworkTools && !busy
                        )
                        OutlinedTextField(
                            value = pingPort,
                            onValueChange = { pingPort = it },
                            label = { Text("port") },
                            modifier = Modifier.weight(1f),
                            enabled = enableNetworkTools && !busy
                        )
                        OutlinedTextField(
                            value = pingTimeoutMs,
                            onValueChange = { pingTimeoutMs = it },
                            label = { Text("timeout (ms)") },
                            modifier = Modifier.weight(1f),
                            enabled = enableNetworkTools && !busy
                        )
                    }

                    Text(
                        "Hinweis: „Ping“ ist TCP-Connect (kein ICMP). INTERNET wird als Plugin-Permission deklariert (Consent nötig).",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    enabled = !busy,
                    onClick = {
                        status = "Export läuft..."
                        lastExportPath = null
                        action = BuilderAction.EXPORT
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Exportieren (.cplug)")
                }
                Button(
                    enabled = !busy && lastExportPath != null,
                    onClick = {
                        status = "Installation läuft..."
                        action = BuilderAction.INSTALL
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Installieren")
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (status != null) {
                Text(status!!, style = MaterialTheme.typography.bodyMedium)
            }
            if (lastExportPath != null) {
                Text("Export: $lastExportPath", style = MaterialTheme.typography.bodySmall)
            } else {
                Text("Hinweis: Erst exportieren, dann installieren.", style = MaterialTheme.typography.bodySmall)
            }

            LaunchedEffect(action) {
                when (action) {
                    BuilderAction.EXPORT -> {
                        val form = BuilderForm(
                            pluginId = pluginId.trim(),
                            pluginName = pluginName.trim(),
                            versionName = versionName.trim(),
                            versionCode = versionCode.trim().toIntOrNull() ?: 1,
                            developerId = developerId.trim(),
                            threshold = threshold.trim().toLongOrNull() ?: 3L,
                            toastMessage = toastMessage,
                            enableNetworkTools = enableNetworkTools,
                            curlUrl = curlUrl.trim(),
                            pingHost = pingHost.trim(),
                            pingPort = pingPort.trim().toIntOrNull() ?: 443,
                            pingTimeoutMs = pingTimeoutMs.trim().toIntOrNull() ?: 1500,
                        )
                        val r = onExport(form)
                        r.onSuccess { path ->
                            lastExportPath = path
                            status = "Export erfolgreich."
                        }.onFailure { e ->
                            status = "Export fehlgeschlagen: ${e.message}"
                        }
                        action = null
                    }
                    BuilderAction.INSTALL -> {
                        val p = lastExportPath
                        if (p == null) {
                            status = "Keine Export-Datei vorhanden."
                            action = null
                            return@LaunchedEffect
                        }
                        val r = onInstall(p)
                        r.onSuccess { pid ->
                            status = "Installiert: $pid"
                        }.onFailure { e ->
                            status = "Installation fehlgeschlagen: ${e.message}"
                        }
                        action = null
                    }
                    null -> Unit
                }
            }
        }
    }
}

data class BuilderForm(
    val pluginId: String,
    val pluginName: String,
    val versionName: String,
    val versionCode: Int,
    val developerId: String,
    val threshold: Long,
    val toastMessage: String,
    val enableNetworkTools: Boolean,
    val curlUrl: String,
    val pingHost: String,
    val pingPort: Int,
    val pingTimeoutMs: Int,
)

