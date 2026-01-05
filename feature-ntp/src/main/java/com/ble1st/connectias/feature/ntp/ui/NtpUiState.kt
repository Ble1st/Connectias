package com.ble1st.connectias.feature.ntp.ui

import com.ble1st.connectias.feature.ntp.data.NtpHistoryEntity
import com.ble1st.connectias.feature.ntp.data.NtpResult

data class NtpUiState(
    val server: String = "time.google.com",
    val result: NtpResult? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val history: List<NtpHistoryEntity> = emptyList()
)
