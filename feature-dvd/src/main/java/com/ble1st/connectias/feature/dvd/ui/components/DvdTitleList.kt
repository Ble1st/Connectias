package com.ble1st.connectias.feature.dvd.ui.components

import timber.log.Timber

private fun formatDuration(millis: Long): String {
    if (millis < 0) {
        Timber.w("Negative duration encountered: $millis ms. Returning '0:00'.")
        return "0:00"
    }
    val seconds = millis / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes % 60, seconds % 60)
    } else {
        "%d:%02d".format(minutes, seconds % 60)
    }
}
