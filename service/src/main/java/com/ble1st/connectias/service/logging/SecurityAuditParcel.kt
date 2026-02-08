// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.service.logging

import android.os.Parcel
import android.os.Parcelable

/**
 * Parcelable wrapper for security audit events for IPC communication.
 */
data class SecurityAuditParcel(
    val id: Long,
    val timestamp: Long,
    val packageName: String,
    val eventType: String,
    val details: String
) : Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.readLong(),
        parcel.readLong(),
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(id)
        parcel.writeLong(timestamp)
        parcel.writeString(packageName)
        parcel.writeString(eventType)
        parcel.writeString(details)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<SecurityAuditParcel> {
        override fun createFromParcel(parcel: Parcel): SecurityAuditParcel =
            SecurityAuditParcel(parcel)

        override fun newArray(size: Int): Array<SecurityAuditParcel?> =
            arrayOfNulls(size)

        fun fromEntity(entity: SecurityAuditEntity): SecurityAuditParcel =
            SecurityAuditParcel(
                id = entity.id,
                timestamp = entity.timestamp,
                packageName = entity.packageName,
                eventType = entity.eventType,
                details = entity.details
            )

        fun fromEntities(entities: List<SecurityAuditEntity>): List<SecurityAuditParcel> =
            entities.map { fromEntity(it) }
    }
}
