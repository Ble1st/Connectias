package com.ble1st.connectias.core.plugin.declarative

/**
 * Metadata for declarative workflow nodes (host-curated).
 *
 * SECURITY:
 * - Declarative packages must never ship arbitrary executable code.
 * - All node behavior is implemented in trusted host code and constrained by NodeSpec validation.
 */
data class NodeSpec(
    val type: String,
    val displayName: String,
    val version: Int = 1,
    val params: List<ParamSpec> = emptyList(),
    val sideEffects: Set<SideEffect> = emptySet(),
) {
    data class ParamSpec(
        val key: String,
        val label: String = key,
        val type: ParamType,
        val required: Boolean = false,
        val defaultValue: Any? = null,
        val allowedValues: List<String>? = null,
        val min: Double? = null,
        val max: Double? = null,
        val maxLength: Int? = null,
        val description: String? = null,
    )

    enum class ParamType {
        STRING,
        BOOLEAN,
        LONG,
        DOUBLE,
        ENUM,
        ANY,
    }

    enum class SideEffect {
        UI,
        NETWORK,
        CAMERA,
        STORAGE,
        MESSAGING,
    }
}

