package com.ble1st.connectias.feature.scanner.utils

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

object GmsUtils {
    fun isGmsAvailable(context: Context): Boolean {
        return try {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        } catch (e: Exception) {
            false
        }
    }
}
