package com.ble1st.connectias.plugin.version

import android.os.Parcel
import android.os.Parcelable
import java.util.Date

/**
 * Represents a plugin version with metadata
 */
data class PluginVersion(
    val version: String,
    val versionCode: Int,
    val releaseDate: Date,
    val changelog: String,
    val minHostVersion: String,
    val dependencies: List<String> = emptyList(),
    val size: Long,
    val checksum: String,
    val downloadUrl: String,
    val isPrerelease: Boolean = false,
    val isRollback: Boolean = false
) : Parcelable {
    
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(version)
        parcel.writeInt(versionCode)
        parcel.writeLong(releaseDate.time)
        parcel.writeString(changelog)
        parcel.writeString(minHostVersion)
        parcel.writeStringList(dependencies)
        parcel.writeLong(size)
        parcel.writeString(checksum)
        parcel.writeString(downloadUrl)
        parcel.writeInt(if (isPrerelease) 1 else 0)
        parcel.writeInt(if (isRollback) 1 else 0)
    }
    
    override fun describeContents(): Int = 0
    
    companion object CREATOR : Parcelable.Creator<PluginVersion> {
        override fun createFromParcel(parcel: Parcel): PluginVersion {
            return PluginVersion(
                version = parcel.readString() ?: "",
                versionCode = parcel.readInt(),
                releaseDate = Date(parcel.readLong()),
                changelog = parcel.readString() ?: "",
                minHostVersion = parcel.readString() ?: "",
                dependencies = parcel.createStringArrayList() ?: emptyList(),
                size = parcel.readLong(),
                checksum = parcel.readString() ?: "",
                downloadUrl = parcel.readString() ?: "",
                isPrerelease = parcel.readInt() == 1,
                isRollback = parcel.readInt() == 1
            )
        }
        
        override fun newArray(size: Int): Array<PluginVersion?> {
            return arrayOfNulls(size)
        }
        
        fun fromString(versionString: String): PluginVersion? {
            return try {
                val parts = versionString.split("-")
                val version = parts.getOrNull(0) ?: return null
                val versionCode = parts.getOrNull(1)?.toIntOrNull() ?: return null
                val isPrerelease = parts.getOrNull(2) == "beta"
                
                PluginVersion(
                    version = version,
                    versionCode = versionCode,
                    releaseDate = Date(),
                    changelog = "",
                    minHostVersion = "1.0.0",
                    size = 0L,
                    checksum = "",
                    downloadUrl = "",
                    isPrerelease = isPrerelease
                )
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Compare two versions
     */
    fun isNewerThan(other: PluginVersion): Boolean {
        return this.versionCode > other.versionCode
    }
    
    /**
     * Check if this version is compatible with host version
     */
    fun isCompatibleWith(hostVersion: String): Boolean {
        return compareVersions(hostVersion, this.minHostVersion) >= 0
    }
    
    private fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        
        val maxLength = maxOf(parts1.size, parts2.size)
        
        for (i in 0 until maxLength) {
            val p1 = parts1.getOrNull(i) ?: 0
            val p2 = parts2.getOrNull(i) ?: 0
            
            when {
                p1 > p2 -> return 1
                p1 < p2 -> return -1
            }
        }
        
        return 0
    }
    
    /**
     * Get display name
     */
    fun getDisplayName(): String {
        return if (isPrerelease) "$version (Beta)" else version
    }
}

/**
 * Version update information
 */
data class PluginVersionUpdate(
    val pluginId: String,
    val currentVersion: PluginVersion,
    val availableVersion: PluginVersion,
    val updateType: UpdateType,
    val isMandatory: Boolean = false
) {
    enum class UpdateType {
        PATCH,      // 1.0.0 -> 1.0.1
        MINOR,      // 1.0.0 -> 1.1.0
        MAJOR,      // 1.0.0 -> 2.0.0
        PRERELEASE  // 1.0.0 -> 2.0.0-beta
    }
}

/**
 * Version history entry
 */
data class PluginVersionHistory(
    val pluginId: String,
    val version: PluginVersion,
    val installedAt: Date,
    val uninstalledAt: Date? = null,
    val rollbackReason: String? = null
)
