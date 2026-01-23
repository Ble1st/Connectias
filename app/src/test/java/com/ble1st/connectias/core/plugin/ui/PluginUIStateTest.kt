// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.core.plugin.ui

import android.os.Bundle
import android.os.Parcel
import com.ble1st.connectias.plugin.ui.UIComponentParcel
import com.ble1st.connectias.plugin.ui.UIStateParcel
import com.ble1st.connectias.plugin.ui.UserActionParcel
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for UI State Parcels.
 *
 * Tests serialization, deserialization, and data integrity of Parcelable objects
 * used in the Three-Process UI Architecture.
 */
class PluginUIStateTest {

    /**
     * Helper function to test Parcelable serialization/deserialization.
     */
    private inline fun <reified T : android.os.Parcelable> testParcelable(
        parcelable: T,
        creator: android.os.Parcelable.Creator<T>
    ): T {
        val parcel = Parcel.obtain()
        try {
            parcelable.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            return creator.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }

    @Test
    fun `UIStateParcel serialization and deserialization`() {
        // Create a UI state with components
        val originalState = UIStateParcel().apply {
            screenId = "test_screen"
            title = "Test Title"
            data = Bundle().apply {
                putString("key1", "value1")
                putInt("key2", 42)
            }
            components = arrayOf(
                UIComponentParcel().apply {
                    id = "button1"
                    type = "BUTTON"
                    properties = Bundle().apply {
                        putString("text", "Click Me")
                        putBoolean("enabled", true)
                    }
                    children = emptyArray()
                }
            )
            timestamp = System.currentTimeMillis()
        }

        // Serialize and deserialize
        val deserializedState = testParcelable(originalState, UIStateParcel.CREATOR)

        // Verify
        assertEquals(originalState.screenId, deserializedState.screenId)
        assertEquals(originalState.title, deserializedState.title)
        assertEquals(originalState.data.getString("key1"), deserializedState.data.getString("key1"))
        assertEquals(originalState.data.getInt("key2"), deserializedState.data.getInt("key2"))
        assertEquals(originalState.components.size, deserializedState.components.size)
        assertEquals(originalState.timestamp, deserializedState.timestamp)
    }

    @Test
    fun `UIComponentParcel with nested children`() {
        // Create nested component structure (column with buttons)
        val originalComponent = UIComponentParcel().apply {
            id = "column1"
            type = "COLUMN"
            properties = Bundle()
            children = arrayOf(
                UIComponentParcel().apply {
                    id = "button1"
                    type = "BUTTON"
                    properties = Bundle().apply {
                        putString("text", "Button 1")
                    }
                    children = emptyArray()
                },
                UIComponentParcel().apply {
                    id = "button2"
                    type = "BUTTON"
                    properties = Bundle().apply {
                        putString("text", "Button 2")
                    }
                    children = emptyArray()
                }
            )
        }

        // Serialize and deserialize
        val deserializedComponent = testParcelable(originalComponent, UIComponentParcel.CREATOR)

        // Verify structure
        assertEquals(originalComponent.id, deserializedComponent.id)
        assertEquals(originalComponent.type, deserializedComponent.type)
        assertEquals(originalComponent.children.size, deserializedComponent.children.size)

        // Verify children
        assertEquals("button1", deserializedComponent.children[0].id)
        assertEquals("BUTTON", deserializedComponent.children[0].type)
        assertEquals("Button 1", deserializedComponent.children[0].properties.getString("text"))

        assertEquals("button2", deserializedComponent.children[1].id)
        assertEquals("Button 2", deserializedComponent.children[1].properties.getString("text"))
    }

    @Test
    fun `UserActionParcel serialization`() {
        val originalAction = UserActionParcel().apply {
            actionType = "click"
            targetId = "button_submit"
            data = Bundle().apply {
                putString("meta", "test_data")
                putBoolean("confirmed", true)
            }
            timestamp = System.currentTimeMillis()
        }

        val deserializedAction = testParcelable(originalAction, UserActionParcel.CREATOR)

        assertEquals(originalAction.actionType, deserializedAction.actionType)
        assertEquals(originalAction.targetId, deserializedAction.targetId)
        assertEquals(originalAction.data.getString("meta"), deserializedAction.data.getString("meta"))
        assertEquals(originalAction.data.getBoolean("confirmed"), deserializedAction.data.getBoolean("confirmed"))
        assertEquals(originalAction.timestamp, deserializedAction.timestamp)
    }

    @Test
    fun `Empty UIStateParcel`() {
        val emptyState = UIStateParcel().apply {
            screenId = ""
            title = ""
            data = Bundle.EMPTY
            components = emptyArray()
            timestamp = 0
        }

        val deserialized = testParcelable(emptyState, UIStateParcel.CREATOR)

        assertEquals("", deserialized.screenId)
        assertEquals("", deserialized.title)
        assertTrue(deserialized.data.isEmpty)
        assertEquals(0, deserialized.components.size)
        assertEquals(0, deserialized.timestamp)
    }

    @Test
    fun `UIComponentParcel with all property types`() {
        val component = UIComponentParcel().apply {
            id = "complex_component"
            type = "TEXT_FIELD"
            properties = Bundle().apply {
                putString("label", "Username")
                putBoolean("enabled", true)
                putInt("maxLength", 50)
                putFloat("fontSize", 14f)
                putLong("timestamp", System.currentTimeMillis())
            }
            children = emptyArray()
        }

        val deserialized = testParcelable(component, UIComponentParcel.CREATOR)

        assertEquals("Username", deserialized.properties.getString("label"))
        assertEquals(true, deserialized.properties.getBoolean("enabled"))
        assertEquals(50, deserialized.properties.getInt("maxLength"))
        assertEquals(14f, deserialized.properties.getFloat("fontSize"), 0.001f)
        assertTrue(deserialized.properties.getLong("timestamp") > 0)
    }

    @Test
    fun `Multiple component types in UIStateParcel`() {
        val state = UIStateParcel().apply {
            screenId = "multi_component"
            title = "Multiple Components"
            data = Bundle.EMPTY
            components = arrayOf(
                UIComponentParcel().apply {
                    id = "text1"
                    type = "TEXT_VIEW"
                    properties = Bundle().apply { putString("text", "Hello") }
                    children = emptyArray()
                },
                UIComponentParcel().apply {
                    id = "button1"
                    type = "BUTTON"
                    properties = Bundle().apply { putString("text", "Click") }
                    children = emptyArray()
                },
                UIComponentParcel().apply {
                    id = "input1"
                    type = "TEXT_FIELD"
                    properties = Bundle().apply { putString("label", "Enter") }
                    children = emptyArray()
                },
                UIComponentParcel().apply {
                    id = "checkbox1"
                    type = "CHECKBOX"
                    properties = Bundle().apply {
                        putString("label", "Agree")
                        putBoolean("checked", false)
                    }
                    children = emptyArray()
                }
            )
            timestamp = System.currentTimeMillis()
        }

        val deserialized = testParcelable(state, UIStateParcel.CREATOR)

        assertEquals(4, deserialized.components.size)
        assertEquals("TEXT_VIEW", deserialized.components[0].type)
        assertEquals("BUTTON", deserialized.components[1].type)
        assertEquals("TEXT_FIELD", deserialized.components[2].type)
        assertEquals("CHECKBOX", deserialized.components[3].type)
    }

    @Test
    fun `UserActionParcel for different action types`() {
        val clickAction = UserActionParcel().apply {
            actionType = "click"
            targetId = "btn1"
            data = Bundle.EMPTY
            timestamp = System.currentTimeMillis()
        }

        val textAction = UserActionParcel().apply {
            actionType = "text_changed"
            targetId = "input1"
            data = Bundle().apply { putString("value", "test") }
            timestamp = System.currentTimeMillis()
        }

        val itemAction = UserActionParcel().apply {
            actionType = "item_selected"
            targetId = "list1"
            data = Bundle().apply {
                putInt("position", 2)
                putString("itemId", "item_2")
            }
            timestamp = System.currentTimeMillis()
        }

        // Verify all action types work
        val deserializedClick = testParcelable(clickAction, UserActionParcel.CREATOR)
        val deserializedText = testParcelable(textAction, UserActionParcel.CREATOR)
        val deserializedItem = testParcelable(itemAction, UserActionParcel.CREATOR)

        assertEquals("click", deserializedClick.actionType)
        assertEquals("text_changed", deserializedText.actionType)
        assertEquals("item_selected", deserializedItem.actionType)

        assertEquals("test", deserializedText.data.getString("value"))
        assertEquals(2, deserializedItem.data.getInt("position"))
    }
}
