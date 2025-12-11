package com.ble1st.connectias.feature.dnstools.ui

import com.ble1st.connectias.feature.dnstools.data.CaptivePortalResult
import com.ble1st.connectias.feature.dnstools.data.DnsQueryResult
import com.ble1st.connectias.feature.dnstools.data.MarkdownDocument
import com.ble1st.connectias.feature.dnstools.data.PingResult
import com.ble1st.connectias.feature.dnstools.data.SubnetResult
import com.ble1st.connectias.feature.dnstools.data.WhoisResult

data class DnsToolsUiState(
    val domainInput: String = "example.com",
    val recordType: DnsRecordType = DnsRecordType.A,
    val dnsResult: DnsQueryResult? = null,
    val dmarcResult: DnsQueryResult? = null,
    val whoisResult: WhoisResult? = null,
    val subnetInput: String = "192.168.0.10/24",
    val subnetResult: SubnetResult? = null,
    val pingHost: String = "8.8.8.8",
    val pingResult: PingResult? = null,
    val captivePortalResult: CaptivePortalResult? = null,
    val markdownDocument: MarkdownDocument = MarkdownDocument(name = "notes.txt", content = ""),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
