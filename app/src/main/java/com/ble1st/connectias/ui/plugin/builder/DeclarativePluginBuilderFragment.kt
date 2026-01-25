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
import com.ble1st.connectias.core.plugin.declarative.NodeRegistry
import com.ble1st.connectias.core.plugin.declarative.NodeSpec
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
import org.json.JSONArray
import org.json.JSONObject

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
                                val needsInternet = form.nodes.any { n ->
                                    NodeRegistry.get(n.type)?.sideEffects?.contains(NodeSpec.SideEffect.NETWORK) == true
                                }
                                val needsCamera = form.nodes.any { n ->
                                    NodeRegistry.get(n.type)?.sideEffects?.contains(NodeSpec.SideEffect.CAMERA) == true
                                }
                                val capabilities = buildList {
                                    add("ui.basic")
                                    add("workflow.basic")
                                    if (needsInternet) add("network.tools")
                                    if (needsCamera) add("camera.capture")
                                }
                                val permissions = buildList {
                                    if (needsInternet) add(android.Manifest.permission.INTERNET)
                                    if (needsCamera) add(android.Manifest.permission.CAMERA)
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
                                    uiMainJson = buildUiMainJson(form),
                                    uiExtraScreens = mapOf(
                                        "ui/image.json" to buildUiImageJson(form)
                                    ),
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

    private fun buildUiMainJson(form: BuilderForm): String {
        // Minimal UI: a single "Run" button plus a couple of debug text fields.
        // This is enough for end-to-end testing of the automation nodes.
        val hasCurl = form.nodes.any { it.type == "Curl" }
        val root = JSONObject().apply {
            put("id", "root")
            put("type", "COLUMN")
            put("properties", JSONObject())
            put(
                "children",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("id", "txt_result")
                            put("type", "TEXT_VIEW")
                            put("properties", JSONObject().apply { put("text", "Result: {{result}}") })
                        }
                    )
                    put(
                        JSONObject().apply {
                            put("id", "txt_ping")
                            put("type", "TEXT_VIEW")
                            put(
                                "properties",
                                JSONObject().apply {
                                    put("text", "Ping: ok={{lastPingOk}} latencyMs={{lastPingLatencyMs}}")
                                }
                            )
                        }
                    )
                    put(
                        JSONObject().apply {
                            put("id", "txt_capture")
                            put("type", "TEXT_VIEW")
                            put(
                                "properties",
                                JSONObject().apply {
                                    put("text", "Capture: {{captureStatus}} bytes(base64)={{lastCapturedImage}}")
                                }
                            )
                        }
                    )
                    put(
                        JSONObject().apply {
                            put("id", "btn_run")
                            put("type", "BUTTON")
                            put("properties", JSONObject().apply {
                                put("text", "Run")
                                put("variant", "PRIMARY")
                            })
                        }
                    )
                    put(
                        JSONObject().apply {
                            put("id", "btn_open_image")
                            put("type", "BUTTON")
                            put("properties", JSONObject().apply {
                                put("text", "Open Image Window")
                                put("variant", "SECONDARY")
                            })
                        }
                    )
                    if (hasCurl) {
                        put(
                            JSONObject().apply {
                                put("id", "txt_http")
                                put("type", "TEXT_VIEW")
                                put("properties", JSONObject().apply { put("text", "HTTP status: {{lastCurlStatus}}") })
                            }
                        )
                    }
                }
            )
        }

        return JSONObject().apply {
            put("screenId", "main")
            put("title", form.pluginName)
            put("components", JSONArray().apply { put(root) })
        }.toString()
    }

    private fun buildUiImageJson(form: BuilderForm): String {
        val root = JSONObject().apply {
            put("id", "root_image")
            put("type", "COLUMN")
            put("properties", JSONObject().apply { put("spacing", 8) })
            put(
                "children",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("id", "txt_image_title")
                            put("type", "TEXT_VIEW")
                            put("properties", JSONObject().apply { put("text", "Image Preview") })
                        }
                    )
                    put(
                        JSONObject().apply {
                            put("id", "img_preview")
                            put("type", "IMAGE")
                            put(
                                "properties",
                                JSONObject().apply {
                                    put("base64Data", "{{uiImageBase64}}")
                                    put("contentDescription", "Captured image")
                                    put("contentScale", "FIT")
                                }
                            )
                        }
                    )
                    put(
                        JSONObject().apply {
                            put("id", "btn_back")
                            put("type", "BUTTON")
                            put("properties", JSONObject().apply {
                                put("text", "Back")
                                put("variant", "SECONDARY")
                            })
                        }
                    )
                }
            )
        }

        return JSONObject().apply {
            put("screenId", "image")
            put("title", "${form.pluginName} - Image")
            put("components", JSONArray().apply { put(root) })
        }.toString()
    }

    private fun buildFlowJson(form: BuilderForm): String {
        val firstId = form.nodes.firstOrNull()?.id ?: ""
        val backNodeId = "sys_back"
        val openImageNodeId = "sys_open_image"
        val triggers = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("type", "OnClick")
                    put("targetId", "btn_run")
                    put("startNodeId", firstId)
                }
            )
            put(
                JSONObject().apply {
                    put("type", "OnClick")
                    put("targetId", "btn_open_image")
                    put("startNodeId", openImageNodeId)
                }
            )
            put(
                JSONObject().apply {
                    put("type", "OnClick")
                    put("targetId", "btn_back")
                    put("startNodeId", backNodeId)
                }
            )
        }

        val nodes = JSONArray()
        form.nodes.forEachIndexed { idx, n ->
            val next = form.nodes.getOrNull(idx + 1)?.id
            val spec = NodeRegistry.get(n.type)
            val paramsObj = JSONObject()
            n.params.forEach { (k, v) ->
                val p = spec?.params?.firstOrNull { it.key == k }
                val coerced = coerceParamValue(p, v)
                if (coerced != null) {
                    paramsObj.put(k, coerced)
                }
            }

            nodes.put(
                JSONObject().apply {
                    put("id", n.id)
                    put("type", n.type)
                    put("params", paramsObj)
                    if (next != null) put("next", next) else put("next", JSONObject.NULL)
                }
            )
        }

        // System nodes for navigation between windows.
        nodes.put(
            JSONObject().apply {
                put("id", openImageNodeId)
                put("type", "ShowImage")
                put("params", JSONObject().apply {
                    put("source", "state.lastCapturedImage")
                    put("imageKey", "uiImageBase64")
                    put("screenId", "image")
                })
                put("next", JSONObject.NULL)
            }
        )
        nodes.put(
            JSONObject().apply {
                put("id", backNodeId)
                put("type", "Navigate")
                put("params", JSONObject().apply { put("screenId", "main") })
                put("next", JSONObject.NULL)
            }
        )

        return JSONObject().apply {
            put("flowId", "main")
            put("triggers", triggers)
            put("nodes", nodes)
        }.toString()
    }

    private fun coerceParamValue(spec: NodeSpec.ParamSpec?, raw: String): Any? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null

        // If spec is unknown (forward compatibility), keep as string.
        val type = spec?.type ?: NodeSpec.ParamType.ANY
        return when (type) {
            NodeSpec.ParamType.BOOLEAN -> {
                when (trimmed.lowercase()) {
                    "true" -> true
                    "false" -> false
                    else -> null
                }
            }
            NodeSpec.ParamType.LONG -> trimmed.toLongOrNull() ?: trimmed
            NodeSpec.ParamType.DOUBLE -> trimmed.toDoubleOrNull() ?: trimmed
            NodeSpec.ParamType.ENUM,
            NodeSpec.ParamType.STRING,
            NodeSpec.ParamType.ANY -> trimmed
        }
    }

    private fun escapeJson(s: String): String {
        return s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }
}

