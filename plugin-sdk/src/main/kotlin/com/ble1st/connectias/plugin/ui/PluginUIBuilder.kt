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

    internal val components = mutableListOf<UIComponentParcel>()
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
            UIComponentParcel().apply {
                this.id = id
                type = ComponentType.BUTTON.name
                this.properties = properties
                children = emptyArray()
            }
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
            UIComponentParcel().apply {
                this.id = id
                type = ComponentType.TEXT_FIELD.name
                this.properties = properties
                children = emptyArray()
            }
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
            UIComponentParcel().apply {
                id = "text_${components.size}"
                type = ComponentType.TEXT_VIEW.name
                this.properties = properties
                children = emptyArray()
            }
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
            UIComponentParcel().apply {
                this.id = id
                type = ComponentType.LIST.name
                this.properties = properties
                children = emptyArray()
            }
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
            UIComponentParcel().apply {
                this.id = id
                type = ComponentType.IMAGE.name
                this.properties = properties
                children = emptyArray()
            }
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
            UIComponentParcel().apply {
                this.id = id
                type = ComponentType.CHECKBOX.name
                this.properties = properties
                children = emptyArray()
            }
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
            UIComponentParcel().apply {
                id = "column_${components.size}"
                type = ComponentType.COLUMN.name
                properties = Bundle()
                children = columnBuilder.components.toTypedArray()
            }
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
            UIComponentParcel().apply {
                id = "row_${components.size}"
                type = ComponentType.ROW.name
                properties = Bundle()
                children = rowBuilder.components.toTypedArray()
            }
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
            UIComponentParcel().apply {
                id = "spacer_${components.size}"
                type = ComponentType.SPACER.name
                this.properties = properties
                children = emptyArray()
            }
        )
    }

    /**
     * Builds the final UIStateParcel object.
     *
     * This is called internally by buildPluginUI() helper function.
     */
    internal fun build(): UIStateParcel {
        return UIStateParcel().apply {
            this.screenId = this@PluginUIBuilder.screenId
            title = titleText
            data = screenData
            components = this@PluginUIBuilder.components.toTypedArray()
            timestamp = System.currentTimeMillis()
        }
    }
}

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
    PRIMARY, // Filled button
    SECONDARY, // Outlined button
    TEXT // Text-only button
}

/**
 * Text style variants.
 */
enum class TextStyle {
    HEADLINE, // Large, bold text
    TITLE, // Medium, semi-bold text
    BODY, // Normal text
    CAPTION // Small text
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
fun buildPluginUI(screenId: String, builder: PluginUIBuilder.() -> Unit): UIStateParcel {
    return PluginUIBuilder(screenId).apply(builder).build()
}

