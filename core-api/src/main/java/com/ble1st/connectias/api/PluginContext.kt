package com.ble1st.connectias.api

interface PluginContext {
    fun getStorageService(): StorageService
    fun getNetworkService(): NetworkService
    fun getLogger(): PluginLogger
    fun getSystemInfoService(): SystemInfoService
    fun requestPermission(permission: PluginPermission, callback: (Boolean) -> Unit)
    fun publishMessage(topic: String, data: Any)
    fun subscribeToMessages(topic: String, callback: (Any) -> Unit)
}

// Plugin-UI Definition
interface PluginUI {
    fun createViewModel(): Any
}
