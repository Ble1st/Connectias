package com.ble1st.connectias.feature.dnstools.ui

import org.xbill.DNS.Type

enum class DnsRecordType(val dnsType: Int, val label: String) {
    A(Type.A, "A"),
    AAAA(Type.AAAA, "AAAA"),
    TXT(Type.TXT, "TXT"),
    MX(Type.MX, "MX"),
    CNAME(Type.CNAME, "CNAME"),
    NS(Type.NS, "NS");
}
