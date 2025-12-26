package com.ble1st.connectias.feature.dvd.settings

import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Settings for DVD features.
 * 
 * CSS decryption is always enabled - libdvdcss is mandatory for DVD playback.
 */
@Singleton
class DvdSettings @Inject constructor(
) {

    init {
        Timber.d("DvdSettings initialized - CSS decryption always enabled")
    }

}
