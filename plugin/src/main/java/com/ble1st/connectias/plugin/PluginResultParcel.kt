package com.ble1st.connectias.plugin

import android.os.Parcel
import android.os.Parcelable

/**
 * Parcelable result wrapper for IPC communication
 */
data class PluginResultParcel(
    val success: Boolean,
    val errorMessage: String?,
    val metadata: PluginMetadataParcel?
) : Parcelable {
    
    constructor(parcel: Parcel) : this(
        parcel.readByte() != 0.toByte(),
        parcel.readString(),
        parcel.readParcelable(PluginMetadataParcel::class.java.classLoader)
    )
    
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeByte(if (success) 1 else 0)
        parcel.writeString(errorMessage)
        parcel.writeParcelable(metadata, flags)
    }
    
    override fun describeContents(): Int = 0
    
    companion object CREATOR : Parcelable.Creator<PluginResultParcel> {
        override fun createFromParcel(parcel: Parcel): PluginResultParcel {
            return PluginResultParcel(parcel)
        }
        
        override fun newArray(size: Int): Array<PluginResultParcel?> {
            return arrayOfNulls(size)
        }
        
        fun success(metadata: PluginMetadataParcel? = null): PluginResultParcel {
            return PluginResultParcel(true, null, metadata)
        }
        
        fun failure(errorMessage: String): PluginResultParcel {
            return PluginResultParcel(false, errorMessage, null)
        }
    }
}
