package com.ble1st.connectias.core.plugin.declarative

import com.ble1st.connectias.plugin.declarative.model.DeclarativeNode

/**
 * Central registry for all supported declarative workflow node types.
 *
 * This is the single source of truth for:
 * - Builder UI (generate parameter forms)
 * - Validator (fail-fast on unknown nodes / invalid parameters)
 * - Runtime governance (side effects, future quotas)
 */
object NodeRegistry {

    private val specs: Map<String, NodeSpec> = listOf(
        // Control flow
        NodeSpec(
            type = "IfElse",
            displayName = "If / Else",
            params = listOf(
                NodeSpec.ParamSpec(key = "left", label = "Left", type = NodeSpec.ParamType.ANY, required = true),
                NodeSpec.ParamSpec(
                    key = "op",
                    label = "Operator",
                    type = NodeSpec.ParamType.ENUM,
                    required = true,
                    allowedValues = listOf("==", "!=", ">", ">=", "<", "<=", "contains"),
                    defaultValue = "=="
                ),
                NodeSpec.ParamSpec(key = "right", label = "Right", type = NodeSpec.ParamType.ANY, required = true),
            )
        ),

        // State
        NodeSpec(
            type = "SetState",
            displayName = "Set State",
            params = listOf(
                NodeSpec.ParamSpec(key = "key", label = "Key", type = NodeSpec.ParamType.STRING, required = false, defaultValue = "result", maxLength = 80),
                // Default to Ping output to make "Ping → SetState → Toast" work out-of-the-box without any user input.
                NodeSpec.ParamSpec(key = "value", label = "Value", type = NodeSpec.ParamType.ANY, required = false, defaultValue = "state.lastPingLatencyMs"),
            ),
            sideEffects = setOf(NodeSpec.SideEffect.STORAGE),
        ),
        NodeSpec(
            type = "Increment",
            displayName = "Increment",
            params = listOf(
                NodeSpec.ParamSpec(key = "key", label = "Key", type = NodeSpec.ParamType.STRING, required = false, defaultValue = "counter", maxLength = 80),
                NodeSpec.ParamSpec(key = "delta", label = "Delta", type = NodeSpec.ParamType.LONG, required = false, defaultValue = 1L),
            ),
            sideEffects = setOf(NodeSpec.SideEffect.STORAGE),
        ),
        NodeSpec(
            type = "PersistState",
            displayName = "Persist State",
            sideEffects = setOf(NodeSpec.SideEffect.STORAGE),
        ),

        // UI
        NodeSpec(
            type = "ShowToast",
            displayName = "Show Toast",
            params = listOf(
                NodeSpec.ParamSpec(key = "message", label = "Message", type = NodeSpec.ParamType.STRING, required = false, defaultValue = "Done: {{result}}", maxLength = 512),
            ),
            sideEffects = setOf(NodeSpec.SideEffect.UI),
        ),
        NodeSpec(
            type = "ShowImage",
            displayName = "Show Image",
            params = listOf(
                NodeSpec.ParamSpec(
                    key = "source",
                    label = "Source",
                    type = NodeSpec.ParamType.ANY,
                    required = false,
                    defaultValue = "state.lastCapturedImage",
                    description = "Base64 image string or reference (e.g. state.lastCapturedImage)"
                ),
                NodeSpec.ParamSpec(
                    key = "imageKey",
                    label = "Image State Key",
                    type = NodeSpec.ParamType.STRING,
                    required = false,
                    defaultValue = "uiImageBase64",
                    maxLength = 80
                ),
                NodeSpec.ParamSpec(
                    key = "screenId",
                    label = "Screen ID",
                    type = NodeSpec.ParamType.STRING,
                    required = false,
                    defaultValue = "image",
                    maxLength = 80
                ),
            ),
            sideEffects = setOf(NodeSpec.SideEffect.UI),
        ),
        NodeSpec(
            type = "ShowDialog",
            displayName = "Show Dialog",
            params = listOf(
                NodeSpec.ParamSpec(key = "title", label = "Title", type = NodeSpec.ParamType.STRING, required = false, defaultValue = "Info", maxLength = 80),
                NodeSpec.ParamSpec(key = "message", label = "Message", type = NodeSpec.ParamType.STRING, required = true, maxLength = 1024),
            ),
            sideEffects = setOf(NodeSpec.SideEffect.UI),
        ),
        NodeSpec(
            type = "Navigate",
            displayName = "Navigate",
            params = listOf(
                NodeSpec.ParamSpec(key = "screenId", label = "Screen ID", type = NodeSpec.ParamType.STRING, required = false, defaultValue = "main", maxLength = 80),
            ),
            sideEffects = setOf(NodeSpec.SideEffect.UI),
        ),

        // Messaging
        NodeSpec(
            type = "EmitMessage",
            displayName = "Emit Message",
            params = listOf(
                NodeSpec.ParamSpec(key = "receiverId", label = "Receiver ID", type = NodeSpec.ParamType.STRING, required = false, maxLength = 80),
                NodeSpec.ParamSpec(key = "messageType", label = "Message Type", type = NodeSpec.ParamType.STRING, required = false, maxLength = 80),
            ),
            sideEffects = setOf(NodeSpec.SideEffect.MESSAGING),
        ),

        // Hardware
        NodeSpec(
            type = "CaptureImage",
            displayName = "Capture Image",
            params = listOf(
                NodeSpec.ParamSpec(key = "imageKey", label = "Image State Key", type = NodeSpec.ParamType.STRING, required = false, defaultValue = "lastCapturedImage", maxLength = 80),
                NodeSpec.ParamSpec(key = "statusKey", label = "Status Key", type = NodeSpec.ParamType.STRING, required = false, defaultValue = "captureStatus", maxLength = 80),
            ),
            sideEffects = setOf(NodeSpec.SideEffect.CAMERA),
        ),

        // Network (MVP: GET only)
        NodeSpec(
            type = "Curl",
            displayName = "HTTP GET (Curl)",
            params = listOf(
                NodeSpec.ParamSpec(key = "url", label = "URL", type = NodeSpec.ParamType.STRING, required = false, defaultValue = "https://httpbin.org/get", maxLength = 2048),
                NodeSpec.ParamSpec(key = "responseKey", label = "Body Key", type = NodeSpec.ParamType.STRING, required = false, defaultValue = "lastCurlBody", maxLength = 80),
                NodeSpec.ParamSpec(key = "statusKey", label = "Status Key", type = NodeSpec.ParamType.STRING, required = false, defaultValue = "lastCurlStatus", maxLength = 80),
                NodeSpec.ParamSpec(key = "contentTypeKey", label = "Content-Type Key", type = NodeSpec.ParamType.STRING, required = false, maxLength = 80),
                NodeSpec.ParamSpec(key = "timeoutMs", label = "Timeout (ms)", type = NodeSpec.ParamType.LONG, required = false, defaultValue = 5000L, min = 250.0, max = 30_000.0),
                NodeSpec.ParamSpec(key = "maxBytes", label = "Max Bytes", type = NodeSpec.ParamType.LONG, required = false, defaultValue = 512_000L, min = 1024.0, max = 5_000_000.0),
            ),
            sideEffects = setOf(NodeSpec.SideEffect.NETWORK),
        ),
        NodeSpec(
            type = "Ping",
            displayName = "TCP Ping",
            params = listOf(
                NodeSpec.ParamSpec(key = "host", label = "Host", type = NodeSpec.ParamType.STRING, required = false, defaultValue = "google.com", maxLength = 255),
                NodeSpec.ParamSpec(key = "port", label = "Port", type = NodeSpec.ParamType.LONG, required = false, defaultValue = 443L, min = 1.0, max = 65535.0),
                NodeSpec.ParamSpec(key = "timeoutMs", label = "Timeout (ms)", type = NodeSpec.ParamType.LONG, required = false, defaultValue = 1500L, min = 250.0, max = 30_000.0),
                NodeSpec.ParamSpec(key = "okKey", label = "OK Key", type = NodeSpec.ParamType.STRING, required = false, defaultValue = "lastPingOk", maxLength = 80),
                NodeSpec.ParamSpec(key = "latencyKey", label = "Latency Key", type = NodeSpec.ParamType.STRING, required = false, defaultValue = "lastPingLatencyMs", maxLength = 80),
            ),
            sideEffects = setOf(NodeSpec.SideEffect.NETWORK),
        ),

        // Dataflow (Items)
        NodeSpec(
            type = "SetField",
            displayName = "Set Field (Items)",
            params = listOf(
                NodeSpec.ParamSpec(key = "field", label = "Field", type = NodeSpec.ParamType.STRING, required = false, defaultValue = "foo", maxLength = 80),
                NodeSpec.ParamSpec(key = "value", label = "Value", type = NodeSpec.ParamType.ANY, required = false, defaultValue = "bar"),
            )
        ),
        NodeSpec(
            type = "Filter",
            displayName = "Filter Items",
            params = listOf(
                NodeSpec.ParamSpec(key = "left", label = "Left", type = NodeSpec.ParamType.ANY, required = true),
                NodeSpec.ParamSpec(
                    key = "op",
                    label = "Operator",
                    type = NodeSpec.ParamType.ENUM,
                    required = true,
                    allowedValues = listOf("==", "!=", ">", ">=", "<", "<=", "contains"),
                    defaultValue = "=="
                ),
                NodeSpec.ParamSpec(key = "right", label = "Right", type = NodeSpec.ParamType.ANY, required = true),
            )
        ),
    ).associateBy { it.type }

    fun list(): List<NodeSpec> = specs.values.sortedBy { it.displayName }

    fun get(type: String): NodeSpec? = specs[type]

    data class ParamValidation(
        val ok: Boolean,
        val errors: List<String> = emptyList(),
        val warnings: List<String> = emptyList(),
    )

    fun validateNode(node: DeclarativeNode): ParamValidation {
        val spec = get(node.type)
            ?: return ParamValidation(ok = false, errors = listOf("Unknown node type: ${node.type}"))
        return validateParams(spec, node.params)
    }

    fun validateParams(spec: NodeSpec, params: Map<String, Any?>): ParamValidation {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val byKey = spec.params.associateBy { it.key }

        // Required keys (only if no default value exists)
        spec.params.filter { it.required && it.defaultValue == null }.forEach { p ->
            if (!params.containsKey(p.key) || params[p.key] == null) {
                errors += "Missing required param '${p.key}' for node type '${spec.type}'"
            }
        }

        // Unknown keys
        params.keys.forEach { k ->
            if (!byKey.containsKey(k)) {
                warnings += "Unknown param '$k' for node type '${spec.type}'"
            }
        }

        // Type + constraints
        spec.params.forEach { p ->
            if (!params.containsKey(p.key)) return@forEach
            val value = params[p.key]
            if (value == null) return@forEach

            when (p.type) {
                NodeSpec.ParamType.ANY -> Unit
                NodeSpec.ParamType.STRING -> {
                    val s = value as? String ?: value.toString()
                    if (p.maxLength != null && s.length > p.maxLength) {
                        errors += "Param '${p.key}' exceeds maxLength=${p.maxLength}"
                    }
                }
                NodeSpec.ParamType.BOOLEAN -> {
                    val ok = value is Boolean || value.toString().equals("true", true) || value.toString().equals("false", true)
                    if (!ok) errors += "Param '${p.key}' must be boolean"
                }
                NodeSpec.ParamType.LONG -> {
                    val n = value as? Number ?: value.toString().toLongOrNull()
                    if (n == null) {
                        errors += "Param '${p.key}' must be integer"
                    } else {
                        val d = n.toDouble()
                        if (p.min != null && d < p.min) errors += "Param '${p.key}' must be >= ${p.min}"
                        if (p.max != null && d > p.max) errors += "Param '${p.key}' must be <= ${p.max}"
                    }
                }
                NodeSpec.ParamType.DOUBLE -> {
                    val n = value as? Number ?: value.toString().toDoubleOrNull()
                    if (n == null) {
                        errors += "Param '${p.key}' must be number"
                    } else {
                        val d = n.toDouble()
                        if (p.min != null && d < p.min) errors += "Param '${p.key}' must be >= ${p.min}"
                        if (p.max != null && d > p.max) errors += "Param '${p.key}' must be <= ${p.max}"
                    }
                }
                NodeSpec.ParamType.ENUM -> {
                    val s = value.toString()
                    val allowed = p.allowedValues
                    if (allowed != null && !allowed.contains(s)) {
                        errors += "Param '${p.key}' must be one of ${allowed.joinToString(", ")}"
                    }
                }
            }
        }

        return ParamValidation(ok = errors.isEmpty(), errors = errors, warnings = warnings)
    }
}

