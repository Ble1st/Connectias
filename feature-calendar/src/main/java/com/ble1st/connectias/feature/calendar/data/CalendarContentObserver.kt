package com.ble1st.connectias.feature.calendar.data

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import timber.log.Timber

class CalendarContentObserver(
    private val onCalendarChanged: () -> Unit
) : ContentObserver(Handler(Looper.getMainLooper())) {

    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)
        Timber.d("Calendar content changed, selfChange: $selfChange")
        onCalendarChanged()
    }

    override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
        super.onChange(selfChange, uri)
        Timber.d("Calendar content changed, selfChange: $selfChange, uri: $uri")
        onCalendarChanged()
    }
}

