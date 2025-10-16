package com.ble1st.connectias.pluginmanager

import com.ble1st.connectias.api.PluginInfo

class DependencyResolver(
    private val pluginLoader: PluginLoader
) {
    suspend fun checkDependencies(plugin: PluginInfo): DependencyCheckResult {
        val missingDependencies = mutableListOf<String>()
        val dependencies = plugin.dependencies ?: emptyList()
        
        dependencies.forEach { depId ->
            if (!pluginLoader.isPluginLoaded(depId)) {
                missingDependencies.add(depId)
            }
        }
        
        return DependencyCheckResult(
            hasMissingDependencies = missingDependencies.isNotEmpty(),
            missingDependencies = missingDependencies,
            allDependencies = dependencies
        )
    }
}

data class DependencyCheckResult(
    val hasMissingDependencies: Boolean,
    val missingDependencies: List<String>,
    val allDependencies: List<String>
)
