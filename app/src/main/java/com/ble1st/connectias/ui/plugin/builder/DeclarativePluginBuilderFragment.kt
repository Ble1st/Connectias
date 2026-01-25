package com.ble1st.connectias.ui.plugin.builder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.core.module.ModuleInfo
import com.ble1st.connectias.core.module.ModuleRegistry
import com.ble1st.connectias.plugin.PluginImportHandler
import com.ble1st.connectias.plugin.PluginManagerSandbox
import com.ble1st.connectias.plugin.PluginManifestParser
import com.ble1st.connectias.plugin.PluginPermissionManager
import com.ble1st.connectias.plugin.declarative.packaging.DeclarativePluginPackager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class DeclarativePluginBuilderFragment : Fragment() {

    @Inject lateinit var pluginManager: PluginManagerSandbox
    @Inject lateinit var moduleRegistry: ModuleRegistry
    @Inject lateinit var permissionManager: PluginPermissionManager
    @Inject lateinit var manifestParser: PluginManifestParser

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ConnectiasTheme {
                    DeclarativePluginBuilderScreen(
                        onNavigateBack = { findNavController().navigateUp() },
                        onExport = { form ->
                            withContext(Dispatchers.IO) {
                                val packager = DeclarativePluginPackager(requireContext())
                                val capabilities = buildList {
                                    add("ui.basic")
                                    add("workflow.basic")
                                    if (form.enableNetworkTools) add("network.tools")
                                }
                                val permissions = if (form.enableNetworkTools) {
                                    listOf(android.Manifest.permission.INTERNET)
                                } else {
                                    emptyList()
                                }
                                val spec = DeclarativePluginPackager.BuildSpec(
                                    pluginId = form.pluginId,
                                    pluginName = form.pluginName,
                                    versionName = form.versionName,
                                    versionCode = form.versionCode,
                                    developerId = form.developerId,
                                    capabilities = capabilities,
                                    permissions = permissions,
                                    startScreenId = "main",
                                    uiMainJson = buildUiJson(form),
                                    flowMainJson = buildFlowJson(form),
                                    description = "Built on-device (MVP)"
                                )

                                val outDir = requireContext().getExternalFilesDir("exports") ?: requireContext().filesDir
                                val outFile = File(outDir, "${form.pluginId}_${form.versionName}.cplug")
                                packager.buildToFile(spec, outFile).map { it.absolutePath }
                            }
                        },
                        onInstall = { exportPath ->
                            withContext(Dispatchers.IO) {
                                val pluginDir = requireContext().filesDir.resolve("plugins")
                                val importHandler = PluginImportHandler(
                                    requireContext(),
                                    pluginDir,
                                    pluginManager,
                                    manifestParser,
                                    permissionManager
                                )

                                val importResult = importHandler.importPluginFromPath(exportPath)
                                importResult.onFailure { e ->
                                    Timber.e(e, "Declarative install failed during import")
                                }
                                val pluginId = importResult.getOrNull() ?: return@withContext Result.failure(Exception("Import failed"))

                                // Load into sandbox + enable state in manager UI
                                val load = pluginManager.loadAndEnablePlugin(pluginId)
                                load.onSuccess { meta ->
                                    try {
                                        pluginManager.enablePlugin(pluginId)
                                    } catch (_: Exception) {
                                        // best-effort
                                    }

                                    moduleRegistry.registerModule(
                                        ModuleInfo(
                                            id = meta.pluginId,
                                            name = meta.pluginName,
                                            version = meta.version,
                                            isActive = true
                                        )
                                    )
                                }
                                load.map { pluginId }
                            }
                        }
                    )
                }
            }
        }
    }

    private fun buildUiJson(form: BuilderForm): String {
        // Minimal UI: Text + Buttons. Optional Network Tools (curl/ping).
        val networkUi = if (form.enableNetworkTools) {
            """
              ,
              { "id": "txt_net", "type": "TEXT_VIEW", "properties": { "text": "Ping: {{lastPingOk}} ({{lastPingLatencyMs}}ms) | HTTP: {{lastCurlStatus}}" } },
              { "id": "btn_ping", "type": "BUTTON", "properties": { "text": "Ping", "variant": "SECONDARY" } },
              { "id": "btn_curl", "type": "BUTTON", "properties": { "text": "Curl", "variant": "SECONDARY" } }
            """.trimIndent()
        } else {
            ""
        }
        return """
            {
              "screenId": "main",
              "title": "${escapeJson(form.pluginName)}",
              "components": [
                {
                  "id": "root",
                  "type": "COLUMN",
                  "properties": { },
                  "children": [
                    { "id": "txt_counter", "type": "TEXT_VIEW", "properties": { "text": "Counter: {{counter}}" } },
                    { "id": "btn_inc", "type": "BUTTON", "properties": { "text": "Increment", "variant": "PRIMARY" } }
                    $networkUi
                  ]
                }
              ]
            }
        """.trimIndent()
    }

    private fun buildFlowJson(form: BuilderForm): String {
        val threshold = form.threshold
        val toast = escapeJson(form.toastMessage)
        val netTriggers = if (form.enableNetworkTools) {
            """
                ,
                { "type": "OnClick", "targetId": "btn_ping", "startNodeId": "ping1" },
                { "type": "OnClick", "targetId": "btn_curl", "startNodeId": "curl1" }
            """.trimIndent()
        } else {
            ""
        }

        val netNodes = if (form.enableNetworkTools) {
            val host = escapeJson(form.pingHost)
            val url = escapeJson(form.curlUrl)
            """
                ,
                { "id": "ping1", "type": "Ping", "params": { "host": "$host", "port": ${form.pingPort}, "timeoutMs": ${form.pingTimeoutMs}, "okKey": "lastPingOk", "latencyKey": "lastPingLatencyMs" }, "next": "toast_ping" },
                { "id": "toast_ping", "type": "ShowToast", "params": { "message": "Ping: {{lastPingOk}} ({{lastPingLatencyMs}}ms)" }, "next": null },
                { "id": "curl1", "type": "Curl", "params": { "url": "$url", "responseKey": "lastCurlBody", "statusKey": "lastCurlStatus", "contentTypeKey": "lastCurlContentType" }, "next": "toast_curl" },
                { "id": "toast_curl", "type": "ShowToast", "params": { "message": "HTTP status: {{lastCurlStatus}}" }, "next": null }
            """.trimIndent()
        } else {
            ""
        }
        return """
            {
              "flowId": "main",
              "triggers": [
                { "type": "OnClick", "targetId": "btn_inc", "startNodeId": "if1" },
                { "type": "OnMessage", "messageType": "PING", "startNodeId": "toast1" },
                { "type": "OnTimer", "everyMs": 15000, "startNodeId": "inc1" }
                $netTriggers
              ],
              "nodes": [
                { "id": "if1", "type": "IfElse", "params": { "left": "state.counter", "op": ">=", "right": $threshold }, "nextTrue": "toast1", "nextFalse": "inc1" },
                { "id": "inc1", "type": "Increment", "params": { "key": "counter", "delta": 1 }, "next": "persist1" },
                { "id": "persist1", "type": "PersistState", "params": { }, "next": null },
                { "id": "toast1", "type": "ShowToast", "params": { "message": "$toast" }, "next": null }
                $netNodes
              ]
            }
        """.trimIndent()
    }

    private fun escapeJson(s: String): String {
        return s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }
}

