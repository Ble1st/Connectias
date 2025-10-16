package com.ble1st.connectias.pluginmanager

class PluginVersionManager {
    fun checkVersionConflict(existingVersion: String, newVersion: String): VersionConflictResult {
        val existing = parseVersion(existingVersion)
        val new = parseVersion(newVersion)
        
        return when {
            new > existing -> VersionConflictResult(
                isDowngrade = false,
                action = VersionConflictAction.ALLOW,
                message = "Update von v$existingVersion auf v$newVersion"
            )
            new < existing -> VersionConflictResult(
                isDowngrade = true,
                action = VersionConflictAction.WARN,
                message = "Downgrade von v$existingVersion auf v$newVersion"
            )
            else -> VersionConflictResult(
                isDowngrade = false,
                action = VersionConflictAction.SKIP,
                message = "Version v$newVersion ist bereits installiert"
            )
        }
    }
    
    private fun parseVersion(version: String): Version {
        val parts = version.split(".")
        return Version(
            major = parts.getOrNull(0)?.toIntOrNull() ?: 0,
            minor = parts.getOrNull(1)?.toIntOrNull() ?: 0,
            patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        )
    }
}

data class Version(
    val major: Int,
    val minor: Int,
    val patch: Int
) : Comparable<Version> {
    override fun compareTo(other: Version): Int {
        return compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
    }
}

data class VersionConflictResult(
    val isDowngrade: Boolean,
    val action: VersionConflictAction,
    val message: String
)

enum class VersionConflictAction {
    ALLOW,   // Update
    WARN,    // Downgrade mit Warnung
    SKIP     // Gleiche Version
}
