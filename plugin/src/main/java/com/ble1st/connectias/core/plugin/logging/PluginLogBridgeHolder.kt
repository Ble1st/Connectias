package com.ble1st.connectias.core.plugin.logging

import com.ble1st.connectias.plugin.logging.IPluginLogBridge
import java.util.concurrent.atomic.AtomicBoolean

internal object PluginLogBridgeHolder {
    @Volatile
    var bridge: IPluginLogBridge? = null

    val isTreeInstalled = AtomicBoolean(false)
}

