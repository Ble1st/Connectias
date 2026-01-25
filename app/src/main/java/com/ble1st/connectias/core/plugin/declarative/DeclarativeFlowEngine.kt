package com.ble1st.connectias.core.plugin.declarative

import android.os.Bundle
import com.ble1st.connectias.core.plugin.SandboxPluginContext
import com.ble1st.connectias.plugin.declarative.model.DeclarativeFlowDefinition
import com.ble1st.connectias.plugin.declarative.model.DeclarativeNode
import com.ble1st.connectias.plugin.declarative.model.DeclarativeTrigger
import com.ble1st.connectias.plugin.ui.IPluginUIController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import timber.log.Timber

/**
 * Deterministic workflow engine for declarative plugins.
 *
 * Security notes:
 * - No arbitrary code execution
 * - Hard step limit per run
 * - Minimal supported node set (MVP)
 */
class DeclarativeFlowEngine(
    private val pluginId: String,
    private val uiController: IPluginUIController,
    private val state: MutableMap<String, Any?>,
    private val assets: Map<String, ByteArray>,
    private val persistence: DeclarativeStatePersistence,
    private val sandboxContext: SandboxPluginContext,
) {
    private val maxStepsPerRun = 128
    private val rateLimiter = FlowRateLimiter()
    private val runHistory = ArrayDeque<RunRecord>(50)

    fun runFlow(flow: DeclarativeFlowDefinition, trigger: DeclarativeTrigger, triggerContext: TriggerContext) {
        val triggerKey = "${flow.flowId}:${trigger.type}:${trigger.targetId ?: trigger.messageType ?: ""}"
        if (!rateLimiter.tryAcquire(triggerKey, trigger.type)) {
            recordRun(
                RunRecord(
                    flowId = flow.flowId,
                    triggerType = trigger.type,
                    ok = false,
                    steps = 0,
                    durationMs = 0,
                    error = "rate_limited"
                )
            )
            Timber.w("[DECL_AUDIT][$pluginId] rate_limited flowId=${flow.flowId} trigger=${trigger.type}")
            return
        }

        val startNs = System.nanoTime()
        val nodeMap = flow.nodes.associateBy { it.id }
        var currentId: String? = trigger.startNodeId
        var steps = 0

        while (!currentId.isNullOrBlank()) {
            if (steps++ > maxStepsPerRun) {
                Timber.w("[SANDBOX][DECL:$pluginId] Flow '${flow.flowId}' aborted: step limit exceeded")
                recordRun(
                    RunRecord(
                        flowId = flow.flowId,
                        triggerType = trigger.type,
                        ok = false,
                        steps = steps,
                        durationMs = nanosToMs(System.nanoTime() - startNs),
                        error = "step_limit_exceeded"
                    )
                )
                return
            }

            val node = nodeMap[currentId]
            if (node == null) {
                Timber.w("[SANDBOX][DECL:$pluginId] Missing node: $currentId")
                return
            }

            try {
                currentId = executeNode(node, triggerContext)
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX][DECL:$pluginId] Node failed: ${node.id} (${node.type})")
                uiController.showDialog(pluginId, "Workflow error", "Node failed: ${node.type}", 2)
                recordRun(
                    RunRecord(
                        flowId = flow.flowId,
                        triggerType = trigger.type,
                        ok = false,
                        steps = steps,
                        durationMs = nanosToMs(System.nanoTime() - startNs),
                        error = e.message ?: "node_failed"
                    )
                )
                return
            }
        }

        recordRun(
            RunRecord(
                flowId = flow.flowId,
                triggerType = trigger.type,
                ok = true,
                steps = steps,
                durationMs = nanosToMs(System.nanoTime() - startNs),
                error = null
            )
        )
    }

    private fun executeNode(node: DeclarativeNode, ctx: TriggerContext): String? {
        return when (node.type) {
            "IfElse" -> {
                val ok = evalCondition(
                    left = node.params["left"],
                    op = node.params["op"]?.toString(),
                    right = node.params["right"],
                    ctx = ctx
                )
                if (ok) node.nextTrue else node.nextFalse
            }
            "SetState" -> {
                val key = node.params["key"]?.toString() ?: return node.next
                val value = resolveValue(node.params["value"], ctx)
                state[key] = value
                node.next
            }
            "Increment" -> {
                val key = node.params["key"]?.toString() ?: return node.next
                val delta = (node.params["delta"] as? Number)?.toLong() ?: node.params["delta"]?.toString()?.toLongOrNull() ?: 1L
                val current = (state[key] as? Number)?.toLong() ?: state[key]?.toString()?.toLongOrNull() ?: 0L
                state[key] = current + delta
                node.next
            }
            "ShowToast" -> {
                val message = node.params["message"]?.toString() ?: ""
                uiController.showToast(pluginId, DeclarativeTemplate.render(message, state), 0)
                node.next
            }
            "ShowDialog" -> {
                val title = node.params["title"]?.toString() ?: "Info"
                val message = node.params["message"]?.toString() ?: ""
                uiController.showDialog(pluginId, title, DeclarativeTemplate.render(message, state), 0)
                node.next
            }
            "Navigate" -> {
                val screenId = node.params["screenId"]?.toString() ?: return node.next
                uiController.navigateToScreen(pluginId, screenId, Bundle())
                state["_currentScreenId"] = screenId
                node.next
            }
            "EmitMessage" -> {
                // MVP: message send is optional. Keep as a no-op with logging for now.
                Timber.i("[SANDBOX][DECL:$pluginId] EmitMessage requested (MVP no-op)")
                node.next
            }
            "PersistState" -> {
                persistence.persist(state)
                node.next
            }
            "Curl" -> {
                val urlRaw = node.params["url"]?.toString() ?: return node.next
                val url = DeclarativeTemplate.render(urlRaw, state)
                val outKey = node.params["responseKey"]?.toString() ?: "lastCurlBody"
                val statusKey = node.params["statusKey"]?.toString()
                val contentTypeKey = node.params["contentTypeKey"]?.toString()

                val r = runBlocking(Dispatchers.IO) {
                    sandboxContext.httpGetWithInfo(url)
                }

                r.onSuccess { info ->
                    state[outKey] = info.body
                    if (!statusKey.isNullOrBlank()) state[statusKey] = info.statusCode
                    if (!contentTypeKey.isNullOrBlank()) state[contentTypeKey] = info.contentType
                }.onFailure { e ->
                    Timber.w(e, "[SANDBOX][DECL:$pluginId] Curl failed: $url")
                    state[outKey] = ""
                    if (!statusKey.isNullOrBlank()) state[statusKey] = -1
                }

                node.next
            }
            "Ping" -> {
                val hostRaw = node.params["host"]?.toString() ?: return node.next
                val host = DeclarativeTemplate.render(hostRaw, state).trim()
                val port = (node.params["port"] as? Number)?.toInt()
                    ?: node.params["port"]?.toString()?.toIntOrNull()
                    ?: 443
                val timeoutMs = (node.params["timeoutMs"] as? Number)?.toInt()
                    ?: node.params["timeoutMs"]?.toString()?.toIntOrNull()
                    ?: 1500

                val okKey = node.params["okKey"]?.toString() ?: "lastPingOk"
                val latencyKey = node.params["latencyKey"]?.toString() ?: "lastPingLatencyMs"

                val r = runBlocking(Dispatchers.IO) {
                    sandboxContext.tcpPing(host, port, timeoutMs)
                }

                if (r.isSuccess) {
                    state[okKey] = true
                    state[latencyKey] = r.getOrNull()
                } else {
                    Timber.w(r.exceptionOrNull(), "[SANDBOX][DECL:$pluginId] Ping failed: $host:$port")
                    state[okKey] = false
                    state[latencyKey] = -1L
                }

                node.next
            }
            else -> {
                Timber.w("[SANDBOX][DECL:$pluginId] Unknown node type: ${node.type}")
                node.next
            }
        }
    }

    private fun evalCondition(left: Any?, op: String?, right: Any?, ctx: TriggerContext): Boolean {
        val l = resolveValue(left, ctx)
        val r = resolveValue(right, ctx)
        val opSafe = op ?: "=="

        // Prefer numeric comparisons if both can be parsed as numbers
        val ln = l?.toString()?.toDoubleOrNull()
        val rn = r?.toString()?.toDoubleOrNull()
        if (ln != null && rn != null) {
            return when (opSafe) {
                "==" -> ln == rn
                "!=" -> ln != rn
                ">" -> ln > rn
                ">=" -> ln >= rn
                "<" -> ln < rn
                "<=" -> ln <= rn
                else -> false
            }
        }

        val ls = l?.toString()
        val rs = r?.toString()
        return when (opSafe) {
            "==" -> ls == rs
            "!=" -> ls != rs
            "contains" -> (ls ?: "").contains(rs ?: "")
            else -> false
        }
    }

    private fun resolveValue(raw: Any?, ctx: TriggerContext): Any? {
        if (raw == null) return null
        if (raw is Number || raw is Boolean) return raw

        val s = raw.toString()
        if (s.startsWith("state.")) {
            val key = s.removePrefix("state.")
            return state[key]
        }
        if (s == "action.targetId") return ctx.actionTargetId
        if (s.startsWith("action.data.")) {
            val k = s.removePrefix("action.data.")
            return ctx.actionData?.get(k)
        }
        if (s.startsWith("asset:")) {
            val path = s.removePrefix("asset:")
            return assets[path]
        }
        return raw
    }

    data class TriggerContext(
        val actionTargetId: String? = null,
        val actionData: Map<String, Any?>? = null,
    )

    data class RunRecord(
        val flowId: String,
        val triggerType: String,
        val ok: Boolean,
        val steps: Int,
        val durationMs: Long,
        val error: String?
    )

    private fun recordRun(record: RunRecord) {
        if (runHistory.size >= 50) runHistory.removeFirst()
        runHistory.addLast(record)

        // Emit to logs for DB persistence + logcat via plugin log bridge
        Timber.i(
            "[DECL_FLOW][$pluginId] flowId=%s trigger=%s ok=%s steps=%d durationMs=%d error=%s",
            record.flowId,
            record.triggerType,
            record.ok,
            record.steps,
            record.durationMs,
            record.error ?: "null"
        )
    }

    private fun nanosToMs(ns: Long): Long = ns / 1_000_000L

    /**
     * Simple fixed-window rate limiter.
     * - UI triggers: 30 executions / 10s (per flow+trigger target)
     * - Timer triggers: 6 executions / 60s
     * - Message triggers: 20 executions / 10s
     */
    private class FlowRateLimiter {
        private data class Window(var startMs: Long, var count: Int)
        private val windows = HashMap<String, Window>()

        fun tryAcquire(key: String, triggerType: String): Boolean {
            val now = System.currentTimeMillis()
            val (limit, windowMs) = when (triggerType) {
                "OnTimer" -> 6 to 60_000L
                "OnMessage" -> 20 to 10_000L
                else -> 30 to 10_000L
            }

            val w = windows.getOrPut(key) { Window(now, 0) }
            if (now - w.startMs >= windowMs) {
                w.startMs = now
                w.count = 0
            }
            if (w.count >= limit) return false
            w.count++
            return true
        }
    }
}

