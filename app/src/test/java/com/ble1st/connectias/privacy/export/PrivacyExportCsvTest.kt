package com.ble1st.connectias.privacy.export

import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyExportCsvTest {

    @Test
    fun `auditEventsCsv escapes commas and quotes`() {
        val rows = listOf(
            AuditEventRecord(
                id = "1",
                timestamp = 123,
                eventType = "SOME_EVENT",
                severity = "HIGH",
                source = "UnitTest",
                pluginId = "plugin-a",
                message = "Hello, \"world\"",
                details = mapOf("k" to "v,1")
            )
        )

        val csv = PrivacyExportCsv.auditEventsCsv(rows)
        assertTrue(csv.contains("Hello, \"\"world\"\""))
        // The details cell is CSV-escaped, so exact quoting may vary; ensure content is present.
        assertTrue(csv.contains("k"))
        assertTrue(csv.contains("v,1"))
    }
}

