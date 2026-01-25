package com.ble1st.connectias.core.plugin.declarative

import com.ble1st.connectias.core.plugin.SandboxPluginContext
import com.ble1st.connectias.plugin.declarative.model.DeclarativeFlowDefinition
import com.ble1st.connectias.plugin.declarative.model.DeclarativeNode
import com.ble1st.connectias.plugin.declarative.model.DeclarativeTrigger
import com.ble1st.connectias.plugin.ui.IPluginUIController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify

class DeclarativeFlowEngineTest {

    @Test
    fun items_setField_then_setState_from_item() {
        val ui = mock(IPluginUIController::class.java)
        val sandbox = mock(SandboxPluginContext::class.java)
        val state = LinkedHashMap<String, Any?>()

        val engine = DeclarativeFlowEngine(
            pluginId = "demo.automation",
            uiController = ui,
            state = state,
            assets = emptyMap(),
            persistence = DeclarativeStatePersistence("demo.automation", sandbox),
            sandboxContext = sandbox
        )

        val flow = DeclarativeFlowDefinition(
            flowId = "main",
            triggers = listOf(DeclarativeTrigger(type = "OnClick", targetId = "btn_run", startNodeId = "n1")),
            nodes = listOf(
                DeclarativeNode(id = "n1", type = "SetField", params = mapOf("field" to "foo", "value" to "123"), next = "n2"),
                DeclarativeNode(id = "n2", type = "SetState", params = mapOf("key" to "result", "value" to "item.foo"), next = null),
            )
        )

        val result = engine.runFlowWithResult(flow, flow.triggers.first(), DeclarativeFlowEngine.TriggerContext())
        assertTrue(result.ok)
        assertEquals("123", state["result"])
        assertEquals(1, result.items.size)
        assertEquals("123", result.items.first()["foo"])
    }

    @Test
    fun items_filter_keepsOnlyMatching() {
        val ui = mock(IPluginUIController::class.java)
        val sandbox = mock(SandboxPluginContext::class.java)
        val state = LinkedHashMap<String, Any?>()

        val engine = DeclarativeFlowEngine(
            pluginId = "demo.automation",
            uiController = ui,
            state = state,
            assets = emptyMap(),
            persistence = DeclarativeStatePersistence("demo.automation", sandbox),
            sandboxContext = sandbox
        )

        val flow = DeclarativeFlowDefinition(
            flowId = "main",
            triggers = listOf(DeclarativeTrigger(type = "OnClick", targetId = "btn_run", startNodeId = "n1")),
            nodes = listOf(
                DeclarativeNode(
                    id = "n1",
                    type = "Filter",
                    params = mapOf("left" to "item.foo", "op" to ">", "right" to 1),
                    next = null
                )
            )
        )

        val ctx = DeclarativeFlowEngine.TriggerContext(
            items = mutableListOf(
                LinkedHashMap<String, Any?>().apply { put("foo", 1) },
                LinkedHashMap<String, Any?>().apply { put("foo", 2) },
            )
        )

        val result = engine.runFlowWithResult(flow, flow.triggers.first(), ctx)
        assertTrue(result.ok)
        assertEquals(1, result.items.size)
        assertEquals(2, result.items.first()["foo"])
    }

    @Test
    fun curl_blocksNonHttpsUrl() {
        val ui = mock(IPluginUIController::class.java)
        val sandbox = mock(SandboxPluginContext::class.java)
        val state = LinkedHashMap<String, Any?>()

        val engine = DeclarativeFlowEngine(
            pluginId = "demo.automation",
            uiController = ui,
            state = state,
            assets = emptyMap(),
            persistence = DeclarativeStatePersistence("demo.automation", sandbox),
            sandboxContext = sandbox
        )

        val flow = DeclarativeFlowDefinition(
            flowId = "main",
            triggers = listOf(DeclarativeTrigger(type = "OnClick", targetId = "btn_run", startNodeId = "n1")),
            nodes = listOf(
                DeclarativeNode(
                    id = "n1",
                    type = "Curl",
                    params = mapOf(
                        "url" to "http://example.com",
                        "responseKey" to "body",
                        "statusKey" to "status"
                    ),
                    next = null
                )
            )
        )

        val result = engine.runFlowWithResult(flow, flow.triggers.first(), DeclarativeFlowEngine.TriggerContext())
        assertTrue(result.ok)
        assertEquals("", state["body"])
        assertEquals(-1, state["status"])
        verify(sandbox, never()).httpGetWithInfo(org.mockito.Mockito.anyString(), org.mockito.Mockito.anyInt())
    }

    @Test
    fun stepLimit_exceeded_abortsRun() {
        val ui = mock(IPluginUIController::class.java)
        val sandbox = mock(SandboxPluginContext::class.java)
        val state = LinkedHashMap<String, Any?>()

        val engine = DeclarativeFlowEngine(
            pluginId = "demo.automation",
            uiController = ui,
            state = state,
            assets = emptyMap(),
            persistence = DeclarativeStatePersistence("demo.automation", sandbox),
            sandboxContext = sandbox
        )

        // Build a long chain that exceeds maxStepsPerRun (128)
        val nodes = (1..140).map { i ->
            val id = "n$i"
            val next = if (i < 140) "n${i + 1}" else null
            DeclarativeNode(
                id = id,
                type = "SetState",
                params = mapOf("key" to "k$i", "value" to i),
                next = next
            )
        }

        val flow = DeclarativeFlowDefinition(
            flowId = "main",
            triggers = listOf(DeclarativeTrigger(type = "OnClick", targetId = "btn_run", startNodeId = "n1")),
            nodes = nodes
        )

        val result = engine.runFlowWithResult(flow, flow.triggers.first(), DeclarativeFlowEngine.TriggerContext())
        assertFalse(result.ok)
        assertEquals("step_limit_exceeded", result.error)
    }
}

