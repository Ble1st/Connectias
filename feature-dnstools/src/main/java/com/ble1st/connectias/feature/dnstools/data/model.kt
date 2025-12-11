package com.ble1st.connectias.feature.dnstools.data

data class DnsQueryResult(
    val domain: String,
    val type: String,
    val records: List<String>,
    val error: String? = null
)

data class WhoisResult(
    val domain: String,
    val raw: String,
    val error: String? = null
)

data class SubnetResult(
    val input: String? = null,
    val networkAddress: String? = null,
    val broadcastAddress: String? = null,
    val firstHost: String? = null,
    val lastHost: String? = null,
    val usableHosts: Long? = null,
    val error: String? = null
)

data class PingResult(
    val host: String,
    val success: Boolean,
    val latencyMs: Long?,
    val error: String? = null
)

enum class CaptivePortalStatus { OPEN, CAPTIVE, UNKNOWN }

data class CaptivePortalResult(
    val status: CaptivePortalStatus,
    val httpCode: Int? = null,
    val error: String? = null
)

data class MarkdownDocument(
    val name: String?,
    val content: String
)
