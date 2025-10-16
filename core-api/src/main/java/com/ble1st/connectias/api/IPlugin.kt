package com.ble1st.connectias.api

interface IPlugin {
    fun onCreate(context: PluginContext)
    fun onStart()
    fun onStop()
    fun onDestroy()
    fun getPluginInfo(): PluginInfo
}
