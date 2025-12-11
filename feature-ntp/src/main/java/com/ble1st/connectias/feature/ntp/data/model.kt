package com.ble1st.connectias.feature.ntp.data

data class NtpResult(
    val server: String,
    val offsetMs: Long,
    val delayMs: Long,
    val error: String? = null
)
