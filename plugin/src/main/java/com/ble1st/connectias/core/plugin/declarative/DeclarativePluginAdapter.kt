package com.ble1st.connectias.core.plugin.declarative

import com.ble1st.connectias.core.plugin.SandboxPluginContext
import com.ble1st.connectias.plugin.sdk.IPlugin
import com.ble1st.connectias.plugin.sdk.PluginContext
import com.ble1st.connectias.plugin.sdk.PluginMetadata
import com.ble1st.connectias.plugin.ui.IPluginUIController
import com.ble1st.connectias.plugin.ui.UIStateParcel
import com.ble1st.connectias.plugin.ui.UserActionParcel

/**
 * Adapter that exposes a declarative plugin package as an IPlugin instance.
 *
 * The actual logic is handled by DeclarativePluginRuntime (trusted host code).
 */
class DeclarativePluginAdapter(
    private val metadata: PluginMetadata,
    private val uiController: IPluginUIController,
    private val sandboxContext: SandboxPluginContext,
    private val pkg: DeclarativePackageReader.PackageData
) : IPlugin {

    private val runtime = DeclarativePluginRuntime(
        pluginId = metadata.pluginId,
        uiController = uiController,
        sandboxContext = sandboxContext,
        pkg = pkg
    )

    override fun getMetadata(): PluginMetadata = metadata

    override fun onLoad(context: PluginContext): Boolean {
        runtime.onLoad()
        return true
    }

    override fun onEnable(): Boolean {
        runtime.onEnable()
        return true
    }

    override fun onDisable(): Boolean {
        runtime.onDisable()
        return true
    }

    override fun onUnload(): Boolean {
        runtime.onUnload()
        return true
    }

    override fun onRenderUI(screenId: String): UIStateParcel? {
        return runtime.render(screenId)
    }

    override fun onUserAction(action: UserActionParcel) {
        runtime.onUserAction(action)
    }

    override fun onUILifecycle(event: String) {
        runtime.onUILifecycle(event)
    }
}

