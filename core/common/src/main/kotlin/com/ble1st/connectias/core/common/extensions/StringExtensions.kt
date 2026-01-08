package com.ble1st.connectias.core.common.extensions

/**
 * String utility extensions.
 */

fun String.truncate(maxLength: Int, suffix: String = "..."): String {
    return if (length <= maxLength) this
    else take(maxLength - suffix.length) + suffix
}

fun String.isValidIpAddress(): Boolean {
    val parts = split(".")
    if (parts.size != 4) return false
    return parts.all { part ->
        part.toIntOrNull()?.let { it in 0..255 } ?: false
    }
}

fun String.isValidPort(): Boolean {
    return toIntOrNull()?.let { it in 1..65535 } ?: false
}
