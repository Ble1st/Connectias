package com.ble1st.connectias.plugin.declarative.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeclarativePluginValidatorTest {

    @Test
    fun validateFlow_rejectsUnknownNodeType() {
        val flow = DeclarativeFlowDefinition(
            flowId = "main",
            triggers = listOf(
                DeclarativeTrigger(type = "OnClick", targetId = "btn_run", startNodeId = "n1")
            ),
            nodes = listOf(
                DeclarativeNode(id = "n1", type = "DoesNotExist", params = emptyMap(), next = null)
            )
        )

        val r = DeclarativePluginValidator.validateFlow(flow)
        assertFalse(r.ok)
        assertTrue(r.errors.joinToString("\n").contains("Unknown node type"))
    }

    @Test
    fun validateFlow_rejectsInvalidParamType() {
        val flow = DeclarativeFlowDefinition(
            flowId = "main",
            triggers = listOf(
                DeclarativeTrigger(type = "OnClick", targetId = "btn_run", startNodeId = "n1")
            ),
            nodes = listOf(
                DeclarativeNode(
                    id = "n1",
                    type = "Ping",
                    params = mapOf(
                        "host" to "example.com",
                        "port" to "not_a_number",
                    ),
                    next = null
                )
            )
        )

        val r = DeclarativePluginValidator.validateFlow(flow)
        assertFalse(r.ok)
        assertTrue(r.errors.joinToString("\n").contains("must be integer"))
    }
}

