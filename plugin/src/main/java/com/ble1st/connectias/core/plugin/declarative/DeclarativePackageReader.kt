package com.ble1st.connectias.core.plugin.declarative

import com.ble1st.connectias.plugin.declarative.model.DeclarativeJson
import com.ble1st.connectias.plugin.declarative.model.DeclarativePluginManifest
import com.ble1st.connectias.plugin.declarative.model.DeclarativePluginValidator
import com.ble1st.connectias.plugin.declarative.model.DeclarativeFlowDefinition
import com.ble1st.connectias.plugin.declarative.model.DeclarativeUiScreen
import org.json.JSONObject
import timber.log.Timber
import java.util.zip.ZipInputStream

/**
 * Reads a ".cplug" package from bytes inside the sandbox process.
 *
 * The sandbox has no filesystem access, so we must parse/validate in-memory.
 */
object DeclarativePackageReader {

    data class PackageData(
        val manifest: DeclarativePluginManifest,
        val screensById: Map<String, DeclarativeUiScreen>,
        val flowsById: Map<String, DeclarativeFlowDefinition>,
        val assets: Map<String, ByteArray>, // zip-path -> bytes
    )

    fun read(bytes: ByteArray): PackageData {
        val entries = readAllEntries(bytes)

        val manifestBytes = entries["plugin-manifest.json"]
            ?: throw IllegalArgumentException("Missing plugin-manifest.json")
        val manifestObj = JSONObject(String(manifestBytes, Charsets.UTF_8))
        val manifest = DeclarativeJson.parseManifest(manifestObj)
        val manifestValidation = DeclarativePluginValidator.validateManifest(manifest)
        if (!manifestValidation.ok) {
            throw IllegalArgumentException("Invalid declarative manifest: ${manifestValidation.errors.joinToString("; ")}")
        }

        val screens = LinkedHashMap<String, DeclarativeUiScreen>()
        manifest.entrypoints.screens.forEach { path ->
            val b = entries[path] ?: throw IllegalArgumentException("Missing UI screen entry: $path")
            val obj = JSONObject(String(b, Charsets.UTF_8))
            val screen = DeclarativeJson.parseUiScreen(obj)
            val vr = DeclarativePluginValidator.validateUiScreen(screen)
            if (!vr.ok) {
                throw IllegalArgumentException("Invalid UI screen ($path): ${vr.errors.joinToString("; ")}")
            }
            screens[screen.screenId] = screen
        }

        val flows = LinkedHashMap<String, DeclarativeFlowDefinition>()
        manifest.entrypoints.flows.forEach { path ->
            val b = entries[path] ?: throw IllegalArgumentException("Missing flow entry: $path")
            val obj = JSONObject(String(b, Charsets.UTF_8))
            val flow = DeclarativeJson.parseFlow(obj)
            val vr = DeclarativePluginValidator.validateFlow(flow)
            if (!vr.ok) {
                throw IllegalArgumentException("Invalid flow ($path): ${vr.errors.joinToString("; ")}")
            }
            flows[flow.flowId] = flow
        }

        if (!screens.containsKey(manifest.entrypoints.startScreenId)) {
            Timber.w("[SANDBOX][DECL] startScreenId not found in screens: ${manifest.entrypoints.startScreenId}")
        }

        val assets = entries.filterKeys { it.startsWith("assets/") }

        return PackageData(
            manifest = manifest,
            screensById = screens,
            flowsById = flows,
            assets = assets
        )
    }

    private fun readAllEntries(bytes: ByteArray): Map<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                if (name.startsWith("/") || name.contains("..")) {
                    throw IllegalArgumentException("Suspicious entry path: $name")
                }
                if (!entry.isDirectory) {
                    out[name] = zip.readBytes()
                }
            }
        }
        return out
    }
}

