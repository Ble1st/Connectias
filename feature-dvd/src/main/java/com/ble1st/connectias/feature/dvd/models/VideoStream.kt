package com.ble1st.connectias.feature.dvd.models

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Video stream information containing metadata about a video stream.
 *
 * This data class represents the technical properties of a video stream,
 * including dimensions, encoding parameters, and playback information.
 *
 * @property codec The video codec identifier (e.g., "h264", "hevc", "mpeg2video").
 *   Must be non-empty. Common values follow codec name conventions.
 *
 * @property width The video width in pixels. Must be a positive integer greater than 0.
 *   Typical values range from 320 (low resolution) to 7680 (8K UHD).
 *   Example: 1920 for Full HD.
 *
 * @property height The video height in pixels. Must be a positive integer greater than 0.
 *   Typical values range from 240 (low resolution) to 4320 (8K UHD).
 *   Example: 1080 for Full HD.
 *
 * @property bitrate The video bitrate in bits per second (bps). Must be positive.
 *   Typical values range from 500,000 (500 kbps) for low quality to 100,000,000 (100 Mbps)
 *   for high quality content. Common values:
 *   - SD: 1-3 Mbps
 *   - HD: 3-8 Mbps
 *   - Full HD: 5-20 Mbps
 *   - 4K: 25-100 Mbps
 *
 * @property frameRate The video frame rate in frames per second (fps). Must be positive.
 *   May be fractional (e.g., 23.976, 29.97, 59.94). Common values:
 *   - 24 fps (cinema)
 *   - 25 fps (PAL)
 *   - 29.97 fps (NTSC)
 *   - 30 fps
 *   - 50 fps (PAL progressive)
 *   - 59.94 fps (NTSC progressive)
 *   - 60 fps
 *
 * @property uri Optional URI to the video stream source. May be null if the stream
 *   is not directly accessible via URI (e.g., embedded streams, network streams).
 *   When present, should be a valid URI string (file://, http://, https://, etc.).
 *
 * @example
 * ```
 * VideoStream(
 *     codec = "h264",
 *     width = 1920,
 *     height = 1080,
 *     bitrate = 8_000_000L, // 8 Mbps
 *     frameRate = 29.97,
 *     uri = "file:///path/to/video.mp4"
 * )
 * ```
 */
@Parcelize
data class VideoStream(
    val codec: String,
    val width: Int,
    val height: Int,
    val bitrate: Long,
    val frameRate: Double,
    val uri: String?, // URI to video stream
    val audioStreamId: Int? = null,
    val subtitleStreamId: Int? = null,
    val audioLanguage: String? = null,
    val subtitleLanguage: String? = null
) : Parcelable
