package com.ble1st.connectias.feature.utilities.timestamp

import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for timestamp conversion functionality.
 */
@Singleton
class TimestampConverterProvider @Inject constructor() {

    private val commonFormats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", // ISO 8601 with millis
        "yyyy-MM-dd'T'HH:mm:ssXXX",     // ISO 8601
        "yyyy-MM-dd'T'HH:mm:ss'Z'",     // ISO 8601 UTC
        "yyyy-MM-dd HH:mm:ss",           // SQL
        "yyyy-MM-dd",                    // Date only
        "dd/MM/yyyy HH:mm:ss",           // European
        "MM/dd/yyyy HH:mm:ss",           // US
        "dd-MMM-yyyy HH:mm:ss",          // 01-Jan-2024
        "EEE, dd MMM yyyy HH:mm:ss zzz", // RFC 2822
        "yyyyMMdd'T'HHmmss'Z'"           // Compact ISO
    )

    /**
     * Converts Unix timestamp to various formats.
     */
    fun convertTimestamp(
        timestamp: Long,
        isMilliseconds: Boolean = true,
        timezone: String = "UTC"
    ): TimestampResult {
        val millis = if (isMilliseconds) timestamp else timestamp * 1000
        val instant = Instant.ofEpochMilli(millis)
        val zone = ZoneId.of(timezone)
        val zonedDateTime = ZonedDateTime.ofInstant(instant, zone)

        return TimestampResult(
            originalTimestamp = timestamp,
            milliseconds = millis,
            seconds = millis / 1000,
            iso8601 = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(zonedDateTime),
            iso8601Utc = DateTimeFormatter.ISO_INSTANT.format(instant),
            rfc2822 = DateTimeFormatter.RFC_1123_DATE_TIME.format(zonedDateTime),
            human = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy 'at' h:mm:ss a z")
                .format(zonedDateTime),
            date = DateTimeFormatter.ofPattern("yyyy-MM-dd").format(zonedDateTime),
            time = DateTimeFormatter.ofPattern("HH:mm:ss").format(zonedDateTime),
            timezone = timezone,
            dayOfWeek = zonedDateTime.dayOfWeek.name,
            weekOfYear = zonedDateTime.get(java.time.temporal.WeekFields.ISO.weekOfYear()),
            dayOfYear = zonedDateTime.dayOfYear,
            isLeapYear = zonedDateTime.toLocalDate().isLeapYear,
            relativeTime = getRelativeTime(millis)
        )
    }

    /**
     * Parses a date string to timestamp.
     */
    fun parseToTimestamp(
        dateString: String,
        format: String? = null,
        timezone: String = "UTC"
    ): ParseResult {
        // Try specified format first
        if (format != null) {
            try {
                val sdf = SimpleDateFormat(format, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone(timezone)
                val date = sdf.parse(dateString)
                if (date != null) {
                    return ParseResult(
                        success = true,
                        timestamp = date.time,
                        format = format
                    )
                }
            } catch (e: Exception) {
                // Try other formats
            }
        }

        // Try common formats
        for (fmt in commonFormats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone(timezone)
                sdf.isLenient = false
                val date = sdf.parse(dateString)
                if (date != null) {
                    return ParseResult(
                        success = true,
                        timestamp = date.time,
                        format = fmt
                    )
                }
            } catch (e: Exception) {
                // Try next format
            }
        }

        return ParseResult(
            success = false,
            error = "Could not parse date string"
        )
    }

    /**
     * Converts between timezones.
     */
    fun convertTimezone(
        timestamp: Long,
        fromTimezone: String,
        toTimezone: String
    ): TimezoneConversion {
        val instant = Instant.ofEpochMilli(timestamp)
        val fromZone = ZoneId.of(fromTimezone)
        val toZone = ZoneId.of(toTimezone)

        val fromTime = ZonedDateTime.ofInstant(instant, fromZone)
        val toTime = fromTime.withZoneSameInstant(toZone)

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")

        return TimezoneConversion(
            timestamp = timestamp,
            fromTimezone = fromTimezone,
            fromTime = formatter.format(fromTime),
            toTimezone = toTimezone,
            toTime = formatter.format(toTime),
            offsetDifference = (toZone.rules.getOffset(instant).totalSeconds -
                    fromZone.rules.getOffset(instant).totalSeconds) / 3600
        )
    }

    /**
     * Gets current time in different formats.
     */
    fun getCurrentTime(): TimestampResult {
        return convertTimestamp(System.currentTimeMillis())
    }

    /**
     * Calculates difference between two timestamps.
     */
    fun calculateDifference(
        timestamp1: Long,
        timestamp2: Long
    ): TimeDifference {
        val diff = kotlin.math.abs(timestamp2 - timestamp1)

        val days = diff / (24 * 60 * 60 * 1000)
        val hours = (diff % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000)
        val minutes = (diff % (60 * 60 * 1000)) / (60 * 1000)
        val seconds = (diff % (60 * 1000)) / 1000
        val milliseconds = diff % 1000

        return TimeDifference(
            milliseconds = diff,
            seconds = diff / 1000,
            minutes = diff / (60 * 1000),
            hours = diff / (60 * 60 * 1000),
            days = days,
            formatted = "${days}d ${hours}h ${minutes}m ${seconds}s ${milliseconds}ms",
            humanReadable = formatDuration(diff)
        )
    }

    /**
     * Adds duration to timestamp.
     */
    fun addDuration(
        timestamp: Long,
        days: Long = 0,
        hours: Long = 0,
        minutes: Long = 0,
        seconds: Long = 0
    ): Long {
        return timestamp +
                days * 24 * 60 * 60 * 1000 +
                hours * 60 * 60 * 1000 +
                minutes * 60 * 1000 +
                seconds * 1000
    }

    /**
     * Gets list of common timezones.
     */
    fun getCommonTimezones(): List<TimezoneInfo> {
        val commonIds = listOf(
            "UTC", "America/New_York", "America/Chicago", "America/Denver",
            "America/Los_Angeles", "Europe/London", "Europe/Paris", "Europe/Berlin",
            "Asia/Tokyo", "Asia/Shanghai", "Asia/Kolkata", "Australia/Sydney"
        )

        return commonIds.map { id ->
            val zone = TimeZone.getTimeZone(id)
            val offset = zone.rawOffset / (60 * 60 * 1000)
            TimezoneInfo(
                id = id,
                displayName = zone.displayName,
                offset = if (offset >= 0) "+$offset" else "$offset",
                currentTime = SimpleDateFormat("HH:mm:ss", Locale.US).apply {
                    timeZone = zone
                }.format(Date())
            )
        }
    }

    private fun getRelativeTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < 0 -> "in the future"
            diff < 60_000 -> "just now"
            diff < 3600_000 -> "${diff / 60_000} minutes ago"
            diff < 86400_000 -> "${diff / 3600_000} hours ago"
            diff < 604800_000 -> "${diff / 86400_000} days ago"
            diff < 2592000_000 -> "${diff / 604800_000} weeks ago"
            diff < 31536000_000 -> "${diff / 2592000_000} months ago"
            else -> "${diff / 31536000_000} years ago"
        }
    }

    private fun formatDuration(millis: Long): String {
        return when {
            millis < 1000 -> "${millis}ms"
            millis < 60_000 -> "${millis / 1000} seconds"
            millis < 3600_000 -> "${millis / 60_000} minutes"
            millis < 86400_000 -> "${millis / 3600_000} hours"
            else -> "${millis / 86400_000} days"
        }
    }
}

/**
 * Result of timestamp conversion.
 */
@Serializable
data class TimestampResult(
    val originalTimestamp: Long,
    val milliseconds: Long,
    val seconds: Long,
    val iso8601: String,
    val iso8601Utc: String,
    val rfc2822: String,
    val human: String,
    val date: String,
    val time: String,
    val timezone: String,
    val dayOfWeek: String,
    val weekOfYear: Int,
    val dayOfYear: Int,
    val isLeapYear: Boolean,
    val relativeTime: String
)

/**
 * Result of date string parsing.
 */
@Serializable
data class ParseResult(
    val success: Boolean,
    val timestamp: Long = 0,
    val format: String? = null,
    val error: String? = null
)

/**
 * Result of timezone conversion.
 */
@Serializable
data class TimezoneConversion(
    val timestamp: Long,
    val fromTimezone: String,
    val fromTime: String,
    val toTimezone: String,
    val toTime: String,
    val offsetDifference: Int
)

/**
 * Time difference calculation result.
 */
@Serializable
data class TimeDifference(
    val milliseconds: Long,
    val seconds: Long,
    val minutes: Long,
    val hours: Long,
    val days: Long,
    val formatted: String,
    val humanReadable: String
)

/**
 * Timezone information.
 */
@Serializable
data class TimezoneInfo(
    val id: String,
    val displayName: String,
    val offset: String,
    val currentTime: String
)
