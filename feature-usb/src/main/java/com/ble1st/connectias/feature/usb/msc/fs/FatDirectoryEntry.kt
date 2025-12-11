package com.ble1st.connectias.feature.usb.msc.fs

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

data class FatDirectoryEntry(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val firstCluster: Long,
    val lastModified: Long
)

internal object FatDirParser {
    private const val ATTR_LONG_NAME = 0x0F
    private const val ATTR_DIRECTORY = 0x10

    fun parse(entries: ByteArray): List<FatDirectoryEntry> {
        val result = mutableListOf<FatDirectoryEntry>()
        var idx = 0
        var pendingLfn: MutableList<String>? = null

        while (idx + 32 <= entries.size) {
            val entry = entries.copyOfRange(idx, idx + 32)
            val firstByte = entry[0].toInt() and 0xFF
            if (firstByte == 0x00) break // end of directory
            if (firstByte == 0xE5) { // deleted
                idx += 32
                pendingLfn = null
                continue
            }
            val attr = entry[11].toInt() and 0xFF
            if (attr == ATTR_LONG_NAME) {
                val namePart = parseLfnPart(entry)
                if (pendingLfn == null) pendingLfn = mutableListOf()
                pendingLfn.add(0, namePart)
                idx += 32
                continue
            }
            val name = pendingLfn?.joinToString("")?.trimEnd('\u0000', '\uFFFF', ' ')
                ?: parseShortName(entry)
            pendingLfn = null
            val isDir = (attr and ATTR_DIRECTORY) != 0
            val firstClusterHigh = ((entry[20].toInt() and 0xFF) shl 8) or (entry[19].toInt() and 0xFF)
            val firstClusterLow = ((entry[27].toInt() and 0xFF) shl 8) or (entry[26].toInt() and 0xFF)
            val firstCluster = (firstClusterHigh shl 16 or firstClusterLow).toLong() and 0xFFFFFFFFL
            val size = (entry[28].toLong() and 0xFF) or
                    ((entry[29].toLong() and 0xFF) shl 8) or
                    ((entry[30].toLong() and 0xFF) shl 16) or
                    ((entry[31].toLong() and 0xFF) shl 24)
            val lastModified = parseTimestamp(entry)
            result.add(FatDirectoryEntry(name, isDir, size, firstCluster, lastModified))
            idx += 32
        }
        return result
    }

    private fun parseShortName(entry: ByteArray): String {
        val name = entry.copyOfRange(0, 8).toString(StandardCharsets.US_ASCII).trimEnd()
        val ext = entry.copyOfRange(8, 11).toString(StandardCharsets.US_ASCII).trimEnd()
        return if (ext.isNotEmpty()) "$name.$ext" else name
    }

    private fun parseLfnPart(entry: ByteArray): String {
        val chars = mutableListOf<Char>()
        fun readPair(offset: Int) {
            val lo = entry[offset].toInt() and 0xFF
            val hi = entry[offset + 1].toInt() and 0xFF
            val code = (hi shl 8) or lo
            if (code != 0xFFFF && code != 0x0000) chars.add(code.toChar())
        }
        readPair(1); readPair(3); readPair(5); readPair(7); readPair(9)
        readPair(14); readPair(16); readPair(18); readPair(20); readPair(22); readPair(24)
        readPair(28); readPair(30)
        return chars.joinToString("")
    }

    private fun parseTimestamp(entry: ByteArray): Long {
        // Combine date and time fields (FAT date/time)
        val time = ((entry[23].toInt() and 0xFF) shl 8) or (entry[22].toInt() and 0xFF)
        val date = ((entry[25].toInt() and 0xFF) shl 8) or (entry[24].toInt() and 0xFF)
        val seconds = (time and 0x1F) * 2
        val minutes = (time shr 5) and 0x3F
        val hours = (time shr 11) and 0x1F
        val day = date and 0x1F
        val month = (date shr 5) and 0x0F
        val year = ((date shr 9) and 0x7F) + 1980
        return try {
            java.time.LocalDateTime.of(year, month.coerceAtLeast(1), day.coerceAtLeast(1), hours, minutes, seconds)
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli()
        } catch (e: Exception) {
            0L
        }
    }
}
