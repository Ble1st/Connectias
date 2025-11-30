package com.ble1st.connectias.feature.usb.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Complete DVD information.
 */
@Parcelize
data class DvdInfo(
    val handle: Long,
    val mountPoint: String,
    val titles: List<DvdTitle>
) : Parcelable
