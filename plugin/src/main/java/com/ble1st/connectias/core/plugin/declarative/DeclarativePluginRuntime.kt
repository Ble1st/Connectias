package com.ble1st.connectias.core.plugin.declarative

import android.os.Bundle
import com.ble1st.connectias.core.plugin.SandboxPluginContext
import com.ble1st.connectias.plugin.declarative.model.DeclarativeFlowDefinition
import com.ble1st.connectias.plugin.declarative.model.DeclarativeTrigger
import com.ble1st.connectias.plugin.declarative.model.DeclarativeUiComponent
import com.ble1st.connectias.plugin.declarative.model.DeclarativeUiScreen
import com.ble1st.connectias.plugin.messaging.MessageResponse
import com.ble1st.connectias.plugin.messaging.PluginMessage
import com.ble1st.connectias.plugin.ui.IPluginUIController
import com.ble1st.connectias.plugin.ui.UIComponentParcel
import com.ble1st.connectias.plugin.ui.UIStateParcel
import com.ble1st.connectias.plugin.ui.UserActionParcel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Runtime that executes declarative plugin packages inside sandbox.
 *
 * Responsibilities:
 * - Keep mutable state
 * - Render UIStateParcel from declarative UI definitions
 * - Execute flows on triggers (UI actions / timers)
 */
class DeclarativePluginRuntime(
    private val pluginId: String,
    private val uiController: IPluginUIController,
    private val sandboxContext: SandboxPluginContext,
    private val pkg: DeclarativePackageReader.PackageData,
) {
    private val state: MutableMap<String, Any?> = LinkedHashMap()
    private val persistence = DeclarativeStatePersistence(pluginId, sandboxContext)
    private val engine = DeclarativeFlowEngine(
        pluginId = pluginId,
        uiController = uiController,
        state = state,
        assets = pkg.assets,
        persistence = persistence,
        sandboxContext = sandboxContext,
    )

    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val timerJobs = mutableListOf<Job>()

    fun onLoad() {
        state["_currentScreenId"] = pkg.manifest.entrypoints.startScreenId
        persistence.loadInto(state)
        setupMessageTriggers()
    }

    fun onEnable() {
        startTimers()
        pushCurrentState()
    }

    fun onDisable() {
        stopTimers()
    }

    fun onUnload() {
        stopTimers()
    }

    fun onUILifecycle(event: String) {
        if (event == "onCreate") {
            pushCurrentState()
        }
    }

    fun onUserAction(action: UserActionParcel) {
        val currentScreenId = getCurrentScreenId()
        val actionData = bundleToMap(action.data)
        val triggerCtx = DeclarativeFlowEngine.TriggerContext(
            actionTargetId = action.targetId,
            actionData = actionData,
            items = mutableListOf(
                LinkedHashMap<String, Any?>().apply {
                    if (actionData != null) putAll(actionData)
                }
            )
        )

        pkg.flowsById.values.forEach { flow ->
            flow.triggers.forEach { trigger ->
                if (matchesTrigger(trigger, action)) {
                    engine.runFlow(flow, trigger, triggerCtx)
                }
            }
        }

        pushCurrentState()
    }

    fun render(screenId: String): UIStateParcel? {
        val screen = pkg.screensById[screenId] ?: return null
        return buildUiState(screen)
    }

    private fun pushCurrentState() {
        val screenId = getCurrentScreenId()
        val stateParcel = render(screenId)
        if (stateParcel != null) {
            uiController.updateUIState(pluginId, stateParcel)
        } else {
            Timber.w("[SANDBOX][DECL:$pluginId] No screen found for screenId=$screenId")
        }
    }

    private fun getCurrentScreenId(): String {
        return (state["_currentScreenId"] as? String)
            ?: pkg.manifest.entrypoints.startScreenId
    }

    private fun matchesTrigger(trigger: DeclarativeTrigger, action: UserActionParcel): Boolean {
        return when (trigger.type) {
            "OnClick" -> action.actionType == "click" && trigger.targetId == action.targetId
            "OnTextChanged" -> action.actionType == "text_changed" && trigger.targetId == action.targetId
            "OnAnyAction" -> true
            else -> false
        }
    }

    private fun setupMessageTriggers() {
        // Register message handlers once per messageType. If multiple flows use the same type,
        // they will all run sequentially in registration order.
        val byType = LinkedHashMap<String, MutableList<Pair<DeclarativeFlowDefinition, DeclarativeTrigger>>>()
        pkg.flowsById.values.forEach { flow ->
            flow.triggers.forEach { trigger ->
                if (trigger.type == "OnMessage" && !trigger.messageType.isNullOrBlank()) {
                    byType.getOrPut(trigger.messageType) { mutableListOf() }.add(flow to trigger)
                }
            }
        }

        byType.forEach { (messageType, items) ->
            sandboxContext.registerMessageHandler(messageType) { msg: PluginMessage ->
                try {
                    val item = linkedMapOf<String, Any?>(
                        "senderId" to msg.senderId,
                        "receiverId" to msg.receiverId,
                        "messageType" to msg.messageType,
                        "requestId" to msg.requestId,
                        "timestamp" to msg.timestamp,
                    )
                    val triggerCtx = DeclarativeFlowEngine.TriggerContext(
                        actionTargetId = msg.messageType,
                        actionData = mapOf(
                            "senderId" to msg.senderId,
                            "messageType" to msg.messageType
                        ),
                        items = mutableListOf(item)
                    )
                    items.forEach { (flow, trigger) ->
                        engine.runFlow(flow, trigger, triggerCtx)
                    }
                    pushCurrentState()
                    MessageResponse.success(requestId = msg.requestId)
                } catch (e: Exception) {
                    Timber.e(e, "[SANDBOX][DECL:$pluginId] OnMessage handler failed: $messageType")
                    MessageResponse.error(requestId = msg.requestId, errorMessage = e.message ?: "error")
                }
            }
        }
    }

    private fun startTimers() {
        stopTimers()
        pkg.flowsById.values.forEach { flow ->
            flow.triggers.forEach { trigger ->
                if (trigger.type == "OnTimer" && trigger.everyMs != null && trigger.everyMs > 0) {
                    val job = scope.launch {
                        while (true) {
                            delay(trigger.everyMs)
                            try {
                                engine.runFlow(
                                    flow,
                                    trigger,
                                    DeclarativeFlowEngine.TriggerContext(items = mutableListOf(LinkedHashMap()))
                                )
                                pushCurrentState()
                            } catch (e: Exception) {
                                Timber.e(e, "[SANDBOX][DECL:$pluginId] Timer flow failed")
                            }
                        }
                    }
                    timerJobs += job
                }
            }
        }
    }

    private fun stopTimers() {
        timerJobs.forEach { it.cancel() }
        timerJobs.clear()
    }

    private fun buildUiState(screen: DeclarativeUiScreen): UIStateParcel {
        val components = screen.components.map { toComponentParcel(it) }.toTypedArray()
        return UIStateParcel().apply {
            screenId = screen.screenId
            title = DeclarativeTemplate.render(screen.title, state)
            data = Bundle().apply {
                // Expose state snapshot (optional)
                putString("_state_json", org.json.JSONObject(state.filterKeys { !it.startsWith("_") }).toString())
            }
            this.components = components
            timestamp = System.currentTimeMillis()
        }
    }

    private fun toComponentParcel(component: DeclarativeUiComponent): UIComponentParcel {
        val children = component.children.map { toComponentParcel(it) }.toTypedArray()
        val props = Bundle()
        component.properties.forEach { (k, v) ->
            when (v) {
                null -> Unit
                is Boolean -> props.putBoolean(k, v)
                is Int -> props.putInt(k, v)
                is Long -> props.putLong(k, v)
                is Double -> props.putDouble(k, v)
                is Float -> props.putFloat(k, v)
                is String -> props.putString(k, DeclarativeTemplate.render(v, state))
                is Number -> props.putDouble(k, v.toDouble())
                else -> props.putString(k, DeclarativeTemplate.render(v.toString(), state))
            }
        }

        // Asset resolution helper for images
        val assetPath = component.properties["assetPath"]?.toString()
        if (!assetPath.isNullOrBlank() && component.type == "IMAGE") {
            val fullPath = if (assetPath.startsWith("assets/")) assetPath else "assets/$assetPath"
            val bytes = pkg.assets[fullPath]
            if (bytes != null) {
                val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                props.putString("base64Data", b64)
            } else {
                Timber.w("[SANDBOX][DECL:$pluginId] Missing asset: $fullPath")
            }
        }

        return UIComponentParcel().apply {
            id = component.id
            type = component.type
            properties = props
            this.children = children
        }
    }

    private fun bundleToMap(bundle: Bundle?): Map<String, Any?>? {
        if (bundle == null) return null
        val out = LinkedHashMap<String, Any?>()
        for (k in bundle.keySet()) {
            out[k] = bundle.get(k)
        }
        return out
    }
}

