// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.plugin.ui

import android.os.Bundle

/**
 * DSL für einfaches UI-Building in Plugins.
 *
 * Plugins nutzen diesen Builder, um UI-State deklarativ zu erstellen.
 * Der Builder erzeugt UIStateParcel-Objekte, die an den UI-Process
 * gesendet werden für state-basiertes Rendering.
 *
 * Beispiel:
 * ```
 * val uiState = buildPluginUI("main_screen") {
 *     title("My Plugin")
 *
 *     text("Welcome to my plugin!", style = TextStyle.HEADLINE)
 *
 *     button(
 *         id = "action_btn",
 *         text = "Click Me",
 *         enabled = true
 *     )
 *
 *     textField(
 *         id = "input_field",
 *         label = "Enter text",
 *         hint = "Type something..."
 *     )
 *
 *     column {
 *         text("Item 1")
 *         text("Item 2")
 *     }
 * }
 * ```
 */
class PluginUIBuilder(private val screenId: String) {

    private val components = mutableListOf<UIComponentData>()
    private var titleText: String = ""
    private val screenData = Bundle()

    /**
     * Sets the screen title.
     */
    fun title(text: String) {
        this.titleText = text
    }

    /**
     * Adds data to the screen state.
     */
    fun data(key: String, value: Any) {
        when (value) {
            is String -> screenData.putString(key, value)
            is Int -> screenData.putInt(key, value)
            is Long -> screenData.putLong(key, value)
            is Float -> screenData.putFloat(key, value)
            is Double -> screenData.putDouble(key, value)
            is Boolean -> screenData.putBoolean(key, value)
            is Bundle -> screenData.putBundle(key, value)
            else -> throw IllegalArgumentException("Unsupported data type: ${value::class.simpleName}")
        }
    }

    /**
     * Adds a button component.
     *
     * @param id Unique component ID
     * @param text Button text
     * @param enabled Whether button is enabled
     * @param variant Button style variant (PRIMARY, SECONDARY, TEXT)
     */
    fun button(
        id: String,
        text: String,
        enabled: Boolean = true,
        variant: ButtonVariant = ButtonVariant.PRIMARY
    ) {
        val properties = Bundle().apply {
            putString("text", text)
            putBoolean("enabled", enabled)
            putString("variant", variant.name)
        }

        components.add(
            UIComponentData(
                id = id,
                type = ComponentType.BUTTON.name,
                properties = properties,
                children = emptyList()
            )
        )
    }

    /**
     * Adds a text field component.
     *
     * @param id Unique component ID
     * @param label Field label
     * @param value Current value
     * @param hint Placeholder hint
     * @param enabled Whether field is enabled
     * @param multiline Whether field supports multiple lines
     */
    fun textField(
        id: String,
        label: String,
        value: String = "",
        hint: String = "",
        enabled: Boolean = true,
        multiline: Boolean = false
    ) {
        val properties = Bundle().apply {
            putString("label", label)
            putString("value", value)
            putString("hint", hint)
            putBoolean("enabled", enabled)
            putBoolean("multiline", multiline)
        }

        components.add(
            UIComponentData(
                id = id,
                type = ComponentType.TEXT_FIELD.name,
                properties = properties,
                children = emptyList()
            )
        )
    }

    /**
     * Adds a text component.
     *
     * @param text Text content
     * @param style Text style variant
     */
    fun text(text: String, style: TextStyle = TextStyle.BODY) {
        val properties = Bundle().apply {
            putString("text", text)
            putString("style", style.name)
        }

        components.add(
            UIComponentData(
                id = "text_${components.size}",
                type = ComponentType.TEXT_VIEW.name,
                properties = properties,
                children = emptyList()
            )
        )
    }

    /**
     * Adds a list component.
     *
     * @param id Unique component ID
     * @param items List items
     */
    fun list(
        id: String,
        items: List<ListItem>
    ) {
        val properties = Bundle().apply {
            putInt("itemCount", items.size)

            // Serialize items as parallel arrays for efficient IPC
            val titles = items.map { it.title }.toTypedArray()
            val subtitles = items.map { it.subtitle ?: "" }.toTypedArray()
            val itemIds = items.map { it.id }.toTypedArray()

            putStringArray("item_titles", titles)
            putStringArray("item_subtitles", subtitles)
            putStringArray("item_ids", itemIds)
        }

        components.add(
            UIComponentData(
                id = id,
                type = ComponentType.LIST.name,
                properties = properties,
                children = emptyList()
            )
        )
    }

    /**
     * Adds an image component.
     *
     * @param id Unique component ID
     * @param url Image URL or resource identifier
     * @param contentDescription Accessibility description
     */
    fun image(
        id: String,
        url: String,
        contentDescription: String = ""
    ) {
        val properties = Bundle().apply {
            putString("url", url)
            putString("contentDescription", contentDescription)
        }

        components.add(
            UIComponentData(
                id = id,
                type = ComponentType.IMAGE.name,
                properties = properties,
                children = emptyList()
            )
        )
    }

    /**
     * Adds a checkbox component.
     *
     * @param id Unique component ID
     * @param label Checkbox label
     * @param checked Whether checkbox is checked
     * @param enabled Whether checkbox is enabled
     */
    fun checkbox(
        id: String,
        label: String,
        checked: Boolean = false,
        enabled: Boolean = true
    ) {
        val properties = Bundle().apply {
            putString("label", label)
            putBoolean("checked", checked)
            putBoolean("enabled", enabled)
        }

        components.add(
            UIComponentData(
                id = id,
                type = ComponentType.CHECKBOX.name,
                properties = properties,
                children = emptyList()
            )
        )
    }

    /**
     * Adds a vertical column layout.
     *
     * Components inside the builder will be arranged vertically.
     */
    fun column(builder: PluginUIBuilder.() -> Unit) {
        val columnBuilder = PluginUIBuilder("column")
        columnBuilder.builder()

        components.add(
            UIComponentData(
                id = "column_${components.size}",
                type = ComponentType.COLUMN.name,
                properties = Bundle(),
                children = columnBuilder.components
            )
        )
    }

    /**
     * Adds a horizontal row layout.
     *
     * Components inside the builder will be arranged horizontally.
     */
    fun row(builder: PluginUIBuilder.() -> Unit) {
        val rowBuilder = PluginUIBuilder("row")
        rowBuilder.builder()

        components.add(
            UIComponentData(
                id = "row_${components.size}",
                type = ComponentType.ROW.name,
                properties = Bundle(),
                children = rowBuilder.components
            )
        )
    }

    /**
     * Adds a spacer component for layout spacing.
     *
     * @param height Spacer height in dp (default: 8)
     */
    fun spacer(height: Int = 8) {
        val properties = Bundle().apply {
            putInt("height", height)
        }

        components.add(
            UIComponentData(
                id = "spacer_${components.size}",
                type = ComponentType.SPACER.name,
                properties = properties,
                children = emptyList()
            )
        )
    }

    /**
     * Builds the final UIStateData object.
     *
     * This is called internally by buildPluginUI() helper function.
     */
    internal fun build(): UIStateData {
        return UIStateData(
            screenId = screenId,
            title = titleText,
            data = screenData,
            components = components,
            timestamp = System.currentTimeMillis()
        )
    }
}

/**
 * Data class representing UI state (SDK-side).
 * Will be converted to UIStateParcel for IPC.
 */
data class UIStateData(
    val screenId: String,
    val title: String,
    val data: Bundle,
    val components: List<UIComponentData>,
    val timestamp: Long
)

/**
 * Data class representing a UI component (SDK-side).
 * Will be converted to UIComponentParcel for IPC.
 */
data class UIComponentData(
    val id: String,
    val type: String,
    val properties: Bundle,
    val children: List<UIComponentData>
)

/**
 * Component types supported by the UI builder.
 */
enum class ComponentType {
    BUTTON,
    TEXT_FIELD,
    TEXT_VIEW,
    LIST,
    IMAGE,
    CHECKBOX,
    COLUMN,
    ROW,
    SPACER
}

/**
 * Button style variants.
 */
enum class ButtonVariant {
    PRIMARY,    // Filled button
    SECONDARY,  // Outlined button
    TEXT        // Text-only button
}

/**
 * Text style variants.
 */
enum class TextStyle {
    HEADLINE,   // Large, bold text
    TITLE,      // Medium, semi-bold text
    BODY,       // Normal text
    CAPTION     // Small text
}

/**
 * Represents a list item.
 */
data class ListItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val data: Bundle = Bundle()
)

/**
 * Helper function for building plugin UI with DSL.
 *
 * Example:
 * ```
 * val uiState = buildPluginUI("main_screen") {
 *     title("My Plugin")
 *     text("Hello World!", style = TextStyle.HEADLINE)
 *     button(id = "btn1", text = "Click Me")
 * }
 * ```
 */
fun buildPluginUI(screenId: String, builder: PluginUIBuilder.() -> Unit): UIStateData {
    return PluginUIBuilder(screenId).apply(builder).build()
}
