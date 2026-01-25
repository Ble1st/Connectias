package com.ble1st.connectias.analytics.export

object AnalyticsExportCsv {
    fun pluginStatsCsv(rows: List<AnalyticsPluginStat>): String {
        val header = listOf(
            "pluginId",
            "samples",
            "avgCpu",
            "peakCpu",
            "avgMemMB",
            "peakMemMB",
            "netInBytes",
            "netOutBytes",
            "uiActions",
            "rateLimitHits"
        )

        val sb = StringBuilder()
        sb.appendLine(header.joinToString(","))
        rows.forEach { r ->
            sb.appendLine(
                listOf(
                    r.pluginId,
                    r.samples.toString(),
                    r.avgCpu.toString(),
                    r.peakCpu.toString(),
                    r.avgMemMB.toString(),
                    r.peakMemMB.toString(),
                    r.netInBytes.toString(),
                    r.netOutBytes.toString(),
                    r.uiActions.toString(),
                    r.rateLimitHits.toString()
                ).joinToString(",") { escapeCsv(it) }
            )
        }
        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        if (!needsQuotes) return value
        return buildString {
            append('"')
            for (c in value) {
                if (c == '"') append("\"\"") else append(c)
            }
            append('"')
        }
    }
}

