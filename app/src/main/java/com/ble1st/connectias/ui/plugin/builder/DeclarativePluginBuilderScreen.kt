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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.core.plugin.declarative.NodeRegistry
import com.ble1st.connectias.core.plugin.declarative.NodeSpec

private enum class BuilderAction { EXPORT, INSTALL }

private class NodeInstanceState(
    val id: String,
    val spec: NodeSpec,
) {
    val type: String = spec.type
    val params = mutableStateMapOf<String, String>()

    init {
        spec.params.forEach { p ->
            val defaultText = p.defaultValue?.toString().orEmpty()
            if (defaultText.isNotBlank()) {
                params[p.key] = defaultText
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeclarativePluginBuilderScreen(
    onNavigateBack: () -> Unit,
    onExport: suspend (BuilderForm) -> Result<String>,
    onInstall: suspend (exportPath: String) -> Result<String>,
) {
    var pluginId by remember { mutableStateOf("demo.automation") }
    var pluginName by remember { mutableStateOf("Automation Demo") }
    var versionName by remember { mutableStateOf("1.0.0") }
    var versionCode by remember { mutableStateOf("1") }
    var developerId by remember { mutableStateOf("local.dev") }

    val supportedSpecs = remember {
        // MVP builder supports linear chains only (no branching).
        NodeRegistry.list().filter { it.type != "IfElse" }
    }
    var selectedType by remember { mutableStateOf(supportedSpecs.firstOrNull()?.type ?: "") }
    val nodes = remember { mutableStateListOf<NodeInstanceState>() }
    var nextNodeIndex by remember { mutableIntStateOf(1) }

    var lastExportPath by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var action by remember { mutableStateOf<BuilderAction?>(null) }
    val busy = action != null

    val scroll = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Declarative Plugin Builder (Automation MVP)") },
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
                        label = { Text("pluginId (z.B. demo.automation)") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy
                    )
                    OutlinedTextField(
                        value = pluginName,
                        onValueChange = { pluginName = it },
                        label = { Text("pluginName") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = versionName,
                            onValueChange = { versionName = it },
                            label = { Text("versionName") },
                            modifier = Modifier.weight(1f),
                            enabled = !busy
                        )
                        OutlinedTextField(
                            value = versionCode,
                            onValueChange = { versionCode = it },
                            label = { Text("versionCode") },
                            modifier = Modifier.weight(1f),
                            enabled = !busy
                        )
                    }
                    OutlinedTextField(
                        value = developerId,
                        onValueChange = { developerId = it },
                        label = { Text("developerId (Truststore-Key)") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Workflow (Linear MVP)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Klicke auf eine vorgefertigte Node, um sie mit sinnvollen Defaults hinzuzufügen. " +
                            "Oder tippe einen Node-Typ manuell ein.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text("Vorgefertigte Nodes:", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            enabled = !busy,
                            onClick = {
                                val spec = NodeRegistry.get("CaptureImage") ?: return@Button
                                nodes.add(NodeInstanceState(id = "n${nextNodeIndex++}", spec = spec))
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("📷 Foto") }
                        Button(
                            enabled = !busy,
                            onClick = {
                                val spec = NodeRegistry.get("Curl") ?: return@Button
                                nodes.add(NodeInstanceState(id = "n${nextNodeIndex++}", spec = spec))
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("🌐 HTTP") }
                        Button(
                            enabled = !busy,
                            onClick = {
                                val spec = NodeRegistry.get("Ping") ?: return@Button
                                nodes.add(NodeInstanceState(id = "n${nextNodeIndex++}", spec = spec))
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("📡 Ping") }
                        Button(
                            enabled = !busy,
                            onClick = {
                                val spec = NodeRegistry.get("ShowImage") ?: return@Button
                                nodes.add(NodeInstanceState(id = "n${nextNodeIndex++}", spec = spec))
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("🖼️ Bild") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            enabled = !busy,
                            onClick = {
                                val spec = NodeRegistry.get("SetField") ?: return@Button
                                nodes.add(NodeInstanceState(id = "n${nextNodeIndex++}", spec = spec))
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("📝 SetField") }
                        Button(
                            enabled = !busy,
                            onClick = {
                                val spec = NodeRegistry.get("SetState") ?: return@Button
                                nodes.add(NodeInstanceState(id = "n${nextNodeIndex++}", spec = spec))
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("💾 SetState") }
                        Button(
                            enabled = !busy,
                            onClick = {
                                val spec = NodeRegistry.get("Increment") ?: return@Button
                                nodes.add(NodeInstanceState(id = "n${nextNodeIndex++}", spec = spec))
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("➕ Increment") }
                        Button(
                            enabled = !busy,
                            onClick = {
                                val spec = NodeRegistry.get("ShowToast") ?: return@Button
                                nodes.add(NodeInstanceState(id = "n${nextNodeIndex++}", spec = spec))
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("💬 Toast") }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedType,
                            onValueChange = { selectedType = it },
                            label = { Text("Oder Node-Typ manuell (z.B. SetState)") },
                            modifier = Modifier.weight(2f),
                            enabled = !busy
                        )
                        IconButton(
                            enabled = !busy && NodeRegistry.get(selectedType) != null,
                            onClick = {
                                val spec = NodeRegistry.get(selectedType) ?: return@IconButton
                                if (spec.type == "IfElse") return@IconButton
                                nodes.add(NodeInstanceState(id = "n${nextNodeIndex++}", spec = spec))
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add node")
                        }
                    }

                    if (nodes.isEmpty()) {
                        Text("Noch keine Nodes hinzugefügt.", style = MaterialTheme.typography.bodySmall)
                    } else {
                        nodes.forEachIndexed { idx, node ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("${node.id}: ${node.spec.displayName} (${node.type})", modifier = Modifier.weight(1f))
                                        IconButton(
                                            enabled = !busy && idx > 0,
                                            onClick = {
                                                val tmp = nodes[idx - 1]
                                                nodes[idx - 1] = nodes[idx]
                                                nodes[idx] = tmp
                                            }
                                        ) { Icon(Icons.Default.ArrowUpward, contentDescription = "Move up") }
                                        IconButton(
                                            enabled = !busy && idx < nodes.lastIndex,
                                            onClick = {
                                                val tmp = nodes[idx + 1]
                                                nodes[idx + 1] = nodes[idx]
                                                nodes[idx] = tmp
                                            }
                                        ) { Icon(Icons.Default.ArrowDownward, contentDescription = "Move down") }
                                        IconButton(
                                            enabled = !busy,
                                            onClick = { nodes.removeAt(idx) }
                                        ) { Icon(Icons.Default.Delete, contentDescription = "Remove") }
                                    }

                                    node.spec.params.forEach { p ->
                                        when (p.type) {
                                            NodeSpec.ParamType.BOOLEAN -> {
                                                val current = node.params[p.key]?.equals("true", true) == true
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    Text(p.label, modifier = Modifier.weight(1f))
                                                    Switch(
                                                        checked = current,
                                                        onCheckedChange = { checked -> node.params[p.key] = checked.toString() },
                                                        enabled = !busy
                                                    )
                                                }
                                            }
                                            NodeSpec.ParamType.LONG, NodeSpec.ParamType.DOUBLE -> {
                                                OutlinedTextField(
                                                    value = node.params[p.key] ?: "",
                                                    onValueChange = { node.params[p.key] = it },
                                                    label = { Text(p.label + if (p.required) " *" else "") },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    enabled = !busy,
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                                )
                                            }
                                            NodeSpec.ParamType.ENUM -> {
                                                OutlinedTextField(
                                                    value = node.params[p.key] ?: "",
                                                    onValueChange = { node.params[p.key] = it },
                                                    label = { Text(p.label + if (p.required) " *" else "") },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    enabled = !busy,
                                                )
                                                if (!p.allowedValues.isNullOrEmpty()) {
                                                    Text(
                                                        "Allowed: ${p.allowedValues.joinToString(", ")}",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                            }
                                            NodeSpec.ParamType.STRING, NodeSpec.ParamType.ANY -> {
                                                OutlinedTextField(
                                                    value = node.params[p.key] ?: "",
                                                    onValueChange = { node.params[p.key] = it },
                                                    label = { Text(p.label + if (p.required) " *" else "") },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    enabled = !busy,
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

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    enabled = !busy && nodes.isNotEmpty(),
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
                            nodes = nodes.map { n ->
                                BuilderNodeForm(
                                    id = n.id,
                                    type = n.type,
                                    params = n.params.toMap()
                                )
                            }
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
    val nodes: List<BuilderNodeForm>,
)

data class BuilderNodeForm(
    val id: String,
    val type: String,
    val params: Map<String, String>,
)

