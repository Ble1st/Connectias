// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.core.plugin.ui

import android.os.Bundle
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ble1st.connectias.plugin.ui.UIComponentParcel
import com.ble1st.connectias.plugin.ui.UIStateParcel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Performance benchmarks for Three-Process UI Architecture.
 *
 * Measures:
 * - UIStateParcel creation time
 * - Parcelable serialization/deserialization overhead
 * - State diffing performance
 * - Component tree traversal
 *
 * Run with: ./gradlew :app:connectedAndroidTest -P android.testInstrumentationRunnerArguments.class=...UIPerformanceBenchmark
 */
@RunWith(AndroidJUnit4::class)
class UIPerformanceBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    @Test
    fun benchmarkSimpleUIStateCreation() {
        benchmarkRule.measureRepeated {
            UIStateParcel().apply {
                screenId = "test_screen"
                title = "Test Title"
                data = Bundle.EMPTY
                components = arrayOf(
                    UIComponentParcel().apply {
                        id = "button1"
                        type = "BUTTON"
                        properties = Bundle().apply { putString("text", "Click Me") }
                        children = emptyArray()
                    }
                )
                timestamp = System.currentTimeMillis()
            }
        }
    }

    @Test
    fun benchmarkComplexUIStateCreation() {
        benchmarkRule.measureRepeated {
            createComplexUIState()
        }
    }

    @Test
    fun benchmarkStateDiffingNoChanges() {
        val state1 = createComplexUIState()
        val state2 = createComplexUIState()

        benchmarkRule.measureRepeated {
            UIStateDiffer.diff(state1, state2)
        }
    }

    @Test
    fun benchmarkStateDiffingWithChanges() {
        val state1 = createComplexUIState()
        val state2 = createComplexUIState().apply {
            title = "Updated Title"
            components = components.copyOf().apply {
                this[0] = this[0].apply {
                    properties = Bundle().apply { putString("text", "Updated Button") }
                }
            }
        }

        benchmarkRule.measureRepeated {
            UIStateDiffer.diff(state1, state2)
        }
    }

    @Test
    fun benchmarkComponentTreeTraversal() {
        val nestedComponents = createNestedComponentTree(depth = 5, childrenPerLevel = 3)

        benchmarkRule.measureRepeated {
            traverseComponentTree(nestedComponents)
        }
    }

    @Test
    fun benchmarkLargeComponentList() {
        benchmarkRule.measureRepeated {
            val components = Array(100) { index ->
                UIComponentParcel().apply {
                    id = "component_$index"
                    type = "TEXT_VIEW"
                    properties = Bundle().apply { putString("text", "Item $index") }
                    children = emptyArray()
                }
            }

            UIStateParcel().apply {
                screenId = "large_list"
                title = "Large List"
                data = Bundle.EMPTY
                this.components = components
                timestamp = System.currentTimeMillis()
            }
        }
    }

    @Test
    fun benchmarkBundleCreation() {
        benchmarkRule.measureRepeated {
            Bundle().apply {
                putString("key1", "value1")
                putInt("key2", 42)
                putBoolean("key3", true)
                putFloat("key4", 3.14f)
                putLong("key5", System.currentTimeMillis())
            }
        }
    }

    @Test
    fun benchmarkStateHashCalculation() {
        val state = createComplexUIState()

        benchmarkRule.measureRepeated {
            UIStateDiffer.calculateStateHash(state)
        }
    }

    // Helper: Create complex UI state
    private fun createComplexUIState(): UIStateParcel {
        return UIStateParcel().apply {
            screenId = "complex_screen"
            title = "Complex UI"
            data = Bundle().apply {
                putString("user_id", "12345")
                putBoolean("is_premium", true)
                putInt("credits", 100)
            }
            components = arrayOf(
                // Header
                UIComponentParcel().apply {
                    id = "header"
                    type = "TEXT_VIEW"
                    properties = Bundle().apply { putString("text", "Welcome") }
                    children = emptyArray()
                },
                // Form column
                UIComponentParcel().apply {
                    id = "form"
                    type = "COLUMN"
                    properties = Bundle.EMPTY
                    children = arrayOf(
                        UIComponentParcel().apply {
                            id = "input1"
                            type = "TEXT_FIELD"
                            properties = Bundle().apply { putString("label", "Username") }
                            children = emptyArray()
                        },
                        UIComponentParcel().apply {
                            id = "input2"
                            type = "TEXT_FIELD"
                            properties = Bundle().apply { putString("label", "Email") }
                            children = emptyArray()
                        },
                        UIComponentParcel().apply {
                            id = "checkbox"
                            type = "CHECKBOX"
                            properties = Bundle().apply {
                                putString("label", "Accept")
                                putBoolean("checked", false)
                            }
                            children = emptyArray()
                        }
                    )
                },
                // Actions row
                UIComponentParcel().apply {
                    id = "actions"
                    type = "ROW"
                    properties = Bundle.EMPTY
                    children = arrayOf(
                        UIComponentParcel().apply {
                            id = "btn_cancel"
                            type = "BUTTON"
                            properties = Bundle().apply { putString("text", "Cancel") }
                            children = emptyArray()
                        },
                        UIComponentParcel().apply {
                            id = "btn_submit"
                            type = "BUTTON"
                            properties = Bundle().apply { putString("text", "Submit") }
                            children = emptyArray()
                        }
                    )
                }
            )
            timestamp = System.currentTimeMillis()
        }
    }

    // Helper: Create nested component tree
    private fun createNestedComponentTree(depth: Int, childrenPerLevel: Int): UIComponentParcel {
        if (depth == 0) {
            return UIComponentParcel().apply {
                id = "leaf"
                type = "TEXT_VIEW"
                properties = Bundle().apply { putString("text", "Leaf") }
                children = emptyArray()
            }
        }

        return UIComponentParcel().apply {
            id = "node_$depth"
            type = "COLUMN"
            properties = Bundle.EMPTY
            children = Array(childrenPerLevel) {
                createNestedComponentTree(depth - 1, childrenPerLevel)
            }
        }
    }

    // Helper: Traverse component tree (depth-first)
    private fun traverseComponentTree(component: UIComponentParcel): Int {
        var count = 1
        for (child in component.children) {
            count += traverseComponentTree(child)
        }
        return count
    }
}
