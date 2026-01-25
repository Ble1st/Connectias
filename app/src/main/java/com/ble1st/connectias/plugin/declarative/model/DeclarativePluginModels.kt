package com.ble1st.connectias.plugin.declarative.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Declarative plugin package format (".cplug") models.
 *
 * NOTE: Comments are in English by project rules.
 */

/**
 * Supported declarative package versions.
 *
 * This is the schema version of the declarative package, not the plugin version.
 */
object DeclarativeSchemaVersions {
    const val V1 = 1
    val supported = setOf(V1)
}

/**
 * "plugin-manifest.json" for declarative plugins.
 *
 * Intended to be stable across app versions (forward/backward compatible with schemaVersion).
 */
data class DeclarativePluginManifest(
    val schemaVersion: Int,
    val pluginType: String, // must be "declarative"
    val pluginId: String,
    val pluginName: String,
    val versionName: String,
    val versionCode: Int,
    val developerId: String,
    val capabilities: List<String>,
    val permissions: List<String> = emptyList(), // Android permissions (e.g. android.permission.INTERNET)
    val entrypoints: DeclarativeEntryPoints,
    val description: String? = null,
    val minHostVersionCode: Int? = null,
)

data class DeclarativeEntryPoints(
    val startScreenId: String,
    val screens: List<String>, // e.g. ["ui/main.json"]
    val flows: List<String>,   // e.g. ["flows/main.json"]
)

/**
 * "signature.json" for declarative plugins.
 */
data class DeclarativeSignatureInfo(
    val algorithm: String, // "SHA256withECDSA"
    val developerId: String,
    val publicKeyBase64: String,
    val contentDigestHex: String,
    val signatureBase64: String,
    val signedAtEpochMillis: Long,
    val schemaVersion: Int = DeclarativeSchemaVersions.V1,
)

/**
 * UI screen definition (e.g. "ui/main.json").
 */
data class DeclarativeUiScreen(
    val screenId: String,
    val title: String,
    val components: List<DeclarativeUiComponent>,
)

data class DeclarativeUiComponent(
    val id: String,
    val type: String, // must map to UIComponentParcel.type (e.g. "BUTTON", "TEXT_VIEW", ...)
    val properties: Map<String, Any?> = emptyMap(),
    val children: List<DeclarativeUiComponent> = emptyList(),
)

/**
 * Flow definition (e.g. "flows/main.json").
 *
 * MVP uses a simplified node graph with explicit next pointers.
 */
data class DeclarativeFlowDefinition(
    val flowId: String,
    val triggers: List<DeclarativeTrigger>,
    val nodes: List<DeclarativeNode>,
)

data class DeclarativeTrigger(
    val type: String, // "OnClick", "OnTextChanged", "OnTimer", "OnMessage"
    val targetId: String? = null, // for UI triggers
    val everyMs: Long? = null,    // for timer
    val messageType: String? = null, // for message trigger
    val startNodeId: String,
)

data class DeclarativeNode(
    val id: String,
    val type: String, // "IfElse", "SetState", "Increment", "ShowToast", "Navigate", "EmitMessage", "PersistState"
    val params: Map<String, Any?> = emptyMap(),
    val next: String? = null,
    val nextTrue: String? = null,
    val nextFalse: String? = null,
)

/**
 * Helper parsing utilities (JSONObject based for consistency with existing codebase).
 */
object DeclarativeJson {

    fun parseManifest(obj: JSONObject): DeclarativePluginManifest {
        val schemaVersion = obj.optInt("schemaVersion", 0)
        val pluginType = obj.optString("pluginType", "")
        val pluginId = obj.optString("pluginId", "")
        val pluginName = obj.optString("pluginName", "")
        val versionName = obj.optString("versionName", "")
        val versionCode = obj.optInt("versionCode", -1)
        val developerId = obj.optString("developerId", "")
        val description = obj.optNullableString("description")
        val minHostVersionCode = if (obj.has("minHostVersionCode")) obj.optInt("minHostVersionCode") else null

        val capabilities = obj.optJSONArray("capabilities")
            ?.toStringList()
            ?: emptyList()

        val permissions = obj.optJSONArray("permissions")
            ?.toStringList()
            ?: emptyList()

        val entrypointsObj = obj.optJSONObject("entrypoints") ?: JSONObject()
        val entrypoints = DeclarativeEntryPoints(
            startScreenId = entrypointsObj.optString("startScreenId", ""),
            screens = entrypointsObj.optJSONArray("screens")?.toStringList() ?: emptyList(),
            flows = entrypointsObj.optJSONArray("flows")?.toStringList() ?: emptyList(),
        )

        return DeclarativePluginManifest(
            schemaVersion = schemaVersion,
            pluginType = pluginType,
            pluginId = pluginId,
            pluginName = pluginName,
            versionName = versionName,
            versionCode = versionCode,
            developerId = developerId,
            capabilities = capabilities,
            permissions = permissions,
            entrypoints = entrypoints,
            description = description,
            minHostVersionCode = minHostVersionCode,
        )
    }

    fun parseSignature(obj: JSONObject): DeclarativeSignatureInfo {
        return DeclarativeSignatureInfo(
            algorithm = obj.optString("algorithm", ""),
            developerId = obj.optString("developerId", ""),
            publicKeyBase64 = obj.optString("publicKeyBase64", ""),
            contentDigestHex = obj.optString("contentDigestHex", ""),
            signatureBase64 = obj.optString("signatureBase64", ""),
            signedAtEpochMillis = obj.optLong("signedAtEpochMillis", 0L),
            schemaVersion = obj.optInt("schemaVersion", DeclarativeSchemaVersions.V1),
        )
    }

    fun parseUiScreen(obj: JSONObject): DeclarativeUiScreen {
        val screenId = obj.optString("screenId", "")
        val title = obj.optString("title", "")
        val components = obj.optJSONArray("components")?.let { arr ->
            (0 until arr.length()).mapNotNull { idx ->
                arr.optJSONObject(idx)?.let { parseUiComponent(it) }
            }
        } ?: emptyList()
        return DeclarativeUiScreen(screenId = screenId, title = title, components = components)
    }

    private fun parseUiComponent(obj: JSONObject): DeclarativeUiComponent {
        val id = obj.optString("id", "")
        val type = obj.optString("type", "")
        val props = obj.optJSONObject("properties")?.toMapShallow() ?: emptyMap()
        val children = obj.optJSONArray("children")?.let { arr ->
            (0 until arr.length()).mapNotNull { idx ->
                arr.optJSONObject(idx)?.let { parseUiComponent(it) }
            }
        } ?: emptyList()
        return DeclarativeUiComponent(id = id, type = type, properties = props, children = children)
    }

    fun parseFlow(obj: JSONObject): DeclarativeFlowDefinition {
        val flowId = obj.optString("flowId", "")
        val triggers = obj.optJSONArray("triggers")?.let { arr ->
            (0 until arr.length()).mapNotNull { idx ->
                arr.optJSONObject(idx)?.let { t ->
                    DeclarativeTrigger(
                        type = t.optString("type", ""),
                        targetId = t.optNullableString("targetId"),
                        everyMs = if (t.has("everyMs")) t.optLong("everyMs") else null,
                        messageType = t.optNullableString("messageType"),
                        startNodeId = t.optString("startNodeId", ""),
                    )
                }
            }
        } ?: emptyList()

        val nodes = obj.optJSONArray("nodes")?.let { arr ->
            (0 until arr.length()).mapNotNull { idx ->
                arr.optJSONObject(idx)?.let { n ->
                    DeclarativeNode(
                        id = n.optString("id", ""),
                        type = n.optString("type", ""),
                        params = n.optJSONObject("params")?.toMapShallow() ?: emptyMap(),
                        next = n.optNullableString("next"),
                        nextTrue = n.optNullableString("nextTrue"),
                        nextFalse = n.optNullableString("nextFalse"),
                    )
                }
            }
        } ?: emptyList()

        return DeclarativeFlowDefinition(flowId = flowId, triggers = triggers, nodes = nodes)
    }

    private fun JSONArray.toStringList(): List<String> {
        return (0 until length()).mapNotNull { idx -> optString(idx, null) }
    }

    private fun JSONObject.toMapShallow(): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        val keys = keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = opt(key)
            out[key] = when (value) {
                JSONObject.NULL -> null
                else -> value
            }
        }
        return out
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val value = opt(key)
        if (value == JSONObject.NULL) return null
        // org.json can sometimes produce the literal string "null"; guard that.
        val s = when (value) {
            is String -> value
            else -> value.toString()
        }
        return s.takeIf { it != "null" }
    }
}

