package com.ble1st.connectias.feature.dnstools.ui

import com.ble1st.connectias.feature.dnstools.data.CaptivePortalResult
import com.ble1st.connectias.feature.dnstools.data.DnsHistoryEntity
import com.ble1st.connectias.feature.dnstools.data.DnsQueryResult
import com.ble1st.connectias.feature.dnstools.data.PingResult
import com.ble1st.connectias.feature.dnstools.data.SubnetResult
import com.ble1st.connectias.feature.dnstools.data.WhoisResult

data class DnsToolsUiState(
    val dnsQuery: String = "",
    val selectedRecordType: DnsRecordType = DnsRecordType.A,
    val dnsResult: DnsQueryResult? = null,
    val dnsLoading: Boolean = false,

    val subnetCidr: String = "",
    val subnetResult: SubnetResult? = null,
    val subnetLoading: Boolean = false,

    val pingHost: String = "",
    val pingResult: PingResult? = null,
    val pingLoading: Boolean = false,

    val captivePortalResult: CaptivePortalResult? = null,
    val captivePortalLoading: Boolean = false,
    
    val history: List<DnsHistoryEntity> = emptyList()
)
