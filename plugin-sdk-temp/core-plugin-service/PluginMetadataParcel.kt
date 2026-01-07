package com.ble1st.connectias.plugin

import android.os.Parcel
import android.os.Parcelable
import com.ble1st.connectias.plugin.PluginMetadata
import com.ble1st.connectias.plugin.PluginCategory

/**
 * Parcelable wrapper for PluginMetadata for IPC communication
 */
data class PluginMetadataParcel(
    val pluginId: String,
    val pluginName: String,
    val version: String,
    val author: String,
    val minApiLevel: Int,
    val maxApiLevel: Int,
    val minAppVersion: String,
    val nativeLibraries: List<String>,
    val fragmentClassName: String,
    val description: String,
    val permissions: List<String>,
    val category: String,
    val dependencies: List<String>
) : Parcelable {
    
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readInt(),
        parcel.readInt(),
        parcel.readString() ?: "",
        parcel.createStringArrayList() ?: emptyList(),
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.createStringArrayList() ?: emptyList(),
        parcel.readString() ?: "",
        parcel.createStringArrayList() ?: emptyList()
    )
    
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(pluginId)
        parcel.writeString(pluginName)
        parcel.writeString(version)
        parcel.writeString(author)
        parcel.writeInt(minApiLevel)
        parcel.writeInt(maxApiLevel)
        parcel.writeString(minAppVersion)
        parcel.writeStringList(nativeLibraries)
        parcel.writeString(fragmentClassName)
        parcel.writeString(description)
        parcel.writeStringList(permissions)
        parcel.writeString(category)
        parcel.writeStringList(dependencies)
    }
    
    override fun describeContents(): Int = 0
    
    companion object CREATOR : Parcelable.Creator<PluginMetadataParcel> {
        override fun createFromParcel(parcel: Parcel): PluginMetadataParcel {
            return PluginMetadataParcel(parcel)
        }
        
        override fun newArray(size: Int): Array<PluginMetadataParcel?> {
            return arrayOfNulls(size)
        }
    }
    
    fun toPluginMetadata(): PluginMetadata {
        return PluginMetadata(
            pluginId = pluginId,
            pluginName = pluginName,
            version = version,
            author = author,
            minApiLevel = minApiLevel,
            maxApiLevel = maxApiLevel,
            minAppVersion = minAppVersion,
            nativeLibraries = nativeLibraries,
            fragmentClassName = fragmentClassName,
            description = description,
            permissions = permissions,
            category = PluginCategory.valueOf(category),
            dependencies = dependencies
        )
    }
    
    companion object {
        fun fromPluginMetadata(metadata: PluginMetadata): PluginMetadataParcel {
            return PluginMetadataParcel(
                pluginId = metadata.pluginId,
                pluginName = metadata.pluginName,
                version = metadata.version,
                author = metadata.author,
                minApiLevel = metadata.minApiLevel,
                maxApiLevel = metadata.maxApiLevel,
                minAppVersion = metadata.minAppVersion,
                nativeLibraries = metadata.nativeLibraries,
                fragmentClassName = metadata.fragmentClassName ?: "",
                description = metadata.description,
                permissions = metadata.permissions,
                category = metadata.category.name,
                dependencies = metadata.dependencies
            )
        }
    }
}
