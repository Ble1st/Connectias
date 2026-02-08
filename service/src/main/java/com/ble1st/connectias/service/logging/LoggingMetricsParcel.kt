// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.service.logging

import android.os.Parcel
import android.os.Parcelable

/**
 * Parcelable snapshot of LoggingService runtime security metrics.
 * Transferred via AIDL to the main process for monitoring and dashboards.
 */
data class LoggingMetricsParcel(
    /** Total log submissions received since service start. */
    val totalLogsReceived: Long,

    /** Number of submissions blocked by rate limiting. */
    val rateLimitViolations: Long,

    /** Number of submissions rejected by input validation. */
    val validationFailures: Long,

    /** Database file size in bytes, or -1 if unavailable. */
    val databaseSize: Long,

    /** Epoch ms of the oldest log entry, or 0 if the database is empty. */
    val oldestLogTimestamp: Long,

    /** Total number of security audit events in the audit table. */
    val auditEventCount: Int
) : Parcelable {

    constructor(parcel: Parcel) : this(
        parcel.readLong(),
        parcel.readLong(),
        parcel.readLong(),
        parcel.readLong(),
        parcel.readLong(),
        parcel.readInt()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(totalLogsReceived)
        parcel.writeLong(rateLimitViolations)
        parcel.writeLong(validationFailures)
        parcel.writeLong(databaseSize)
        parcel.writeLong(oldestLogTimestamp)
        parcel.writeInt(auditEventCount)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<LoggingMetricsParcel> {
        override fun createFromParcel(parcel: Parcel): LoggingMetricsParcel =
            LoggingMetricsParcel(parcel)

        override fun newArray(size: Int): Array<LoggingMetricsParcel?> =
            arrayOfNulls(size)
    }
}
