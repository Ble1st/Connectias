package com.ble1st.connectias.feature.scanner.utils

import android.content.Context

object GmsUtils {
    fun isGmsAvailable(context: Context): Boolean {
        // GMS removed - always return false for non-GMS scanning
        return false
    }
}
