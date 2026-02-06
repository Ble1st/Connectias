package com.ble1st.connectias.ui.plugin

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Returns icon, label, and description for a permission
 */
fun getPermissionInfo(permission: String): Triple<ImageVector, String, String> {
    return when {
        permission.contains("INTERNET") -> Triple(
            Icons.Default.Language,
            "Internet",
            "Access the internet"
        )
        permission.contains("CAMERA") -> Triple(
            Icons.Default.CameraAlt,
            "Camera",
            "Take photos and record videos"
        )
        permission.contains("RECORD_AUDIO") -> Triple(
            Icons.Default.Mic,
            "Microphone",
            "Record audio"
        )
        permission.contains("LOCATION") -> Triple(
            Icons.Default.LocationOn,
            "Location",
            "Access device location"
        )
        permission.contains("STORAGE") || permission.contains("READ_MEDIA") -> Triple(
            Icons.Default.Storage,
            "Storage",
            "Read and write files"
        )
        permission.contains("CONTACTS") -> Triple(
            Icons.Default.Contacts,
            "Contacts",
            "Access contacts"
        )
        permission.contains("PHONE") || permission.contains("CALL") -> Triple(
            Icons.Default.Phone,
            "Phone",
            "Make phone calls"
        )
        permission.contains("SMS") -> Triple(
            Icons.Default.Message,
            "SMS",
            "Send and receive SMS"
        )
        permission.contains("CALENDAR") -> Triple(
            Icons.Default.CalendarToday,
            "Calendar",
            "Access calendar events"
        )
        permission.contains("BLUETOOTH") -> Triple(
            Icons.Default.Bluetooth,
            "Bluetooth",
            "Connect to Bluetooth devices"
        )
        permission.contains("BODY_SENSORS") -> Triple(
            Icons.Default.MonitorHeart,
            "Body Sensors",
            "Access body sensor data"
        )
        permission.contains("ACTIVITY_RECOGNITION") -> Triple(
            Icons.Default.DirectionsWalk,
            "Activity Recognition",
            "Recognize physical activity"
        )
        else -> Triple(
            Icons.Default.Security,
            permission.substringAfterLast(".").replace("_", " "),
            ""
        )
    }
}
