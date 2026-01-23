// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.plugin.test

import android.os.Bundle
import com.ble1st.connectias.plugin.IPlugin
import com.ble1st.connectias.plugin.PluginContext
import com.ble1st.connectias.plugin.PluginMetadata
import com.ble1st.connectias.plugin.ui.*
import timber.log.Timber

/**
 * Test plugin for Three-Process UI Architecture.
 *
 * Demonstrates:
 * - UI state-based rendering via PluginUIBuilder DSL
 * - User action handling
 * - UI lifecycle events
 * - All supported UI components
 *
 * This plugin runs in the Sandbox Process and sends UI state to the UI Process
 * for rendering, showcasing the complete separation of business logic and UI.
 */
class TestUIPlugin : IPlugin {

    private var pluginContext: PluginContext? = null
    private var counter: Int = 0
    private var inputText: String = ""
    private var isChecked: Boolean = false
    private var selectedItemIndex: Int = -1

    override fun getMetadata(): PluginMetadata {
        return PluginMetadata(
            pluginId = "com.ble1st.connectias.plugin.test.ui",
            pluginName = "Test UI Plugin",
            version = "1.0.0",
            author = "Connectias Team",
            description = "Test plugin for Three-Process UI Architecture",
            minApiLevel = 33,
            maxApiLevel = 36,
            minAppVersion = "1.0.0",
            nativeLibraries = emptyList(),
            fragmentClassName = null, // No legacy fragment
            permissions = emptyList(),
            category = com.ble1st.connectias.plugin.PluginCategory.UTILITY,
            dependencies = emptyList()
        )
    }

    override fun onLoad(context: PluginContext): Boolean {
        Timber.i("[TEST_UI_PLUGIN] Loading plugin")
        pluginContext = context
        return true
    }

    override fun onEnable(): Boolean {
        Timber.i("[TEST_UI_PLUGIN] Plugin enabled")
        return true
    }

    override fun onDisable(): Boolean {
        Timber.i("[TEST_UI_PLUGIN] Plugin disabled")
        return true
    }

    override fun onUnload(): Boolean {
        Timber.i("[TEST_UI_PLUGIN] Plugin unloaded")
        pluginContext = null
        return true
    }

    /**
     * Renders UI state for the plugin.
     *
     * This method is called when the UI Process needs to render or update the UI.
     * We use the PluginUIBuilder DSL to create a declarative UI structure.
     */
    override fun onRenderUI(screenId: String): UIStateData? {
        Timber.d("[TEST_UI_PLUGIN] Rendering UI for screen: $screenId")

        return when (screenId) {
            "main" -> renderMainScreen()
            "components" -> renderComponentsScreen()
            else -> {
                Timber.w("[TEST_UI_PLUGIN] Unknown screen: $screenId")
                null
            }
        }
    }

    /**
     * Renders the main screen with interactive elements.
     */
    private fun renderMainScreen(): UIStateData {
        return buildPluginUI("main") {
            title("Test UI Plugin")

            text("Welcome to the Three-Process UI Architecture Test Plugin!", style = TextStyle.HEADLINE)

            spacer(16)

            text("Counter Demo", style = TextStyle.TITLE)
            text("Current count: $counter", style = TextStyle.BODY)

            row {
                button(
                    id = "btn_decrement",
                    text = "−",
                    variant = ButtonVariant.SECONDARY
                )
                button(
                    id = "btn_increment",
                    text = "+",
                    variant = ButtonVariant.PRIMARY
                )
            }

            spacer(16)

            text("Text Input Demo", style = TextStyle.TITLE)
            textField(
                id = "input_field",
                label = "Enter text",
                value = inputText,
                hint = "Type something..."
            )

            if (inputText.isNotEmpty()) {
                text("You typed: $inputText", style = TextStyle.CAPTION)
            }

            spacer(16)

            text("Checkbox Demo", style = TextStyle.TITLE)
            checkbox(
                id = "checkbox_demo",
                label = "Enable feature",
                checked = isChecked
            )

            text("Checkbox is: ${if (isChecked) "Checked" else "Unchecked"}", style = TextStyle.CAPTION)

            spacer(16)

            button(
                id = "btn_components",
                text = "Show All Components",
                variant = ButtonVariant.TEXT
            )

            button(
                id = "btn_reset",
                text = "Reset All",
                variant = ButtonVariant.SECONDARY
            )
        }
    }

    /**
     * Renders a screen showcasing all available components.
     */
    private fun renderComponentsScreen(): UIStateData {
        return buildPluginUI("components") {
            title("All Components")

            text("Component Showcase", style = TextStyle.HEADLINE)

            spacer(8)

            text("Buttons", style = TextStyle.TITLE)
            button(id = "btn_primary", text = "Primary Button", variant = ButtonVariant.PRIMARY)
            button(id = "btn_secondary", text = "Secondary Button", variant = ButtonVariant.SECONDARY)
            button(id = "btn_text", text = "Text Button", variant = ButtonVariant.TEXT)

            spacer(8)

            text("Text Styles", style = TextStyle.TITLE)
            text("Headline Text", style = TextStyle.HEADLINE)
            text("Title Text", style = TextStyle.TITLE)
            text("Body Text", style = TextStyle.BODY)
            text("Caption Text", style = TextStyle.CAPTION)

            spacer(8)

            text("List", style = TextStyle.TITLE)
            val items = listOf(
                ListItem(id = "item1", title = "First Item", subtitle = "Subtitle for first item"),
                ListItem(id = "item2", title = "Second Item", subtitle = "Subtitle for second item"),
                ListItem(id = "item3", title = "Third Item")
            )
            list(id = "demo_list", items = items)

            if (selectedItemIndex >= 0) {
                text("Selected item: ${selectedItemIndex + 1}", style = TextStyle.CAPTION)
            }

            spacer(8)

            text("Layouts", style = TextStyle.TITLE)

            column {
                text("Column Layout", style = TextStyle.BODY)
                text("Item 1", style = TextStyle.CAPTION)
                text("Item 2", style = TextStyle.CAPTION)
            }

            row {
                text("Row Layout")
                text("Item A")
                text("Item B")
            }

            spacer(16)

            button(
                id = "btn_back_main",
                text = "Back to Main",
                variant = ButtonVariant.SECONDARY
            )
        }
    }

    /**
     * Handles user actions from the UI Process.
     *
     * User actions are generated when users interact with the UI.
     * We update our internal state and can trigger a UI update.
     */
    override fun onUserAction(action: UserAction) {
        Timber.d("[TEST_UI_PLUGIN] User action: ${action.actionType} on ${action.targetId}")

        when (action.targetId) {
            "btn_increment" -> {
                counter++
                Timber.i("[TEST_UI_PLUGIN] Counter incremented to: $counter")
                // UI will re-render with updated counter
            }

            "btn_decrement" -> {
                counter--
                Timber.i("[TEST_UI_PLUGIN] Counter decremented to: $counter")
            }

            "input_field" -> {
                if (action.actionType == UserAction.ACTION_TEXT_CHANGED) {
                    inputText = action.data.getString("value") ?: ""
                    Timber.d("[TEST_UI_PLUGIN] Input text changed to: $inputText")
                }
            }

            "checkbox_demo" -> {
                if (action.actionType == "checkbox_changed") {
                    isChecked = action.data.getBoolean("checked", false)
                    Timber.i("[TEST_UI_PLUGIN] Checkbox changed to: $isChecked")
                }
            }

            "btn_reset" -> {
                counter = 0
                inputText = ""
                isChecked = false
                selectedItemIndex = -1
                Timber.i("[TEST_UI_PLUGIN] Reset all state")
            }

            "btn_components" -> {
                Timber.i("[TEST_UI_PLUGIN] Navigate to components screen")
                // In a real implementation, trigger navigation to "components" screen
                // For now, just log the action
            }

            "btn_back_main" -> {
                Timber.i("[TEST_UI_PLUGIN] Navigate back to main screen")
            }

            "demo_list" -> {
                if (action.actionType == UserAction.ACTION_ITEM_SELECTED) {
                    selectedItemIndex = action.data.getInt("position", -1)
                    val itemId = action.data.getString("itemId") ?: ""
                    Timber.i("[TEST_UI_PLUGIN] List item selected: $selectedItemIndex ($itemId)")
                }
            }

            else -> {
                Timber.w("[TEST_UI_PLUGIN] Unknown action target: ${action.targetId}")
            }
        }
    }

    /**
     * Handles UI lifecycle events.
     *
     * These events are sent from the UI Process when the plugin's UI
     * undergoes lifecycle changes.
     */
    override fun onUILifecycle(event: String) {
        Timber.d("[TEST_UI_PLUGIN] UI lifecycle event: $event")

        when (event) {
            UILifecycleEvent.ON_CREATE -> {
                Timber.i("[TEST_UI_PLUGIN] UI created")
                // Initialize UI state if needed
            }

            UILifecycleEvent.ON_RESUME -> {
                Timber.i("[TEST_UI_PLUGIN] UI resumed")
                // Resume any paused operations
            }

            UILifecycleEvent.ON_PAUSE -> {
                Timber.i("[TEST_UI_PLUGIN] UI paused")
                // Pause operations that don't need to run in background
            }

            UILifecycleEvent.ON_DESTROY -> {
                Timber.i("[TEST_UI_PLUGIN] UI destroyed")
                // Cleanup UI-related resources
            }
        }
    }

    override fun onPause() {
        Timber.d("[TEST_UI_PLUGIN] Plugin paused")
    }

    override fun onResume() {
        Timber.d("[TEST_UI_PLUGIN] Plugin resumed")
    }
}
