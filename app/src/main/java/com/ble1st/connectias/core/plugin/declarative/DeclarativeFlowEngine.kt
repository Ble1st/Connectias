package com.ble1st.connectias.core.plugin.declarative

import android.os.Bundle
import androidx.core.net.toUri
import com.ble1st.connectias.core.plugin.SandboxPluginContext
import com.ble1st.connectias.plugin.declarative.model.DeclarativeFlowDefinition
import com.ble1st.connectias.plugin.declarative.model.DeclarativeNode
import com.ble1st.connectias.plugin.declarative.model.DeclarativeTrigger
import com.ble1st.connectias.plugin.ui.IPluginUIController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
    private val maxLogValueLen = 256

    fun runFlow(flow: DeclarativeFlowDefinition, trigger: DeclarativeTrigger, triggerContext: TriggerContext) {
        runFlowWithResult(flow, trigger, triggerContext)
    }

    internal fun runFlowWithResult(
        flow: DeclarativeFlowDefinition,
        trigger: DeclarativeTrigger,
        triggerContext: TriggerContext
    ): RunResult {
        Timber.i(
            "[DECL_TRACE][$pluginId] start flowId=%s trigger=%s startNodeId=%s actionTargetId=%s actionDataKeys=%s itemsCount=%d",
            flow.flowId,
            trigger.type,
            trigger.startNodeId,
            triggerContext.actionTargetId ?: "null",
            triggerContext.actionData?.keys?.sorted()?.joinToString(",") ?: "null",
            triggerContext.items.size
        )

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
            return RunResult(
                ok = false,
                flowId = flow.flowId,
                triggerType = trigger.type,
                steps = 0,
                durationMs = 0,
                error = "rate_limited",
                items = triggerContext.itemsSnapshot(),
            )
        }

        val startNs = System.nanoTime()
        val nodeMap = flow.nodes.associateBy { it.id }
        var currentId: String? = trigger.startNodeId
        var steps = 0

        while (!currentId.isNullOrBlank()) {
            if (steps++ > maxStepsPerRun) {
                Timber.w("[SANDBOX][DECL:$pluginId] Flow '${flow.flowId}' aborted: step limit exceeded")
                val durationMs = nanosToMs(System.nanoTime() - startNs)
                recordRun(
                    RunRecord(
                        flowId = flow.flowId,
                        triggerType = trigger.type,
                        ok = false,
                        steps = steps,
                        durationMs = durationMs,
                        error = "step_limit_exceeded"
                    )
                )
                return RunResult(
                    ok = false,
                    flowId = flow.flowId,
                    triggerType = trigger.type,
                    steps = steps,
                    durationMs = durationMs,
                    error = "step_limit_exceeded",
                    items = triggerContext.itemsSnapshot(),
                )
            }

            val node = nodeMap[currentId]
            if (node == null) {
                Timber.w("[SANDBOX][DECL:$pluginId] Missing node: $currentId")
                return RunResult(
                    ok = false,
                    flowId = flow.flowId,
                    triggerType = trigger.type,
                    steps = steps,
                    durationMs = nanosToMs(System.nanoTime() - startNs),
                    error = "missing_node:$currentId",
                    items = triggerContext.itemsSnapshot(),
                )
            }

            val beforeState = snapshotStateForLog(state)
            val beforeItems = snapshotItemsForLog(triggerContext.items)

            try {
                Timber.i(
                    "[DECL_TRACE][$pluginId] step=%d nodeId=%s type=%s params=%s",
                    steps,
                    node.id,
                    node.type,
                    summarizeParamsForLog(node.type, node.params)
                )
                currentId = executeNode(node, triggerContext)

                val afterState = snapshotStateForLog(state)
                val afterItems = snapshotItemsForLog(triggerContext.items)
                val stateDiff = diffForLog(beforeState, afterState)
                val itemsDiff = diffForLog(beforeItems, afterItems)
                Timber.i(
                    "[DECL_TRACE][$pluginId] step=%d done nodeId=%s next=%s stateDiff=%s itemsDiff=%s",
                    steps,
                    node.id,
                    currentId ?: "null",
                    stateDiff,
                    itemsDiff
                )
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX][DECL:$pluginId] Node failed: ${node.id} (${node.type})")
                uiController.showDialog(pluginId, "Workflow error", "Node failed: ${node.type}", 2)
                val durationMs = nanosToMs(System.nanoTime() - startNs)
                recordRun(
                    RunRecord(
                        flowId = flow.flowId,
                        triggerType = trigger.type,
                        ok = false,
                        steps = steps,
                        durationMs = durationMs,
                        error = e.message ?: "node_failed"
                    )
                )
                return RunResult(
                    ok = false,
                    flowId = flow.flowId,
                    triggerType = trigger.type,
                    steps = steps,
                    durationMs = durationMs,
                    error = e.message ?: "node_failed",
                    items = triggerContext.itemsSnapshot(),
                )
            }
        }

        val durationMs = nanosToMs(System.nanoTime() - startNs)
        recordRun(
            RunRecord(
                flowId = flow.flowId,
                triggerType = trigger.type,
                ok = true,
                steps = steps,
                durationMs = durationMs,
                error = null
            )
        )
        Timber.i(
            "[DECL_TRACE][$pluginId] done flowId=%s trigger=%s ok=true steps=%d durationMs=%d itemsCount=%d",
            flow.flowId,
            trigger.type,
            steps,
            durationMs,
            triggerContext.items.size
        )
        return RunResult(
            ok = true,
            flowId = flow.flowId,
            triggerType = trigger.type,
            steps = steps,
            durationMs = durationMs,
            error = null,
            items = triggerContext.itemsSnapshot(),
        )
    }

    private fun executeNode(node: DeclarativeNode, ctx: TriggerContext): String? {
        // Helper to get param value with fallback to NodeSpec default
        fun getParam(key: String): Any? {
            if (node.params.containsKey(key)) return node.params[key]
            val spec = NodeRegistry.get(node.type) ?: return null
            return spec.params.firstOrNull { it.key == key }?.defaultValue
        }

        return when (node.type) {
            "IfElse" -> {
                val ok = evalCondition(
                    left = node.params["left"],
                    op = node.params["op"]?.toString(),
                    right = node.params["right"],
                    ctx = ctx,
                    currentItem = ctx.items.firstOrNull(),
                )
                if (ok) node.nextTrue else node.nextFalse
            }
            "SetField" -> {
                val field = (getParam("field") ?: node.params["field"])?.toString() ?: return node.next
                val raw = getParam("value") ?: node.params["value"]
                if (ctx.items.isEmpty()) {
                    ctx.items.add(LinkedHashMap())
                }
                ctx.items.forEach { item ->
                    val value = when (val resolved = resolveValue(raw, ctx, item)) {
                        is String -> DeclarativeTemplate.render(resolved, state)
                        else -> resolved
                    }
                    item[field] = value
                }
                node.next
            }
            "Filter" -> {
                val left = node.params["left"]
                val op = node.params["op"]?.toString()
                val right = node.params["right"]
                val kept = ctx.items.filter { item ->
                    evalCondition(left = left, op = op, right = right, ctx = ctx, currentItem = item)
                }
                ctx.items.clear()
                ctx.items.addAll(kept.map { LinkedHashMap(it) })
                node.next
            }
            "SetState" -> {
                val key = (getParam("key") ?: node.params["key"])?.toString() ?: return node.next
                val value = resolveValue(getParam("value") ?: node.params["value"], ctx, ctx.items.firstOrNull())
                state[key] = value
                node.next
            }
            "Increment" -> {
                val key = (getParam("key") ?: node.params["key"])?.toString() ?: return node.next
                val delta = ((getParam("delta") ?: node.params["delta"]) as? Number)?.toLong()
                    ?: (getParam("delta") ?: node.params["delta"])?.toString()?.toLongOrNull()
                    ?: 1L
                val current = (state[key] as? Number)?.toLong() ?: state[key]?.toString()?.toLongOrNull() ?: 0L
                state[key] = current + delta
                node.next
            }
            "ShowToast" -> {
                val message = (getParam("message") ?: node.params["message"])?.toString() ?: ""
                uiController.showToast(pluginId, DeclarativeTemplate.render(message, state), 0)
                node.next
            }
            "ShowImage" -> {
                val imageKey = (getParam("imageKey") ?: node.params["imageKey"])?.toString() ?: "uiImageBase64"
                val screenId = (getParam("screenId") ?: node.params["screenId"])?.toString() ?: "image"
                val source = resolveValue(getParam("source") ?: node.params["source"], ctx, ctx.items.firstOrNull())

                // We store a Base64 string in state and let the UI bind to it via {{uiImageBase64}}.
                // Note: large images will be offloaded by PluginUIControllerImpl to avoid Binder limits.
                val base64 = when (source) {
                    null -> ""
                    is String -> DeclarativeTemplate.render(source, state)
                    else -> source.toString()
                }
                state[imageKey] = base64

                Timber.i("[DECL_TRACE][$pluginId] ShowImage set %s (%d chars), navigate=%s", imageKey, base64.length, screenId)
                uiController.navigateToScreen(pluginId, screenId, Bundle())
                state["_currentScreenId"] = screenId
                node.next
            }
            "ShowDialog" -> {
                val title = (getParam("title") ?: node.params["title"])?.toString() ?: "Info"
                val message = (getParam("message") ?: node.params["message"])?.toString() ?: ""
                uiController.showDialog(pluginId, title, DeclarativeTemplate.render(message, state), 0)
                node.next
            }
            "Navigate" -> {
                val screenId = (getParam("screenId") ?: node.params["screenId"])?.toString() ?: return node.next
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
                val urlRaw = (getParam("url") ?: node.params["url"])?.toString() ?: return node.next
                val url = DeclarativeTemplate.render(urlRaw, state)
                val outKey = (getParam("responseKey") ?: node.params["responseKey"])?.toString() ?: "lastCurlBody"
                val statusKey = (getParam("statusKey") ?: node.params["statusKey"])?.toString()
                val contentTypeKey = (getParam("contentTypeKey") ?: node.params["contentTypeKey"])?.toString()
                val timeoutMs = ((getParam("timeoutMs") ?: node.params["timeoutMs"]) as? Number)?.toLong()
                    ?: (getParam("timeoutMs") ?: node.params["timeoutMs"])?.toString()?.toLongOrNull()
                    ?: 5_000L
                val maxBytes = ((getParam("maxBytes") ?: node.params["maxBytes"]) as? Number)?.toInt()
                    ?: (getParam("maxBytes") ?: node.params["maxBytes"])?.toString()?.toIntOrNull()
                    ?: 512_000

                val urlError = validateHttpUrl(url)
                if (urlError != null) {
                    Timber.w("[SANDBOX][DECL:$pluginId] Curl blocked: $urlError url=$url")
                    state[outKey] = ""
                    if (!statusKey.isNullOrBlank()) state[statusKey] = -1
                    if (!contentTypeKey.isNullOrBlank()) state[contentTypeKey] = null
                    return node.next
                }

                val r = runBlocking(Dispatchers.IO) {
                    withTimeout(timeoutMs.coerceIn(250L, 30_000L)) {
                        sandboxContext.httpGetWithInfo(url, maxBytes = maxBytes)
                    }
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
                val hostRaw = (getParam("host") ?: node.params["host"])?.toString() ?: return node.next
                val host = DeclarativeTemplate.render(hostRaw, state).trim()
                val port = ((getParam("port") ?: node.params["port"]) as? Number)?.toInt()
                    ?: (getParam("port") ?: node.params["port"])?.toString()?.toIntOrNull()
                    ?: 443
                val timeoutMs = ((getParam("timeoutMs") ?: node.params["timeoutMs"]) as? Number)?.toInt()
                    ?: (getParam("timeoutMs") ?: node.params["timeoutMs"])?.toString()?.toIntOrNull()
                    ?: 1500

                val okKey = (getParam("okKey") ?: node.params["okKey"])?.toString() ?: "lastPingOk"
                val latencyKey = (getParam("latencyKey") ?: node.params["latencyKey"])?.toString() ?: "lastPingLatencyMs"

                Timber.i("[DECL_TRACE][$pluginId] Ping start host=%s port=%d timeoutMs=%d", host, port, timeoutMs)
                val r = runBlocking(Dispatchers.IO) {
                    sandboxContext.tcpPing(host, port, timeoutMs)
                }

                if (r.isSuccess) {
                    state[okKey] = true
                    state[latencyKey] = r.getOrNull()
                    Timber.i("[DECL_TRACE][$pluginId] Ping ok latencyMs=%s", r.getOrNull()?.toString() ?: "null")
                } else {
                    Timber.w(r.exceptionOrNull(), "[SANDBOX][DECL:$pluginId] Ping failed: $host:$port")
                    state[okKey] = false
                    state[latencyKey] = -1L
                    Timber.i("[DECL_TRACE][$pluginId] Ping failed")
                }

                node.next
            }
            "CaptureImage" -> {
                val imageKey = (getParam("imageKey") ?: node.params["imageKey"])?.toString() ?: "lastCapturedImage"
                val statusKey = (getParam("statusKey") ?: node.params["statusKey"])?.toString() ?: "captureStatus"

                val r = runBlocking(Dispatchers.IO) {
                    sandboxContext.captureImage()
                }

                r.onSuccess { bytes ->
                    val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    state[imageKey] = base64
                    state[statusKey] = "success"
                    Timber.i("[SANDBOX][DECL:$pluginId] CaptureImage: ${bytes.size} bytes")
                }.onFailure { e ->
                    Timber.w(e, "[SANDBOX][DECL:$pluginId] CaptureImage failed")
                    state[imageKey] = ""
                    state[statusKey] = "error:${e.message}"
                }

                node.next
            }
            else -> {
                Timber.w("[SANDBOX][DECL:$pluginId] Unknown node type: ${node.type}")
                node.next
            }
        }
    }

    private fun evalCondition(left: Any?, op: String?, right: Any?, ctx: TriggerContext, currentItem: Map<String, Any?>?): Boolean {
        val l = resolveValue(left, ctx, currentItem)
        val r = resolveValue(right, ctx, currentItem)
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

    private fun resolveValue(raw: Any?, ctx: TriggerContext, currentItem: Map<String, Any?>?): Any? {
        if (raw == null) return null
        if (raw is Number || raw is Boolean) return raw

        val s = raw.toString()
        if (s.startsWith("state.")) {
            val key = s.removePrefix("state.")
            return state[key]
        }
        if (s.startsWith("item.")) {
            val key = s.removePrefix("item.")
            val item = currentItem ?: ctx.items.firstOrNull()
            return item?.get(key)
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
        val items: MutableList<MutableMap<String, Any?>> = mutableListOf(LinkedHashMap()),
    )

    internal fun TriggerContext.itemsSnapshot(): List<Map<String, Any?>> {
        return items.map { LinkedHashMap(it) }
    }

    internal data class RunResult(
        val ok: Boolean,
        val flowId: String,
        val triggerType: String,
        val steps: Int,
        val durationMs: Long,
        val error: String?,
        val items: List<Map<String, Any?>>,
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

    private fun summarizeParamsForLog(nodeType: String, params: Map<String, Any?>): String {
        if (params.isEmpty()) return "{}"
        val keys = params.keys.sorted()
        val safe = LinkedHashMap<String, String>(keys.size)
        keys.forEach { k ->
            safe[k] = safeValueForLog(nodeType = nodeType, key = k, value = params[k])
        }
        return safe.entries.joinToString(prefix = "{", postfix = "}") { (k, v) -> "$k=$v" }
    }

    private fun snapshotStateForLog(state: Map<String, Any?>): Map<String, String> {
        val keys = state.keys.sorted()
        val out = LinkedHashMap<String, String>(keys.size)
        keys.forEach { k ->
            out[k] = safeValueForLog(nodeType = "state", key = k, value = state[k])
        }
        return out
    }

    private fun snapshotItemsForLog(items: List<Map<String, Any?>>): Map<String, String> {
        // Flatten into a stable string map: itemCount + first few keys of first item.
        val out = LinkedHashMap<String, String>()
        out["items.count"] = items.size.toString()
        val first = items.firstOrNull()
        first?.keys?.sorted()?.take(20)?.forEach { k ->
            out["item0.$k"] = safeValueForLog(nodeType = "items", key = k, value = first[k])
        }
        return out
    }

    private fun diffForLog(before: Map<String, String>, after: Map<String, String>): String {
        val changed = mutableListOf<String>()
        val keys = (before.keys + after.keys).toSortedSet()
        keys.forEach { k ->
            val b = before[k]
            val a = after[k]
            if (b != a) {
                changed += "$k:${b ?: "∅"}→${a ?: "∅"}"
            }
        }
        return when {
            changed.isEmpty() -> "[]"
            changed.size <= 10 -> changed.joinToString(prefix = "[", postfix = "]")
            else -> changed.take(10).joinToString(prefix = "[", postfix = ", … +${changed.size - 10}]")
        }
    }

    private fun safeValueForLog(nodeType: String, key: String, value: Any?): String {
        if (value == null) return "null"

        // Redact potentially large/sensitive payloads.
        val k = key.lowercase()
        val shouldRedact =
            k.contains("image") ||
                k.contains("body") ||
                k.contains("base64") ||
                (nodeType == "Curl" && k.contains("response")) ||
                (nodeType == "CaptureImage" && (k.contains("image") || k.contains("bytes")))
        if (shouldRedact) return "<redacted>"

        val s = value.toString()
        if (s.length <= maxLogValueLen) return s
        return s.take(maxLogValueLen) + "…"
    }

    /**
     * Minimal URL policy for low-code HTTP requests.
     *
     * SECURITY:
     * - Default-deny for local/private hosts to reduce SSRF risk.
     * - Allow https only for MVP.
     */
    private fun validateHttpUrl(url: String): String? {
        val u = try {
            url.toUri()
        } catch (_: Exception) {
            return "invalid_url"
        }

        val scheme = (u.scheme ?: "").lowercase()
        if (scheme != "https") return "scheme_not_allowed"

        val host = (u.host ?: "").lowercase()
        if (host.isBlank()) return "missing_host"

        // Block common localhost variants.
        if (host == "localhost" || host == "127.0.0.1" || host == "0.0.0.0" || host == "::1") {
            return "local_host_blocked"
        }

        // Block private IPv4 ranges to reduce SSRF risk (only if host is an IPv4 literal).
        val ipv4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""").matchEntire(host)
        if (ipv4 != null) {
            val parts = ipv4.groupValues.drop(1).map { it.toIntOrNull() ?: 999 }
            if (parts.any { it !in 0..255 }) return "invalid_ipv4"
            val (a, b) = parts[0] to parts[1]
            val isPrivate =
                a == 10 ||
                    (a == 192 && b == 168) ||
                    (a == 172 && b in 16..31) ||
                    a == 127
            if (isPrivate) return "private_ipv4_blocked"
        }

        return null
    }

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

