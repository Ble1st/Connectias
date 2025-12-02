package com.ble1st.connectias.feature.utilities.metadata

import android.content.Context
import android.media.ExifInterface
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider for metadata cleaning functionality.
 */
@Singleton
class MetadataCleanerProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val sensitiveExifTags = listOf(
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_SOFTWARE,
        ExifInterface.TAG_ARTIST,
        ExifInterface.TAG_COPYRIGHT,
        ExifInterface.TAG_USER_COMMENT,
        ExifInterface.TAG_IMAGE_UNIQUE_ID
    )

    /**
     * Extracts metadata from an image.
     */
    suspend fun extractMetadata(uri: Uri): ImageMetadata? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                extractMetadataFromExif(exif)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error extracting metadata from $uri")
            null
        }
    }

    /**
     * Extracts metadata from a file.
     */
    suspend fun extractMetadata(file: File): ImageMetadata? = withContext(Dispatchers.IO) {
        try {
            val exif = ExifInterface(file)
            extractMetadataFromExif(exif)
        } catch (e: Exception) {
            Timber.e(e, "Error extracting metadata from ${file.path}")
            null
        }
    }

    private fun extractMetadataFromExif(exif: ExifInterface): ImageMetadata {
        val latLong = FloatArray(2)
        val hasGps = exif.getLatLong(latLong)

        return ImageMetadata(
            make = exif.getAttribute(ExifInterface.TAG_MAKE),
            model = exif.getAttribute(ExifInterface.TAG_MODEL),
            software = exif.getAttribute(ExifInterface.TAG_SOFTWARE),
            dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME),
            dateTimeOriginal = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL),
            latitude = if (hasGps) latLong[0].toDouble() else null,
            longitude = if (hasGps) latLong[1].toDouble() else null,
            altitude = exif.getAltitude(Double.NaN).takeIf { !it.isNaN() },
            orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1),
            imageWidth = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0).takeIf { it > 0 },
            imageHeight = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0).takeIf { it > 0 },
            artist = exif.getAttribute(ExifInterface.TAG_ARTIST),
            copyright = exif.getAttribute(ExifInterface.TAG_COPYRIGHT),
            userComment = exif.getAttribute(ExifInterface.TAG_USER_COMMENT),
            fNumber = exif.getAttribute(ExifInterface.TAG_F_NUMBER),
            exposureTime = exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME),
            isoSpeed = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS),
            focalLength = exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)
        )
    }

    /**
     * Cleans metadata from an image.
     */
    suspend fun cleanMetadata(
        sourceUri: Uri,
        outputFile: File,
        options: CleaningOptions = CleaningOptions()
    ): CleaningResult = withContext(Dispatchers.IO) {
        try {
            // Copy file first
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(outputFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Remove EXIF data
            val exif = ExifInterface(outputFile)
            var removedCount = 0

            if (options.removeGps) {
                for (tag in sensitiveExifTags.filter { it.contains("GPS") }) {
                    if (exif.getAttribute(tag) != null) {
                        exif.setAttribute(tag, null)
                        removedCount++
                    }
                }
            }

            if (options.removeDateTime) {
                for (tag in listOf(
                    ExifInterface.TAG_DATETIME,
                    ExifInterface.TAG_DATETIME_ORIGINAL,
                    ExifInterface.TAG_DATETIME_DIGITIZED
                )) {
                    if (exif.getAttribute(tag) != null) {
                        exif.setAttribute(tag, null)
                        removedCount++
                    }
                }
            }

            if (options.removeDeviceInfo) {
                for (tag in listOf(
                    ExifInterface.TAG_MAKE,
                    ExifInterface.TAG_MODEL,
                    ExifInterface.TAG_SOFTWARE
                )) {
                    if (exif.getAttribute(tag) != null) {
                        exif.setAttribute(tag, null)
                        removedCount++
                    }
                }
            }

            if (options.removeAll) {
                for (tag in sensitiveExifTags) {
                    if (exif.getAttribute(tag) != null) {
                        exif.setAttribute(tag, null)
                        removedCount++
                    }
                }
            }

            exif.saveAttributes()

            CleaningResult(
                success = true,
                outputPath = outputFile.absolutePath,
                removedFieldsCount = removedCount,
                originalSize = context.contentResolver.openInputStream(sourceUri)?.available()?.toLong() ?: 0,
                cleanedSize = outputFile.length()
            )
        } catch (e: Exception) {
            Timber.e(e, "Error cleaning metadata")
            CleaningResult(
                success = false,
                error = e.message
            )
        }
    }

    /**
     * Batch cleans multiple files.
     */
    suspend fun batchClean(
        files: List<Uri>,
        outputDir: File,
        options: CleaningOptions = CleaningOptions()
    ): List<CleaningResult> = withContext(Dispatchers.IO) {
        files.mapIndexed { index, uri ->
            val outputFile = File(outputDir, "cleaned_${index}_${System.currentTimeMillis()}.jpg")
            cleanMetadata(uri, outputFile, options)
        }
    }

    /**
     * Checks if a file has sensitive metadata.
     */
    suspend fun hasSensitiveMetadata(uri: Uri): SensitivityReport = withContext(Dispatchers.IO) {
        val metadata = extractMetadata(uri)
        val issues = mutableListOf<String>()

        metadata?.let { m ->
            if (m.latitude != null || m.longitude != null) {
                issues.add("Contains GPS location data")
            }
            if (m.make != null || m.model != null) {
                issues.add("Contains device information (${m.make} ${m.model})")
            }
            if (m.dateTime != null || m.dateTimeOriginal != null) {
                issues.add("Contains date/time information")
            }
            if (m.artist != null || m.copyright != null) {
                issues.add("Contains author/copyright information")
            }
        }

        SensitivityReport(
            hasSensitiveData = issues.isNotEmpty(),
            issues = issues,
            metadata = metadata
        )
    }
}

/**
 * Extracted image metadata.
 */
@Serializable
data class ImageMetadata(
    val make: String? = null,
    val model: String? = null,
    val software: String? = null,
    val dateTime: String? = null,
    val dateTimeOriginal: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val orientation: Int? = null,
    val imageWidth: Int? = null,
    val imageHeight: Int? = null,
    val artist: String? = null,
    val copyright: String? = null,
    val userComment: String? = null,
    val fNumber: String? = null,
    val exposureTime: String? = null,
    val isoSpeed: String? = null,
    val focalLength: String? = null
) {
    val hasGpsData: Boolean
        get() = latitude != null || longitude != null
}

/**
 * Options for metadata cleaning.
 */
@Serializable
data class CleaningOptions(
    val removeGps: Boolean = true,
    val removeDateTime: Boolean = true,
    val removeDeviceInfo: Boolean = true,
    val removeAll: Boolean = false
)

/**
 * Result of metadata cleaning.
 */
@Serializable
data class CleaningResult(
    val success: Boolean,
    val outputPath: String? = null,
    val removedFieldsCount: Int = 0,
    val originalSize: Long = 0,
    val cleanedSize: Long = 0,
    val error: String? = null
)

/**
 * Sensitivity report for a file.
 */
@Serializable
data class SensitivityReport(
    val hasSensitiveData: Boolean,
    val issues: List<String>,
    val metadata: ImageMetadata?
)
