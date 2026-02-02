@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package com.ble1st.connectias.analytics.export

import kotlinx.serialization.Serializable

@Serializable
data class AnalyticsExportBundle(
    val metadata: AnalyticsExportMetadata,
    val pluginStats: List<AnalyticsPluginStat> = emptyList()
)

@Serializable
data class AnalyticsExportMetadata(
    val schemaVersion: Int = 1,
    val createdAtEpochMillis: Long,
    val windowLabel: String,
    val windowStartEpochMillis: Long,
    val windowEndEpochMillis: Long
)

@Serializable
data class AnalyticsPluginStat(
    val pluginId: String,
    val samples: Int,
    val avgCpu: Float,
    val peakCpu: Float,
    val avgMemMB: Float,
    val peakMemMB: Int,
    val netInBytes: Long,
    val netOutBytes: Long,
    val uiActions: Int,
    val rateLimitHits: Int
)

