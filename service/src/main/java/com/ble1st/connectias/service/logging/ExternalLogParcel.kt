// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.service.logging

import android.os.Parcel
import android.os.Parcelable

/**
 * Parcelable wrapper for external log entries for IPC communication.
 * Used to transfer logs between isolated process and main process via AIDL.
 */
data class ExternalLogParcel(
    val id: Long,
    val timestamp: Long,
    val packageName: String,
    val level: String,
    val tag: String,
    val message: String,
    val exceptionTrace: String?
) : Parcelable {
    
    constructor(parcel: Parcel) : this(
        parcel.readLong(),
        parcel.readLong(),
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString()
    )
    
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(id)
        parcel.writeLong(timestamp)
        parcel.writeString(packageName)
        parcel.writeString(level)
        parcel.writeString(tag)
        parcel.writeString(message)
        parcel.writeString(exceptionTrace)
    }
    
    override fun describeContents(): Int = 0
    
    companion object CREATOR : Parcelable.Creator<ExternalLogParcel> {
        override fun createFromParcel(parcel: Parcel): ExternalLogParcel {
            return ExternalLogParcel(parcel)
        }
        
        override fun newArray(size: Int): Array<ExternalLogParcel?> {
            return arrayOfNulls(size)
        }
        
        /**
         * Convert from database entity to Parcel for IPC.
         */
        fun fromEntity(entity: ExternalLogEntity): ExternalLogParcel {
            return ExternalLogParcel(
                id = entity.id,
                timestamp = entity.timestamp,
                packageName = entity.packageName,
                level = entity.level,
                tag = entity.tag,
                message = entity.message,
                exceptionTrace = entity.exceptionTrace
            )
        }
        
        /**
         * Convert list of entities to list of Parcels.
         */
        fun fromEntities(entities: List<ExternalLogEntity>): List<ExternalLogParcel> {
            return entities.map { fromEntity(it) }
        }
    }
    
    /**
     * Convert Parcel back to database entity for storage.
     */
    fun toEntity(): ExternalLogEntity {
        return ExternalLogEntity(
            id = if (id == 0L) 0 else id, // Auto-generate ID if 0
            timestamp = timestamp,
            packageName = packageName,
            level = level,
            tag = tag,
            message = message,
            exceptionTrace = exceptionTrace
        )
    }
}
