package com.ble1st.connectias.feature.barcode.data

object BarcodeFormatters {
    fun formatWifi(ssid: String, password: String, type: String = "WPA"): String {
        // WIFI:T:WPA;S:mynetwork;P:mypass;;
        val safeSsid = escape(ssid)
        val safePass = escape(password)
        return "WIFI:T:$type;S:$safeSsid;P:$safePass;;"
    }

    fun formatVCard(firstName: String, lastName: String, email: String, phone: String): String {
        return """
            BEGIN:VCARD
            VERSION:3.0
            N:$lastName;$firstName
            FN:$firstName $lastName
            EMAIL:$email
            TEL:$phone
            END:VCARD
        """.trimIndent()
    }
    
    private fun escape(text: String): String {
        return text.replace("\\", "\\\\")
            .replace(",", "\\,")
            .replace(";", "\\;")
            .replace(":", "\\:")
    }
}
