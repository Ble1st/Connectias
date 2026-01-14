package com.ble1st.connectias.plugin

import android.os.Parcel
import android.os.Parcelable

/**
 * Parcelable for permission results in AIDL
 * Used instead of Map which is not supported in AIDL
 */
data class PermissionResult(
    val permission: String,
    val granted: Boolean
) : Parcelable {
    
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readByte() != 0.toByte()
    )
    
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(permission)
        parcel.writeByte(if (granted) 1 else 0)
    }
    
    override fun describeContents(): Int {
        return 0
    }
    
    companion object CREATOR : Parcelable.Creator<PermissionResult> {
        override fun createFromParcel(parcel: Parcel): PermissionResult {
            return PermissionResult(parcel)
        }
        
        override fun newArray(size: Int): Array<PermissionResult?> {
            return arrayOfNulls(size)
        }
    }
}
