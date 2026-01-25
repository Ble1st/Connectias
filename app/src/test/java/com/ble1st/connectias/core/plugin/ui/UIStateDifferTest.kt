// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.core.plugin.ui

import android.os.Bundle
import com.ble1st.connectias.plugin.ui.UIComponentParcel
import com.ble1st.connectias.plugin.ui.UIStateParcel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for UIStateDiffer.
 *
 * Tests state diffing logic and performance optimization.
 */
@RunWith(RobolectricTestRunner::class)
class UIStateDifferTest {

    @Test
    fun `diff with null old state returns full change`() {
        val newState = createSimpleState("screen1", "Title", 1)

        val diff = UIStateDiffer.diff(null, newState)

        assertTrue(diff.hasChanges)
        assertTrue(diff.titleChanged)
        assertTrue(diff.dataChanged)
        assertTrue(diff.componentsChanged)
        assertEquals(1, diff.addedComponentIds.size)
        assertEquals(0, diff.removedComponentIds.size)
        assertEquals(0, diff.changedComponentIds.size)
        assertEquals(0f, diff.estimatedPayloadReduction, 0.01f)
    }

    @Test
    fun `diff with identical states returns no changes`() {
        val state1 = createSimpleState("screen1", "Title", 1)
        val state2 = createSimpleState("screen1", "Title", 1)

        val diff = UIStateDiffer.diff(state1, state2)

        assertFalse(diff.hasChanges)
        assertFalse(diff.titleChanged)
        assertFalse(diff.dataChanged)
        assertFalse(diff.componentsChanged)
        assertEquals(0, diff.addedComponentIds.size)
        assertEquals(0, diff.removedComponentIds.size)
        assertEquals(0, diff.changedComponentIds.size)
        assertEquals(1f, diff.estimatedPayloadReduction, 0.01f)
    }

    @Test
    fun `diff with different screen IDs returns full change`() {
        val state1 = createSimpleState("screen1", "Title", 1)
        val state2 = createSimpleState("screen2", "Title", 1)

        val diff = UIStateDiffer.diff(state1, state2)

        assertTrue(diff.hasChanges)
        assertTrue(diff.titleChanged)
        assertTrue(diff.dataChanged)
        assertTrue(diff.componentsChanged)
        assertEquals(0f, diff.estimatedPayloadReduction, 0.01f)
    }

    @Test
    fun `diff with changed title only`() {
        val state1 = createSimpleState("screen1", "Title 1", 1)
        val state2 = createSimpleState("screen1", "Title 2", 1)

        val diff = UIStateDiffer.diff(state1, state2)

        assertTrue(diff.hasChanges)
        assertTrue(diff.titleChanged)
        assertFalse(diff.dataChanged)
        assertFalse(diff.componentsChanged)
    }

    @Test
    fun `diff with changed component properties`() {
        val state1 = UIStateParcel().apply {
            screenId = "screen1"
            title = "Title"
            data = Bundle.EMPTY
            components = arrayOf(
                UIComponentParcel().apply {
                    id = "btn1"
                    type = "BUTTON"
                    properties = Bundle().apply { putString("text", "Click Me") }
                    children = emptyArray()
                }
            )
            timestamp = 0
        }

        val state2 = UIStateParcel().apply {
            screenId = "screen1"
            title = "Title"
            data = Bundle.EMPTY
            components = arrayOf(
                UIComponentParcel().apply {
                    id = "btn1"
                    type = "BUTTON"
                    properties = Bundle().apply { putString("text", "Updated Text") }
                    children = emptyArray()
                }
            )
            timestamp = 0
        }

        val diff = UIStateDiffer.diff(state1, state2)

        assertTrue(diff.hasChanges)
        assertFalse(diff.titleChanged)
        assertFalse(diff.dataChanged)
        assertTrue(diff.componentsChanged)
        assertEquals(1, diff.changedComponentIds.size)
        assertTrue(diff.changedComponentIds.contains("btn1"))
        assertEquals(0, diff.addedComponentIds.size)
        assertEquals(0, diff.removedComponentIds.size)
    }

    @Test
    fun `diff with added components`() {
        val state1 = UIStateParcel().apply {
            screenId = "screen1"
            title = "Title"
            data = Bundle.EMPTY
            components = arrayOf(
                createComponent("btn1", "BUTTON")
            )
            timestamp = 0
        }

        val state2 = UIStateParcel().apply {
            screenId = "screen1"
            title = "Title"
            data = Bundle.EMPTY
            components = arrayOf(
                createComponent("btn1", "BUTTON"),
                createComponent("btn2", "BUTTON")
            )
            timestamp = 0
        }

        val diff = UIStateDiffer.diff(state1, state2)

        assertTrue(diff.hasChanges)
        assertTrue(diff.componentsChanged)
        assertEquals(1, diff.addedComponentIds.size)
        assertTrue(diff.addedComponentIds.contains("btn2"))
        assertEquals(0, diff.removedComponentIds.size)
        assertEquals(0, diff.changedComponentIds.size)
    }

    @Test
    fun `diff with removed components`() {
        val state1 = UIStateParcel().apply {
            screenId = "screen1"
            title = "Title"
            data = Bundle.EMPTY
            components = arrayOf(
                createComponent("btn1", "BUTTON"),
                createComponent("btn2", "BUTTON")
            )
            timestamp = 0
        }

        val state2 = UIStateParcel().apply {
            screenId = "screen1"
            title = "Title"
            data = Bundle.EMPTY
            components = arrayOf(
                createComponent("btn1", "BUTTON")
            )
            timestamp = 0
        }

        val diff = UIStateDiffer.diff(state1, state2)

        assertTrue(diff.hasChanges)
        assertTrue(diff.componentsChanged)
        assertEquals(0, diff.addedComponentIds.size)
        assertEquals(1, diff.removedComponentIds.size)
        assertTrue(diff.removedComponentIds.contains("btn2"))
        assertEquals(0, diff.changedComponentIds.size)
    }

    @Test
    fun `diff with nested component changes`() {
        val state1 = UIStateParcel().apply {
            screenId = "screen1"
            title = "Title"
            data = Bundle.EMPTY
            components = arrayOf(
                UIComponentParcel().apply {
                    id = "column1"
                    type = "COLUMN"
                    properties = Bundle.EMPTY
                    children = arrayOf(
                        createComponent("child1", "BUTTON")
                    )
                }
            )
            timestamp = 0
        }

        val state2 = UIStateParcel().apply {
            screenId = "screen1"
            title = "Title"
            data = Bundle.EMPTY
            components = arrayOf(
                UIComponentParcel().apply {
                    id = "column1"
                    type = "COLUMN"
                    properties = Bundle.EMPTY
                    children = arrayOf(
                        createComponent("child1", "BUTTON"),
                        createComponent("child2", "BUTTON")
                    )
                }
            )
            timestamp = 0
        }

        val diff = UIStateDiffer.diff(state1, state2)

        assertTrue(diff.hasChanges)
        assertTrue(diff.componentsChanged)
        assertEquals(1, diff.changedComponentIds.size)
        assertTrue(diff.changedComponentIds.contains("column1"))
    }

    @Test
    fun `shouldUpdate returns false for identical states`() {
        val state1 = createSimpleState("screen1", "Title", 1)
        val state2 = createSimpleState("screen1", "Title", 1)

        val diff = UIStateDiffer.diff(state1, state2)

        assertFalse(UIStateDiffer.shouldUpdate(diff))
    }

    @Test
    fun `shouldUpdate returns true for changed states`() {
        val state1 = createSimpleState("screen1", "Title 1", 1)
        val state2 = createSimpleState("screen1", "Title 2", 1)

        val diff = UIStateDiffer.diff(state1, state2)

        assertTrue(UIStateDiffer.shouldUpdate(diff))
    }

    @Test
    fun `calculateStateHash is consistent for same state`() {
        val state = createSimpleState("screen1", "Title", 3)

        val hash1 = UIStateDiffer.calculateStateHash(state)
        val hash2 = UIStateDiffer.calculateStateHash(state)

        assertEquals(hash1, hash2)
    }

    @Test
    fun `calculateStateHash differs for different states`() {
        val state1 = createSimpleState("screen1", "Title", 1)
        val state2 = createSimpleState("screen1", "Title 2", 1)

        val hash1 = UIStateDiffer.calculateStateHash(state1)
        val hash2 = UIStateDiffer.calculateStateHash(state2)

        assertNotEquals(hash1, hash2)
    }

    @Test
    fun `diff with multiple changes estimates payload reduction`() {
        val state1 = UIStateParcel().apply {
            screenId = "screen1"
            title = "Title"
            data = Bundle.EMPTY
            components = Array(10) { index ->
                createComponent("component_$index", "TEXT_VIEW")
            }
            timestamp = 0
        }

        val state2 = UIStateParcel().apply {
            screenId = "screen1"
            title = "Title"
            data = Bundle.EMPTY
            components = Array(10) { index ->
                if (index == 5) {
                    // Change one component
                    UIComponentParcel().apply {
                        id = "component_$index"
                        type = "TEXT_VIEW"
                        properties = Bundle().apply { putString("text", "Changed") }
                        children = emptyArray()
                    }
                } else {
                    createComponent("component_$index", "TEXT_VIEW")
                }
            }
            timestamp = 0
        }

        val diff = UIStateDiffer.diff(state1, state2)

        assertTrue(diff.hasChanges)
        assertEquals(1, diff.changedComponentIds.size)
        // 9 out of 10 components unchanged = 90% reduction
        assertTrue(diff.estimatedPayloadReduction > 0.8f)
    }

    // Helper: Create simple state
    private fun createSimpleState(screenId: String, title: String, componentCount: Int): UIStateParcel {
        return UIStateParcel().apply {
            this.screenId = screenId
            this.title = title
            this.data = Bundle.EMPTY
            this.components = Array(componentCount) { index ->
                createComponent("component_$index", "BUTTON")
            }
            this.timestamp = 0
        }
    }

    // Helper: Create component
    private fun createComponent(id: String, type: String): UIComponentParcel {
        return UIComponentParcel().apply {
            this.id = id
            this.type = type
            this.properties = Bundle().apply { putString("text", "Default") }
            this.children = emptyArray()
        }
    }
}
