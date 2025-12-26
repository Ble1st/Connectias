package com.ble1st.connectias.feature.dnstools.data

data class DnsQueryResult(
    val domain: String,
    val type: String,
    val records: List<String>,
    val error: String? = null
)



