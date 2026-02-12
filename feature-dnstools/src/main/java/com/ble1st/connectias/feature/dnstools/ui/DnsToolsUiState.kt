package com.ble1st.connectias.feature.dnstools.ui

import com.ble1st.connectias.feature.dnstools.data.DnsHistoryEntity
import com.ble1st.connectias.feature.dnstools.data.DnsQueryResult

data class DnsToolsUiState(
    val dnsQuery: String = "",
    val selectedRecordType: DnsRecordType = DnsRecordType.A,
    val dnsResult: DnsQueryResult? = null,
    val dnsLoading: Boolean = false,
    
    val history: List<DnsHistoryEntity> = emptyList()
)
