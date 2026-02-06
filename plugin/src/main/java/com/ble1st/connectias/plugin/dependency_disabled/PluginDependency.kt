package com.ble1st.connectias.plugin.dependency

/**
 * Represents a plugin dependency with version constraints
 */
data class PluginDependency(
    val pluginId: String,
    val versionConstraint: VersionConstraint,
    val isOptional: Boolean = false,
    val reason: String? = null
)

/**
 * Version constraint using semantic versioning
 */
sealed class VersionConstraint {
    abstract fun satisfies(version: String): Boolean
    
    data class Exact(val version: String) : VersionConstraint() {
        override fun satisfies(version: String): Boolean {
            return version == this.version
        }
        
        override fun toString(): String = version
    }
    
    data class Range(val min: String? = null, val max: String? = null, val includeMin: Boolean = true, val includeMax: Boolean = true) : VersionConstraint() {
        override fun satisfies(version: String): Boolean {
            val comparator = SemanticVersionComparator()
            
            min?.let { minVersion ->
                val comparison = comparator.compare(version, minVersion)
                if (includeMin && comparison < 0) return false
                if (!includeMin && comparison <= 0) return false
            }
            
            max?.let { maxVersion ->
                val comparison = comparator.compare(version, maxVersion)
                if (includeMax && comparison > 0) return false
                if (!includeMax && comparison >= 0) return false
            }
            
            return true
        }
        
        override fun toString(): String {
            val left = if (includeMin) "[" else "("
            val right = if (includeMax) "]" else ")"
            return "$left${min ?: ""},${max ?: ""}$right"
        }
    }
    
    data class CompatibleWith(val baseVersion: String) : VersionConstraint() {
        override fun satisfies(version: String): Boolean {
            val comparator = SemanticVersionComparator()
            val baseParts = baseVersion.split(".")
            val versionParts = version.split(".")
            
            if (baseParts.size >= 2 && versionParts.size >= 2) {
                if (baseParts[0] != versionParts[0] || baseParts[1] != versionParts[1]) {
                    return false
                }
            }
            
            return comparator.compare(version, baseVersion) >= 0
        }
        
        override fun toString(): String = "~$baseVersion"
    }
    
    data class AnyOf(val constraints: List<VersionConstraint>) : VersionConstraint() {
        override fun satisfies(version: String): Boolean {
            return constraints.any { it.satisfies(version) }
        }
        
        override fun toString(): String = constraints.joinToString(" || ")
    }
    
    data class AllOf(val constraints: List<VersionConstraint>) : VersionConstraint() {
        override fun satisfies(version: String): Boolean {
            return constraints.all { it.satisfies(version) }
        }
        
        override fun toString(): String = constraints.joinToString(" && ")
    }
    
    object Any : VersionConstraint() {
        override fun satisfies(version: String): Boolean = true
        
        override fun toString(): String = "*"
    }
    
    companion object {
        fun fromString(constraintString: String): VersionConstraint {
            return when {
                constraintString == "*" -> Any
                constraintString.startsWith("~") -> CompatibleWith(constraintString.substring(1))
                constraintString.contains("||") -> {
                    val parts = constraintString.split("||").map { it.trim() }
                    AnyOf(parts.map { fromString(it) })
                }
                constraintString.contains("&&") -> {
                    val parts = constraintString.split("&&").map { it.trim() }
                    AllOf(parts.map { fromString(it) })
                }
                constraintString.startsWith("[") || constraintString.startsWith("(") -> {
                    val inner = constraintString.substring(1, constraintString.length - 1)
                    val parts = inner.split(",")
                    val min = parts.getOrNull(0)?.takeIf { it.isNotBlank() }
                    val max = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
                    Range(
                        min = min,
                        max = max,
                        includeMin = constraintString.startsWith("["),
                        includeMax = constraintString.endsWith("]")
                    )
                }
                constraintString.contains(">=") || constraintString.contains("<=") || 
                constraintString.contains(">") || constraintString.contains("<") -> {
                    val comparator = when {
                        constraintString.contains(">=") -> ">="
                        constraintString.contains("<=") -> "<="
                        constraintString.contains(">") -> ">"
                        constraintString.contains("<") -> "<"
                        else -> throw IllegalArgumentException("Invalid constraint: $constraintString")
                    }
                    val version = constraintString.substring(comparator.length).trim()
                    when (comparator) {
                        ">=" -> Range(min = version, includeMin = true)
                        "<=" -> Range(max = version, includeMax = true)
                        ">" -> Range(min = version, includeMin = false)
                        "<" -> Range(max = version, includeMax = false)
                        else -> throw IllegalArgumentException("Invalid comparator: $comparator")
                    }
                }
                else -> Exact(constraintString)
            }
        }
    }
}

/**
 * Comparator for semantic versions
 */
class SemanticVersionComparator {
    fun compare(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        
        val maxLength = maxOf(parts1.size, parts2.size)
        
        for (i in 0 until maxLength) {
            val part1 = parts1.getOrNull(i) ?: 0
            val part2 = parts2.getOrNull(i) ?: 0
            
            when {
                part1 < part2 -> return -1
                part1 > part2 -> return 1
            }
        }
        
        return 0
    }
}

/**
 * Represents a dependency graph node
 */
data class DependencyNode(
    val pluginId: String,
    val version: String,
    val dependencies: List<PluginDependency>,
    val dependents: List<String> = emptyList()
)

/**
 * Complete dependency graph
 */
data class DependencyGraph(
    val nodes: Map<String, DependencyNode>,
    val loadOrder: List<String>
) {
    fun getTransitiveDependencies(pluginId: String): Set<String> {
        val visited = mutableSetOf<String>()
        fun visit(id: String) {
            if (visited.contains(id)) return
            visited.add(id)
            nodes[id]?.dependencies?.forEach { dep ->
                visit(dep.pluginId)
            }
        }
        visit(pluginId)
        return visited - pluginId
    }
    
    fun getDependents(pluginId: String): Set<String> {
        return nodes.values
            .filter { node -> node.dependencies.any { it.pluginId == pluginId } }
            .map { it.pluginId }
            .toSet()
    }
}

/**
 * Resolution result
 */
data class DependencyResolutionResult(
    val success: Boolean,
    val loadOrder: List<String> = emptyList(),
    val missingDependencies: List<String> = emptyList(),
    val versionConflicts: List<VersionConflict> = emptyList(),
    val circularDependencies: List<CircularDependency> = emptyList(),
    val resolvedGraph: DependencyGraph? = null
)

/**
 * Version conflict information
 */
data class VersionConflict(
    val pluginId: String,
    val requiredBy: List<String>,
    val requestedVersions: List<String>
)

/**
 * Circular dependency information
 */
data class CircularDependency(
    val cycle: List<String>
)
