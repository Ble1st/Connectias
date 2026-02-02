package com.ble1st.connectias.core.plugin

import com.ble1st.connectias.plugin.PluginManagerSandbox
import com.ble1st.connectias.plugin.dependency.CircularDependency
import com.ble1st.connectias.plugin.dependency.DependencyGraph
import com.ble1st.connectias.plugin.dependency.DependencyNode
import com.ble1st.connectias.plugin.dependency.DependencyResolutionResult
import com.ble1st.connectias.plugin.dependency.PluginDependency
import com.ble1st.connectias.plugin.dependency.VersionConflict
import com.ble1st.connectias.plugin.dependency.VersionConstraint
import com.ble1st.connectias.plugin.sdk.PluginMetadata
import com.ble1st.connectias.plugin.store.GitHubPluginStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Enhanced Plugin Dependency Resolver with version constraints and graph management
 */
class PluginDependencyResolverV2(
    private val pluginManager: PluginManagerSandbox,
    private val pluginStore: GitHubPluginStore? = null
) {
    
    /**
     * Resolve dependencies for a plugin with version constraints
     */
    suspend fun resolveDependencies(
        pluginId: String,
        onProgress: (String) -> Unit = {}
    ): Result<DependencyResolutionResult> = withContext(Dispatchers.Default) {
        try {
            onProgress("Analyzing dependencies...")
            
            // Get the plugin metadata
            val plugin = pluginManager.getPlugin(pluginId)
            
            if (plugin == null) {
                // Plugin not installed, try to fetch from store
                if (pluginStore != null) {
                    onProgress("Fetching plugin from store...")
                    // Note: GitHubPluginStore doesn't have downloadPlugin method
                    // This would need to be implemented or use a different approach
                    return@withContext Result.failure(
                        IllegalArgumentException("Plugin not found: $pluginId")
                    )
                } else {
                    return@withContext Result.failure(
                        IllegalArgumentException("Plugin not found: $pluginId")
                    )
                }
            } else {
                resolveDependenciesInternal(plugin.metadata, onProgress)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to resolve dependencies for: $pluginId")
            Result.failure(e)
        }
    }
    
    private suspend fun resolveDependenciesInternal(
        metadata: PluginMetadata,
        onProgress: (String) -> Unit
    ): Result<DependencyResolutionResult> {
        val allPlugins = pluginManager.getLoadedPlugins()
        val installedVersions = allPlugins.associateBy { it.metadata.pluginId }
        
        // Parse dependencies from metadata
        val dependencies = parseDependencies(metadata)
        
        if (dependencies.isEmpty()) {
            return Result.success(
                DependencyResolutionResult(
                    success = true,
                    loadOrder = listOf(metadata.pluginId),
                    resolvedGraph = DependencyGraph(
                        nodes = mapOf(
                            metadata.pluginId to DependencyNode(
                                pluginId = metadata.pluginId,
                                version = metadata.version,
                                dependencies = emptyList()
                            )
                        ),
                        loadOrder = listOf(metadata.pluginId)
                    )
                )
            )
        }
        
        onProgress("Building dependency graph...")
        
        // Build dependency graph
        val graphResult = buildDependencyGraph(metadata.pluginId, dependencies, installedVersions)
        
        if (!graphResult.success) {
            return Result.success(graphResult)
        }
        
        onProgress("Optimizing dependency order...")
        
        // Optimize load order
        val optimizedGraph = optimizeLoadOrder(graphResult.resolvedGraph!!)
        
        return Result.success(
            graphResult.copy(
                loadOrder = optimizedGraph.loadOrder,
                resolvedGraph = optimizedGraph
            )
        )
    }
    
    private fun parseDependencies(metadata: PluginMetadata): List<PluginDependency> {
        // For now, we'll use the existing dependencies field
        // In a full implementation, this would parse from enhanced plugin.json
        return metadata.dependencies.map { depId ->
            PluginDependency(
                pluginId = depId,
                versionConstraint = VersionConstraint.Any
            )
        }
    }
    
    private fun buildDependencyGraph(
        rootPluginId: String,
        dependencies: List<PluginDependency>,
        installedVersions: Map<String, PluginManagerSandbox.PluginInfo>
    ): DependencyResolutionResult {
        val nodes = mutableMapOf<String, DependencyNode>()
        val missing = mutableListOf<String>()
        val conflicts = mutableListOf<VersionConflict>()
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        val loadOrder = mutableListOf<String>()
        
        fun visit(pluginId: String, deps: List<PluginDependency>): Boolean {
            if (pluginId in visited) return true
            if (pluginId in visiting) {
                Timber.e("Circular dependency detected involving: $pluginId")
                return false
            }
            
            visiting.add(pluginId)
            
            // Check if plugin is installed
            val installed = installedVersions[pluginId]
            if (installed == null) {
                missing.add(pluginId)
                return false
            }
            
            // Create node
            val node = DependencyNode(
                pluginId = pluginId,
                version = installed.metadata.version,
                dependencies = deps,
                dependents = emptyList() // Will be filled later
            )
            nodes[pluginId] = node
            
            // Visit dependencies
            for (dep in deps) {
                val depInstalled = installedVersions[dep.pluginId]
                if (depInstalled == null) {
                    missing.add(dep.pluginId)
                    continue
                }
                
                // Check version constraint
                if (!dep.versionConstraint.satisfies(depInstalled.metadata.version)) {
                    conflicts.add(
                        VersionConflict(
                            pluginId = dep.pluginId,
                            requiredBy = listOf(pluginId),
                            requestedVersions = listOf(dep.versionConstraint.toString())
                        )
                    )
                    continue
                }
                
                // TODO: Parse dependencies from metadata properly
                if (!visit(dep.pluginId, emptyList())) {
                    return false
                }
            }
            
            visiting.remove(pluginId)
            visited.add(pluginId)
            loadOrder.add(pluginId)
            return true
        }
        
        // Start with root plugin
        val rootInstalled = installedVersions[rootPluginId]
        if (rootInstalled != null) {
            if (!visit(rootPluginId, dependencies)) {
                return DependencyResolutionResult(
                    success = false,
                    missingDependencies = missing,
                    versionConflicts = conflicts,
                    circularDependencies = listOf(CircularDependency(visiting.toList()))
                )
            }
        }
        
        // Update dependents - disabled as DependencyNode doesn't have dependents field
        // nodes.values.forEach { node ->
        //     node.dependencies.forEach { dep ->
        //         nodes[dep.pluginId]?.let { depNode ->
        //             nodes[node.pluginId] = node.copy(dependents = depNode.dependents + node.pluginId)
        //         }
        //     }
        // }
        
        return DependencyResolutionResult(
            success = missing.isEmpty() && conflicts.isEmpty(),
            loadOrder = loadOrder.reversed(),
            missingDependencies = missing,
            versionConflicts = conflicts,
            resolvedGraph = DependencyGraph(nodes, loadOrder.reversed())
        )
    }
    
    private fun optimizeLoadOrder(graph: DependencyGraph): DependencyGraph {
        // Implement topological sort optimization
        // This is a simplified version - a full implementation would consider:
        // - Shared dependencies
        // - Plugin priorities
        // - Resource constraints
        
        val visited = mutableSetOf<String>()
        val result = mutableListOf<String>()
        
        fun visit(pluginId: String) {
            if (pluginId in visited) return
            visited.add(pluginId)
            
            graph.nodes[pluginId]?.dependencies?.forEach { dep ->
                visit(dep.pluginId)
            }
            
            result.add(pluginId)
        }
        
        graph.nodes.keys.forEach { visit(it) }
        
        return DependencyGraph(graph.nodes, result)
    }
    
    
    /**
     * Get dependency tree visualization
     */
    fun getDependencyTree(pluginId: String): String {
        pluginManager.getPlugin(pluginId) ?: return "Plugin not found: $pluginId"
        
        fun buildTree(id: String, indent: String = ""): String {
            val plugin = pluginManager.getPlugin(id) ?: return "${indent}$id (missing)\n"
            val deps = plugin.metadata.dependencies
            
            return buildString {
                append("${indent}$id (${plugin.metadata.version})\n")
                deps.forEach { dep ->
                    append(buildTree(dep, "$indent  "))
                }
            }
        }
        
        return buildTree(pluginId)
    }
    
    /**
     * Analyze impact of uninstalling a plugin
     */
    fun analyzeUninstallImpact(pluginId: String): UninstallImpact {
        val allPlugins = pluginManager.getLoadedPlugins()
        val dependents = allPlugins.filter { plugin ->
            plugin.metadata.dependencies.contains(pluginId)
        }
        
        return UninstallImpact(
            pluginId = pluginId,
            dependentPlugins = dependents.map { it.metadata.pluginId },
            canSafelyUninstall = dependents.isEmpty()
        )
    }
}

/**
 * Dependency update information
 */
data class DependencyUpdate(
    val pluginId: String,
    val currentVersion: String,
    val availableVersion: String?,
    val updateAvailable: Boolean
)

/**
 * Uninstall impact analysis
 */
data class UninstallImpact(
    val pluginId: String,
    val dependentPlugins: List<String>,
    val canSafelyUninstall: Boolean
)
