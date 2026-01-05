package com.ble1st.connectias.feature.ntp.data

data class NtpResult(
    val server: String,
    val offsetMs: Long,
    val delayMs: Long,
    val stratum: Int = 0,
    val referenceId: String = "",
    val error: String? = null
)
