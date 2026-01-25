package com.ble1st.connectias.plugin.declarative.model

/**
 * Validation utilities for declarative plugin packages.
 *
 * This is intentionally strict (fail-fast) to keep the low-code surface safe.
 */
object DeclarativePluginValidator {

    data class ValidationResult(
        val ok: Boolean,
        val errors: List<String> = emptyList(),
        val warnings: List<String> = emptyList(),
    )

    fun validateManifest(manifest: DeclarativePluginManifest): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (manifest.schemaVersion !in DeclarativeSchemaVersions.supported) {
            errors += "Unsupported schemaVersion=${manifest.schemaVersion}"
        }

        if (manifest.pluginType != "declarative") {
            errors += "pluginType must be \"declarative\""
        }

        if (!isValidId(manifest.pluginId)) {
            errors += "Invalid pluginId (allowed: [a-z0-9._-], 3..80)"
        }

        if (manifest.pluginName.isBlank() || manifest.pluginName.length > 80) {
            errors += "Invalid pluginName (required, max 80 chars)"
        }

        if (manifest.versionCode <= 0) {
            errors += "versionCode must be > 0"
        }

        if (manifest.versionName.isBlank() || manifest.versionName.length > 32) {
            errors += "Invalid versionName (required, max 32 chars)"
        }

        if (!isValidId(manifest.developerId)) {
            errors += "Invalid developerId (allowed: [a-z0-9._-], 3..80)"
        }

        if (manifest.entrypoints.startScreenId.isBlank()) {
            errors += "entrypoints.startScreenId is required"
        }

        if (manifest.entrypoints.screens.isEmpty()) {
            errors += "entrypoints.screens must contain at least one ui json"
        }

        if (manifest.entrypoints.flows.isEmpty()) {
            warnings += "entrypoints.flows is empty (no workflows)"
        }

        manifest.entrypoints.screens.forEach { path ->
            if (!path.startsWith("ui/") || !path.endsWith(".json")) {
                errors += "Invalid screen path: $path (must be ui/*.json)"
            }
        }

        manifest.entrypoints.flows.forEach { path ->
            if (!path.startsWith("flows/") || !path.endsWith(".json")) {
                errors += "Invalid flow path: $path (must be flows/*.json)"
            }
        }

        // Basic capability validation (host will enforce)
        manifest.capabilities.forEach { cap ->
            if (cap.isBlank() || cap.length > 64) {
                errors += "Invalid capability entry"
            }
        }

        // Permissions (optional). Prefer android.permission.* for host enforcement.
        manifest.permissions.forEach { p ->
            if (p.isBlank() || p.length > 128) {
                errors += "Invalid permission entry"
            } else if (!p.startsWith("android.permission.")) {
                warnings += "Non-Android permission declared (may not be enforceable): $p"
            }
        }

        return ValidationResult(ok = errors.isEmpty(), errors = errors, warnings = warnings)
    }

    fun validateUiScreen(screen: DeclarativeUiScreen): ValidationResult {
        val errors = mutableListOf<String>()

        if (screen.screenId.isBlank()) errors += "screenId is required"
        if (screen.title.isBlank()) errors += "title is required"

        val ids = HashSet<String>()
        fun validateComponent(c: DeclarativeUiComponent) {
            if (c.id.isBlank()) errors += "component.id is required"
            if (c.type.isBlank()) errors += "component.type is required"
            if (c.id.isNotBlank() && !ids.add(c.id)) errors += "duplicate component id: ${c.id}"
            c.children.forEach { validateComponent(it) }
        }
        screen.components.forEach { validateComponent(it) }

        return ValidationResult(ok = errors.isEmpty(), errors = errors)
    }

    fun validateFlow(flow: DeclarativeFlowDefinition): ValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (flow.flowId.isBlank()) errors += "flowId is required"
        if (flow.triggers.isEmpty()) warnings += "no triggers configured"

        val nodeMap = flow.nodes.associateBy { it.id }
        if (nodeMap.size != flow.nodes.size) {
            errors += "duplicate node ids"
        }

        flow.triggers.forEach { t ->
            if (t.type.isBlank()) errors += "trigger.type is required"
            if (t.startNodeId.isBlank()) errors += "trigger.startNodeId is required"
            if (t.startNodeId.isNotBlank() && nodeMap[t.startNodeId] == null) {
                errors += "trigger.startNodeId not found: ${t.startNodeId}"
            }
        }

        flow.nodes.forEach { n ->
            if (n.id.isBlank()) errors += "node.id is required"
            if (n.type.isBlank()) errors += "node.type is required"
            n.next?.let { if (nodeMap[it] == null) errors += "node.next not found: ${n.id} -> $it" }
            n.nextTrue?.let { if (nodeMap[it] == null) errors += "node.nextTrue not found: ${n.id} -> $it" }
            n.nextFalse?.let { if (nodeMap[it] == null) errors += "node.nextFalse not found: ${n.id} -> $it" }
            if (n.type == "IfElse") {
                if (n.nextTrue == null || n.nextFalse == null) {
                    errors += "IfElse requires nextTrue and nextFalse: ${n.id}"
                }
            }
        }

        return ValidationResult(ok = errors.isEmpty(), errors = errors, warnings = warnings)
    }

    private fun isValidId(value: String): Boolean {
        if (value.length !in 3..80) return false
        return value.all { it.isLowerCase() || it.isDigit() || it == '.' || it == '_' || it == '-' }
    }
}

