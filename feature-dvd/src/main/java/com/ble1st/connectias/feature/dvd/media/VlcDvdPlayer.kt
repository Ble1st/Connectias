package com.ble1st.connectias.feature.dvd.media

import android.content.Context
import android.view.SurfaceView
import android.os.Handler
import android.os.Looper
import com.ble1st.connectias.feature.dvd.models.UsbDevice
import com.ble1st.connectias.feature.dvd.driver.UsbBlockDevice
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import timber.log.Timber
import java.util.ArrayList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import org.videolan.libvlc.MediaPlayer.TrackDescription
import androidx.core.net.toUri

/**
 * A DVD Player implementation using LibVLC for Android.
 * Supports full DVD navigation (menus, chapters, audio tracks).
 * 
 * Features:
 * - Direct USB access via Custom Native Callbacks
 * - DVD Menu Navigation
 * - Touch Event Handling
 */
class VlcDvdPlayer(private val context: Context) {

    private var libVlc: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    
    // Active block device for current playback
    // @Volatile ensures visibility across threads (ioOpen() is called from native thread)
    @Volatile
    private var currentBlockDevice: UsbBlockDevice? = null
    private var deviceSize: Long = 0
    private var currentOffset: Long = 0
    
    // Buffer for handling unaligned reads
    private var readBuffer: ByteArray? = null
    private var readBufferOffset: Long = -1
    private var readBufferSize: Int = 0
    
    // VOB data management
    private var dvdHandle: Long = 0
    private var vobOffsets: List<com.ble1st.connectias.feature.dvd.native.VobOffsetInfo>? = null
    private var currentVobFile: Long = 0
    private var vtsN: Int = 0
    private var totalVobSize: Long = 0  // Total size in bytes
    private val trackHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var tracksApplied = false
    @Volatile
    private var lastAudioStreamId: Int? = null
    @Volatile
    private var lastSubtitleStreamId: Int? = null
    @Volatile
    private var lastAudioLanguage: String? = null
    @Volatile
    private var lastSubtitleLanguage: String? = null

    // Native Library Loading
    companion object {
        init {
            try {
                System.loadLibrary("dvd_jni")
            } catch (e: UnsatisfiedLinkError) {
                Timber.e(e, "Failed to load dvd_jni library")
            }
        }
    }

    // Native methods implemented in vlc_jni.cpp
    private external fun nativeInit(): Boolean
    private external fun nativeCreateMedia(libVlcInstance: Long): Long
    private external fun nativeSetMediaOnPlayer(mediaPlayerHandle: Long, mediaHandle: Long): Boolean
    
    init {
        Timber.d("VlcDvdPlayer: Initializing VlcDvdPlayer instance")
        setupLibVlc()
        Timber.d("VlcDvdPlayer: Initialization complete")
    }

    private fun setupLibVlc() {
        Timber.d("VlcDvdPlayer: setupLibVlc() called")
        try {
            Timber.d("VlcDvdPlayer: Starting native bridge initialization")
            // Initialize native bridge (dlopen libvlc.so)
            val nativeInitResult = nativeInit()
            Timber.d("VlcDvdPlayer: nativeInit() returned: $nativeInitResult")
            if (!nativeInitResult) {
                Timber.e("VlcDvdPlayer: Failed to initialize native LibVLC bridge")
            } else {
                Timber.d("VlcDvdPlayer: Native bridge initialized successfully")
            }

            Timber.d("VlcDvdPlayer: Creating LibVLC options list")
            val options = ArrayList<String>()
            // Decoding options
            options.add("--no-drop-late-frames")
            Timber.d("VlcDvdPlayer: Added option: --no-drop-late-frames")
            options.add("--no-skip-frames")
            Timber.d("VlcDvdPlayer: Added option: --no-skip-frames")
            options.add("--rtsp-tcp")
            Timber.d("VlcDvdPlayer: Added option: --rtsp-tcp")
            
            // Verbosity for debugging
            options.add("-vvv")
            Timber.d("VlcDvdPlayer: Added option: -vvv (verbose logging)")
            
            Timber.d("VlcDvdPlayer: Creating LibVLC instance with ${options.size} options")
            libVlc = LibVLC(context, options)
            Timber.d("VlcDvdPlayer: LibVLC instance created: ${libVlc != null}")
            
            Timber.d("VlcDvdPlayer: Creating MediaPlayer instance")
            mediaPlayer = MediaPlayer(libVlc)
            Timber.d("VlcDvdPlayer: MediaPlayer instance created: ${mediaPlayer != null}")
            
            Timber.d("VlcDvdPlayer: Setting up MediaPlayer event listener")
            mediaPlayer?.setEventListener { event ->
                Timber.d("VlcDvdPlayer: Event received - type: ${event.type}, buffering: ${event.buffering}%")
                when (event.type) {
                    MediaPlayer.Event.EndReached -> {
                        Timber.d("VlcDvdPlayer: VLC Event - End reached")
                    }
                    MediaPlayer.Event.EncounteredError -> {
                        Timber.e("VlcDvdPlayer: VLC Event - Error encountered - type: ${event.type}, buffering: ${event.buffering}%")
                        // Log player state for debugging
                        try {
                            val state = mediaPlayer?.playerState
                            Timber.e("VlcDvdPlayer: Player state during error: $state")
                            Timber.e("VlcDvdPlayer: MediaPlayer instance: $mediaPlayer")
                            Timber.e("VlcDvdPlayer: LibVLC instance: $libVlc")
                        } catch (e: Exception) {
                            Timber.e(e, "VlcDvdPlayer: Could not retrieve player state: ${e.message}")
                        }
                    }
                    MediaPlayer.Event.Buffering -> {
                        Timber.d("VlcDvdPlayer: VLC Event - Buffering ${event.buffering}%")
                    }
                    MediaPlayer.Event.Playing -> {
                        Timber.d("VlcDvdPlayer: VLC Event - Playing")
                        scheduleTrackApply()
                    }
                    MediaPlayer.Event.Opening -> {
                        Timber.d("VlcDvdPlayer: VLC Event - Opening media")
                    }
                    MediaPlayer.Event.Paused -> {
                        Timber.d("VlcDvdPlayer: VLC Event - Paused")
                    }
                    MediaPlayer.Event.Stopped -> {
                        Timber.d("VlcDvdPlayer: VLC Event - Stopped")
                    }
                    MediaPlayer.Event.TimeChanged -> {
                        Timber.v("VlcDvdPlayer: VLC Event - Time changed: ${event.timeChanged}ms")
                    }
                    MediaPlayer.Event.PositionChanged -> {
                        Timber.v("VlcDvdPlayer: VLC Event - Position changed: ${event.positionChanged}")
                    }
                    MediaPlayer.Event.LengthChanged -> {
                        Timber.d("VlcDvdPlayer: VLC Event - Length changed: ${event.lengthChanged}ms")
                        scheduleTrackApply()
                    }
                    MediaPlayer.Event.Vout -> {
                        Timber.d("VlcDvdPlayer: VLC Event - Video output event")
                    }
                    MediaPlayer.Event.ESAdded -> {
                        Timber.d("VlcDvdPlayer: VLC Event - Elementary stream added")
                        scheduleTrackApply()
                    }
                    MediaPlayer.Event.ESDeleted -> {
                        Timber.d("VlcDvdPlayer: VLC Event - Elementary stream deleted")
                    }
                    MediaPlayer.Event.ESSelected -> {
                        Timber.d("VlcDvdPlayer: VLC Event - Elementary stream selected")
                        scheduleTrackApply()
                    }
                    else -> {
                        Timber.d("VlcDvdPlayer: VLC Event - Unknown event type: ${event.type}")
                    }
                }
            }
            Timber.d("VlcDvdPlayer: Event listener setup complete")
        } catch (e: UnsatisfiedLinkError) {
            Timber.e(e, "Failed to load LibVLC native library")
        } catch (e: IllegalStateException) {
            Timber.e(e, "LibVLC initialization failed - invalid state")
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error initializing LibVLC")
        }
    }

    /**
     * Extracts title number from URI.
     * Expected format: content://com.ble1st.connectias.provider/dvd/{device_id}/{title_number}
     * 
     * @param uri The URI string
     * @return Title number, or null if not found or invalid
     */
    private fun extractTitleNumberFromUri(uri: String): Int? {
        try {
            val androidUri = uri.toUri()
            val segments = androidUri.pathSegments
            if (segments.size >= 3 && segments[0] == "dvd") {
                val titleNumber = segments[2].toIntOrNull()
                if (titleNumber != null && titleNumber > 0) {
                    Timber.d("VlcDvdPlayer: extractTitleNumberFromUri() - Found title number: $titleNumber")
                    return titleNumber
                }
            }
            Timber.w("VlcDvdPlayer: extractTitleNumberFromUri() - Could not extract title number from URI: $uri")
            return null
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "VlcDvdPlayer: extractTitleNumberFromUri() - Invalid URI format: $uri")
            return null
        } catch (e: NumberFormatException) {
            Timber.e(e, "VlcDvdPlayer: extractTitleNumberFromUri() - Invalid title number in URI: $uri")
            return null
        } catch (e: Exception) {
            Timber.e(e, "VlcDvdPlayer: extractTitleNumberFromUri() - Unexpected error parsing URI: $uri")
            return null
        }
    }
    
    /**
     * Plays a DVD from a USB Device.
     * 
     * Tries to use the high-performance Native Stream if possible,
     * falling back to generic methods if needed.
     * 
     * Note: This method should be called from a background thread to avoid blocking the UI.
     * The final LibVLC operations (player.media, player.play) will be executed on the main thread.
     * 
     * @param titleNumber Optional title number. If null, will be extracted from devicePath URI.
     */
    suspend fun playDvd(
        usbDevice: UsbDevice,
        devicePath: String,
        driver: UsbBlockDevice?,
        titleNumber: Int? = null,
        audioStreamId: Int? = null,
        subtitleStreamId: Int? = null,
        audioLanguage: String? = null,
        subtitleLanguage: String? = null
    ) {
        Timber.d("VlcDvdPlayer: playDvd() called")
        Timber.d("VlcDvdPlayer: usbDevice: ${usbDevice.product}, devicePath: $devicePath, driver: ${driver != null}")
        Timber.d("VlcDvdPlayer: titleNumber param: $titleNumber, audioStreamId: $audioStreamId, subtitleStreamId: $subtitleStreamId")
        
        trackHandler.removeCallbacksAndMessages(null)
        tracksApplied = false
        lastAudioStreamId = audioStreamId
        lastSubtitleStreamId = subtitleStreamId
        lastAudioLanguage = audioLanguage
        lastSubtitleLanguage = subtitleLanguage
        // Extract title number from URI if not provided; if none found, default to 1 (direct playback, no menu)
        val actualTitleNumber = titleNumber ?: extractTitleNumberFromUri(devicePath) ?: 1
        Timber.d("VlcDvdPlayer: playDvd() - Using title number: $actualTitleNumber")
        
        val vlc = libVlc
        if (vlc == null) {
            Timber.e("VlcDvdPlayer: libVlc is null, cannot play DVD")
            return
        }
        Timber.d("VlcDvdPlayer: libVlc instance available: $vlc")
        
        val player = mediaPlayer
        if (player == null) {
            Timber.e("VlcDvdPlayer: mediaPlayer is null, cannot play DVD")
            return
        }
        Timber.d("VlcDvdPlayer: mediaPlayer instance available: $player")

        Timber.d("VlcDvdPlayer: Stopping any previous playback")
        stop() // Stop any previous playback
        Timber.d("VlcDvdPlayer: Previous playback stopped")

        var media: Media? = null
        // Declare nativeMediaHandle at function scope so it's accessible throughout
        var nativeMediaHandle = 0L

        // Strategy 1: Custom Native Callbacks (Preferred for USB Mass Storage)
        // Do heavy reflection/native work on background thread
        Timber.d("VlcDvdPlayer: Strategy 1 - Checking for Custom Native Callbacks")
        if (driver != null) {
            Timber.d("VlcDvdPlayer: Driver available, attempting Custom Native Input setup")
            try {
                Timber.i("VlcDvdPlayer: Attempting to setup Custom Native Input for USB Block Device")
                Timber.d("VlcDvdPlayer: Setting currentBlockDevice to driver")
                // Verify driver is valid before setting
                try {
                    val testBlockSize = driver.blockSize
                    Timber.d("VlcDvdPlayer: Driver verified, blockSize: $testBlockSize")
                } catch (e: Exception) {
                    Timber.e(e, "VlcDvdPlayer: Driver is invalid or closed, cannot use custom input")
                    throw e
                }
                currentBlockDevice = driver
                Timber.i("VlcDvdPlayer: currentBlockDevice set: ${currentBlockDevice != null}")
                // @Volatile ensures memory visibility across threads
                
                // Load VOB offsets if title number is available
                val titleNum = actualTitleNumber ?: 1
                Timber.d("VlcDvdPlayer: Loading VOB offsets for title: $titleNum")
                withContext(Dispatchers.IO) {
                    try {
                        // Open DVD handle if not already open
                        if (dvdHandle == 0L) {
                            Timber.d("VlcDvdPlayer: [IO Thread] Opening DVD handle")
                            // Try to open via stream (preferred for USB devices)
                            dvdHandle = com.ble1st.connectias.feature.dvd.native.DvdNative.dvdOpenStream(driver)
                            if (dvdHandle <= 0) {
                                Timber.w("VlcDvdPlayer: [IO Thread] Failed to open DVD via stream, trying path")
                                // Fallback: try to open via path (if devicePath is a file path)
                                dvdHandle = com.ble1st.connectias.feature.dvd.native.DvdNative.dvdOpen(devicePath)
                            }
                            if (dvdHandle <= 0) {
                                Timber.e("VlcDvdPlayer: [IO Thread] Failed to open DVD handle")
                                return@withContext
                            }
                            Timber.d("VlcDvdPlayer: [IO Thread] DVD handle opened: $dvdHandle")
                        }
                        
                        // Get VOB offsets and VTS number
                        val vobData = com.ble1st.connectias.feature.dvd.native.DvdNative.dvdGetVobOffsets(dvdHandle, titleNum)
                        if (vobData != null && vobData.second.isNotEmpty()) {
                            vtsN = vobData.first
                            vobOffsets = vobData.second
                            Timber.i("VlcDvdPlayer: [IO Thread] Loaded ${vobData.second.size} VOB cell offsets for VTS: $vtsN")
                            
                            // Calculate total VOB size
                            totalVobSize = vobData.second.sumOf { (it.lastSector - it.firstSector + 1) * 2048L }
                            Timber.d("VlcDvdPlayer: [IO Thread] Total VOB size: $totalVobSize bytes (${totalVobSize / 1024 / 1024} MB)")
                            
                            // Open VOB file
                            currentVobFile = com.ble1st.connectias.feature.dvd.native.DvdNative.dvdOpenVobFile(dvdHandle, vtsN)
                            if (currentVobFile > 0) {
                                Timber.i("VlcDvdPlayer: [IO Thread] VOB file opened, handle: $currentVobFile")
                            } else {
                                Timber.e("VlcDvdPlayer: [IO Thread] Failed to open VOB file")
                                vobOffsets = null
                            }
                        } else {
                            Timber.w("VlcDvdPlayer: [IO Thread] Failed to load VOB offsets, will use raw block data")
                            vobOffsets = null
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "VlcDvdPlayer: [IO Thread] Error loading VOB offsets")
                        vobOffsets = null
                    }
                }
                
                // Perform reflection and native calls on background thread
                Timber.d("VlcDvdPlayer: Switching to IO dispatcher for reflection work")
                val instanceHandle = withContext(Dispatchers.IO) {
                    Timber.d("VlcDvdPlayer: [IO Thread] Getting LibVLC handle via reflection")
                    val handle = getLibVlcHandle(vlc)
                    Timber.d("VlcDvdPlayer: [IO Thread] getLibVlcHandle returned: $handle")
                    handle
                }
                
                Timber.d("VlcDvdPlayer: Instance handle check: $instanceHandle")
                if (instanceHandle != 0L) {
                    Timber.d("VlcDvdPlayer: Instance handle is valid, proceeding with nativeCreateMedia")
                    Timber.d("VlcDvdPlayer: Calling nativeCreateMedia with handle: $instanceHandle")
                    // Create Native Media (via JNI) - this can be slow, add timeout
                    nativeMediaHandle = withContext(Dispatchers.IO) {
                        Timber.d("VlcDvdPlayer: [IO Thread] Starting nativeCreateMedia with 5 second timeout")
                        try {
                            withTimeout(5000) { // 5 second timeout
                                Timber.d("VlcDvdPlayer: [IO Thread] Calling nativeCreateMedia()")
                                val handle = nativeCreateMedia(instanceHandle)
                                Timber.d("VlcDvdPlayer: [IO Thread] nativeCreateMedia returned: $handle")
                                handle
                            }
                        } catch (e: TimeoutCancellationException) {
                            Timber.e("VlcDvdPlayer: [IO Thread] nativeCreateMedia timed out after 5 seconds")
                            0L
                        } catch (e: Exception) {
                            Timber.e(e, "VlcDvdPlayer: [IO Thread] nativeCreateMedia threw exception")
                            0L
                        }
                    }
                    
                    Timber.i("VlcDvdPlayer: Native media handle: $nativeMediaHandle")
                    if (nativeMediaHandle != 0L) {
                        Timber.i("VlcDvdPlayer: Native media handle is valid, setting directly on player")
                        // Instead of creating Java Media wrapper, set native media directly on player
                        val playerHandle = getMediaPlayerHandle(player)
                        if (playerHandle != 0L) {
                            Timber.i("VlcDvdPlayer: MediaPlayer handle: $playerHandle")
                            val success = nativeSetMediaOnPlayer(playerHandle, nativeMediaHandle)
                            if (success) {
                                Timber.i("VlcDvdPlayer: Native media set on player successfully")
                                // Create a dummy Media object for compatibility with rest of code
                                // We won't use it, but it prevents null checks
                                media = null // Will be handled specially in final setup
                            } else {
                                Timber.e("VlcDvdPlayer: Failed to set native media on player")
                            }
                        } else {
                            Timber.e("VlcDvdPlayer: Could not get MediaPlayer handle")
                        }
                    } else {
                        Timber.e("VlcDvdPlayer: nativeCreateMedia returned 0 - Custom native input FAILED")
                        Timber.e("VlcDvdPlayer: Check native logs (VlcJni) for libvlc_media_new_callbacks errors")
                        Timber.e("VlcDvdPlayer: Falling back to standard MRL (may not work for USB devices)")
                    }
                } else {
                    Timber.w("VlcDvdPlayer: Instance handle is 0 - falling back to standard MRL")
                }
            } catch (e: Exception) {
                Timber.w(e, "VlcDvdPlayer: Failed to initialize native custom input. Falling back.")
                // Continue to fallback
            }
        } else {
            Timber.w("VlcDvdPlayer: No driver provided - using fallback MRL")
        }
        
        // Strategy 2: Fallback to standard MRL (only if devicePath is a valid file path)
        Timber.d("VlcDvdPlayer: Strategy 2 - Checking for MRL fallback")
        if (media == null) {
            Timber.d("VlcDvdPlayer: Media is null, attempting fallback")
            // Only try fallback if devicePath looks like a file path (not a content:// URI)
            if (devicePath.startsWith("/") && !devicePath.startsWith("content://")) {
                Timber.w("VlcDvdPlayer: Fallback to standard dvd:// MRL for path: $devicePath")
                try {
                    // Media creation should be fast, but do it on background thread to be safe
                    media = withContext(Dispatchers.IO) {
                        Timber.d("VlcDvdPlayer: [IO Thread] Creating Media with URI: dvd://$devicePath")
                        val uri = "dvd://$devicePath".toUri()
                        Timber.d("VlcDvdPlayer: [IO Thread] Parsed URI: $uri")
                        val m = Media(vlc, uri)
                        Timber.d("VlcDvdPlayer: [IO Thread] Media created successfully: ${m != null}")
                        if (m != null) {
                            Timber.d("VlcDvdPlayer: [IO Thread] Setting DVD demuxer options for MRL")
                            // Explicitly set DVD demuxer options for MRL fallback
                            try {
                                Timber.d("VlcDvdPlayer: [IO Thread] Adding option: :demux=dvdnav")
                                m.addOption(":demux=dvdnav")
                                Timber.d("VlcDvdPlayer: [IO Thread] Set demux=dvdnav option for MRL successfully")
                            } catch (e: Exception) {
                                Timber.w(e, "VlcDvdPlayer: [IO Thread] Failed to set demux option for MRL")
                            }
                            try {
                                Timber.d("VlcDvdPlayer: [IO Thread] Adding option: :dvdnav-menu")
                                m.addOption(":dvdnav-menu")
                                Timber.d("VlcDvdPlayer: [IO Thread] Set dvdnav-menu option for MRL successfully")
                            } catch (e: Exception) {
                                Timber.w(e, "VlcDvdPlayer: [IO Thread] Failed to set dvdnav-menu option for MRL")
                            }
                            try {
                                Timber.d("VlcDvdPlayer: [IO Thread] Adding option: :dvdnav-angle=1")
                                m.addOption(":dvdnav-angle=1")
                                Timber.d("VlcDvdPlayer: [IO Thread] Set dvdnav-angle=1 option for MRL successfully")
                            } catch (e: Exception) {
                                Timber.w(e, "VlcDvdPlayer: [IO Thread] Failed to set dvdnav-angle option for MRL")
                            }
                            Timber.d("VlcDvdPlayer: [IO Thread] All MRL options set")
                        }
                        m
                    }
                    Timber.d("VlcDvdPlayer: MRL fallback media created: ${media != null}")
                } catch (e: Exception) {
                    Timber.e(e, "VlcDvdPlayer: Failed to create Media from MRL")
                }
            } else {
                Timber.w("VlcDvdPlayer: Cannot use fallback MRL - devicePath is not a file path: $devicePath")
                Timber.w("VlcDvdPlayer: Custom Native Input must be used. If it failed, playback is not possible.")
            }
        } else {
            Timber.d("VlcDvdPlayer: Media already created, skipping fallback")
        }

        // Final LibVLC operations must be on main thread
        Timber.d("VlcDvdPlayer: Final setup - checking if media is available")
        // Note: If native media was set directly, media will be null but playback should work
        if (media != null || nativeMediaHandle != 0L) {
            Timber.i("VlcDvdPlayer: Setting up MediaPlayer")
            Timber.d("VlcDvdPlayer: Switching to Main dispatcher for final setup")
            withContext(Dispatchers.Main) {
                Timber.d("VlcDvdPlayer: [Main Thread] Starting final playback setup")
                try {
                    if (media != null) {
                        // Standard path: Java Media object
                        Timber.d("VlcDvdPlayer: [Main Thread] Using Java Media object")
                        Timber.d("VlcDvdPlayer: [Main Thread] Disabling hardware decoder")
                        media.setHWDecoderEnabled(false, false)
                        Timber.d("VlcDvdPlayer: [Main Thread] Setting media on player")
                        player.media = media
                        Timber.d("VlcDvdPlayer: [Main Thread] Media set on player")
                        media.release()
                    } else if (nativeMediaHandle != 0L) {
                        // Native path: Media already set via nativeSetMediaOnPlayer
                        Timber.d("VlcDvdPlayer: [Main Thread] Using native Media (already set on player)")
                        Timber.d("VlcDvdPlayer: [Main Thread] Native media handle: $nativeMediaHandle")
                    }
                    
                    Timber.d("VlcDvdPlayer: [Main Thread] Starting playback")
                    Timber.d("VlcDvdPlayer: [Main Thread] Calling player.play()")
                    player.play()
                    applyTracks(player, audioStreamId, subtitleStreamId, audioLanguage, subtitleLanguage)
                    Timber.i("VlcDvdPlayer: [Main Thread] Playback started successfully")
                    Timber.d("VlcDvdPlayer: [Main Thread] Player state after play(): ${player.playerState}")
                } catch (e: Exception) {
                    Timber.e(e, "VlcDvdPlayer: [Main Thread] Error during playback setup")
                    // Log detailed error information
                    Timber.e("VlcDvdPlayer: [Main Thread] Error class: ${e.javaClass.name}")
                    Timber.e("VlcDvdPlayer: [Main Thread] Error message: ${e.message}")
                    Timber.e("VlcDvdPlayer: [Main Thread] Error stack trace:")
                    e.printStackTrace()
                    // Try to get player state for debugging
                    try {
                        val state = player.playerState
                        Timber.e("VlcDvdPlayer: [Main Thread] Player state after error: $state")
                    } catch (stateEx: Exception) {
                        Timber.w(stateEx, "VlcDvdPlayer: [Main Thread] Could not get player state")
                    }
                }
            }
            Timber.d("VlcDvdPlayer: Final setup complete")
        } else {
            Timber.e("VlcDvdPlayer: Could not create valid Media object - Playback aborted")
            Timber.e("VlcDvdPlayer: media is null, resetting currentBlockDevice")
            currentBlockDevice = null // Reset since we aren't using it
        }
        Timber.d("VlcDvdPlayer: playDvd() completed")
    }

    // --- Reflection Helpers ---

    private fun getLibVlcHandle(libVlc: LibVLC): Long {
        Timber.d("VlcDvdPlayer: getLibVlcHandle() called")
        Timber.d("VlcDvdPlayer: getLibVlcHandle() - libVlc class: ${libVlc.javaClass.name}")
        
        // Log ALL fields in the class hierarchy to find the correct one
        var clazz: Class<*>? = libVlc.javaClass
        var depth = 0
        val foundFields = mutableMapOf<String, Pair<Long, String>>()
        
        while (clazz != null && depth < 10) {
            Timber.e("VlcDvdPlayer: === Inspecting class $depth: ${clazz.name} ===")
            try {
                val fields = clazz.declaredFields
                Timber.e("VlcDvdPlayer: Found ${fields.size} fields in ${clazz.name}")
                for (field in fields) {
                    try {
                        field.isAccessible = true
                        val value = field.get(libVlc)
                        val typeName = field.type.name
                        
                        // Log all fields for debugging
                        if (value is Long) {
                            val hexValue = "0x${java.lang.Long.toHexString(value)}"
                            Timber.e("VlcDvdPlayer: FIELD: ${field.name} (Long) = $value ($hexValue)")
                            foundFields[field.name] = Pair(value, clazz.name)
                        } else if (value is Int) {
                            Timber.e("VlcDvdPlayer: FIELD: ${field.name} (Int) = $value")
                        } else if (value != null) {
                            Timber.d("VlcDvdPlayer: FIELD: ${field.name} ($typeName) = ${value.javaClass.name}")
                        }
                    } catch (e: Exception) {
                        Timber.d("VlcDvdPlayer: Could not read field ${field.name}: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "VlcDvdPlayer: Error inspecting class ${clazz.name}")
            }
            clazz = clazz.superclass
            depth++
        }
        
        // Now try to find the best candidate for the native handle
        // Look for fields named mInstance, mLibVlcInstance, or similar
        val priorityNames = listOf("mLibVlcInstance", "mInstance", "nativeHandle", "mNativeContext")
        for (name in priorityNames) {
            foundFields[name]?.let { (value, className) ->
                Timber.e("VlcDvdPlayer: SELECTED FIELD: $name from $className = $value (0x${java.lang.Long.toHexString(value)})")
                return value
            }
        }
        
        // If no known field found, return the first Long field found
        foundFields.entries.firstOrNull()?.let { (name, pair) ->
            Timber.e("VlcDvdPlayer: FALLBACK FIELD: $name from ${pair.second} = ${pair.first}")
            return pair.first
        }
        
        Timber.e("VlcDvdPlayer: getLibVlcHandle() - No Long fields found!")
        return 0L
    }

    private fun getMediaPlayerHandle(player: MediaPlayer): Long {
        Timber.d("VlcDvdPlayer: getMediaPlayerHandle() called")
        var clazz: Class<*>? = player.javaClass
        var depth = 0
        while (clazz != null && depth < 10) {
            try {
                val fields = clazz.declaredFields
                for (field in fields) {
                    if (field.type == Long::class.javaPrimitiveType || field.type == Long::class.java) {
                        try {
                            field.isAccessible = true
                            val value = field.getLong(player)
                            if (value != 0L) {
                                // Try common field names first
                                if (field.name in listOf("mInstance", "mPlayer", "nativeHandle")) {
                                    Timber.e("VlcDvdPlayer: Found MediaPlayer handle in field '${field.name}': $value")
                                    return value
                                }
                            }
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "VlcDvdPlayer: Error inspecting MediaPlayer class ${clazz.name}")
            }
            clazz = clazz.superclass
            depth++
        }
        Timber.e("VlcDvdPlayer: getMediaPlayerHandle() - No handle found!")
        return 0L
    }

    // --- Navigation Controls ---

    // --- IO Callbacks (Called from JNI) ---

    /**
     * Called by JNI to open the stream.
     */
    fun ioOpen(): Boolean {
        Timber.i("VlcDvdPlayer: ioOpen() CALLED - Thread: ${Thread.currentThread().name}")
        Timber.i("VlcDvdPlayer: ioOpen() - currentBlockDevice: ${currentBlockDevice != null}")
        if (currentBlockDevice == null) {
            Timber.e("VlcDvdPlayer: ioOpen() - currentBlockDevice is NULL - This will cause VLC to stop immediately!")
            Timber.e("VlcDvdPlayer: ioOpen() - Stack trace:")
            Thread.dumpStack()
            return false
        }
        
        try {
            Timber.d("VlcDvdPlayer: ioOpen() - Getting block size")
            // Verify device is still accessible
            val device = currentBlockDevice
            if (device == null) {
                Timber.e("VlcDvdPlayer: ioOpen() - Device became null during initialization")
                return false
            }
            
            // Test device accessibility
            val blockSize = try {
                device.blockSize
            } catch (e: Exception) {
                Timber.e(e, "VlcDvdPlayer: ioOpen() - Failed to get block size, device may be closed")
                return false
            }
            
            Timber.d("VlcDvdPlayer: ioOpen() - Block size: $blockSize")
            // Set device size - use VOB size if available, otherwise use default
            if (vobOffsets != null && totalVobSize > 0) {
                deviceSize = totalVobSize
                Timber.d("VlcDvdPlayer: ioOpen() - Device size set to VOB size: $deviceSize bytes (${deviceSize / 1024 / 1024} MB)")
            } else {
                // Set device size (8 GB for typical DVD)
                deviceSize = 8L * 1024 * 1024 * 1024 // 8GB placeholder, or calculate real size
                Timber.d("VlcDvdPlayer: ioOpen() - Device size set to default: $deviceSize bytes (${deviceSize / (1024 * 1024 * 1024)} GB)")
            }
            currentOffset = 0
            Timber.d("VlcDvdPlayer: ioOpen() - Current offset reset to: $currentOffset")
            
            // Initialize read buffer state
            readBuffer = null
            readBufferOffset = -1
            readBufferSize = 0
            Timber.d("VlcDvdPlayer: ioOpen() - Read buffer initialized")
            
            Timber.i("VlcDvdPlayer: ioOpen() - SUCCESS, returning true")
            return true
        } catch (e: Exception) {
            Timber.e(e, "VlcDvdPlayer: ioOpen() - Exception occurred: ${e.message}")
            Timber.e("VlcDvdPlayer: ioOpen() - Stack trace:")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Called by JNI to get stream size.
     */
    fun ioGetSize(): Long {
        Timber.d("VlcDvdPlayer: ioGetSize() called, returning: $deviceSize")
        return deviceSize
    }

    /**
     * Called by JNI to read data.
     * Handles both VOB data reading (when vobOffsets is set) and raw block data reading (fallback).
     * Optimized to read 64KB chunks to minimize USB transaction overhead.
     */
    fun ioRead(buffer: ByteArray, size: Int): Int {
        // Timber.v("VlcDvdPlayer: ioRead() called - size: $size bytes, currentOffset: $currentOffset")
        
        // Check if we should use VOB data reading
        if (vobOffsets != null && currentVobFile > 0) {
            return ioReadVob(buffer, size)
        }
        
        // Fallback to raw block data reading
        val device = currentBlockDevice
        if (device == null) {
            Timber.e("VlcDvdPlayer: ioRead() - currentBlockDevice is null, returning -1")
            return -1
        }
        
        try {
            // Use a 64KB read-ahead buffer (32 blocks of 2048 bytes)
            // DVD reads are typically 2KB (headers) or larger. 64KB is a good balance.
            val BUFFER_SIZE_BLOCKS = 32
            val blockSize = device.blockSize
            val READ_AHEAD_SIZE = blockSize * BUFFER_SIZE_BLOCKS // Typically 65536 bytes

            // 1. Check if the request can be fully satisfied from the existing buffer
            if (readBuffer != null && 
                currentOffset >= readBufferOffset && 
                currentOffset + size <= readBufferOffset + readBufferSize) {
                
                // Calculate index in readBuffer
                val offsetInBuffer = (currentOffset - readBufferOffset).toInt()
                System.arraycopy(readBuffer!!, offsetInBuffer, buffer, 0, size)
                
                currentOffset += size
                // Timber.v("VlcDvdPlayer: ioRead() - Satisfied from buffer. New offset: $currentOffset")
                return size
            }

            // 2. If not in buffer (or partially), we need to read from device.
            // For simplicity, we discard the current buffer and read a new chunk starting at currentOffset.
            // (Optimizing partial overlaps is possible but adds complexity; flushing is safer for random access)
            
            // Align read to block boundaries
            val startLba = currentOffset / blockSize
            val offsetInFirstBlock = (currentOffset % blockSize).toInt()
            
            // We want to read enough to cover the requested 'size', plus extra for read-ahead.
            // But we must align to block size for the device read.
            
            // Calculate total bytes we WANT to have available (request + read-ahead)
            val targetBytes = size + READ_AHEAD_SIZE
            
            // Calculate how many blocks we need to read to cover 'offsetInFirstBlock + targetBytes'
            // (offsetInFirstBlock shifts the start, so we might need an extra block at the end)
            val blocksToRead = (offsetInFirstBlock + targetBytes + blockSize - 1) / blockSize
            
            // Cap blocks to a reasonable maximum if 'size' is huge (unlikely for DVD navigation, but possible)
            // If request is larger than read-ahead, just read what is needed + a bit
            // But 'blocksToRead' calculated above handles it.
            
            val bytesToReadFromDevice = (blocksToRead * blockSize)
            
            // Reallocate buffer if needed
            if (readBuffer == null || readBuffer!!.size < bytesToReadFromDevice) {
                Timber.d("VlcDvdPlayer: ioRead() - Allocating buffer: $bytesToReadFromDevice bytes")
                readBuffer = ByteArray(bytesToReadFromDevice)
            }
            
            // Perform the USB/SCSI read
            // Timber.v("VlcDvdPlayer: ioRead() - Device read: LBA $startLba, blocks: $blocksToRead ($bytesToReadFromDevice bytes)")
            val bytesRead = device.read(startLba, readBuffer!!, bytesToReadFromDevice)
            
            if (bytesRead < 0) {
                Timber.e("VlcDvdPlayer: ioRead() - Device read failed at LBA $startLba")
                return -1
            }
            
            // Update buffer state
            readBufferOffset = startLba * blockSize
            readBufferSize = bytesRead
            
            // 3. Fulfil the request from the newly filled buffer
            if (bytesRead > offsetInFirstBlock) {
                // How much useful data do we have starting at currentOffset?
                val available = bytesRead - offsetInFirstBlock
                val toCopy = minOf(size, available)
                
                System.arraycopy(readBuffer!!, offsetInFirstBlock, buffer, 0, toCopy)
                currentOffset += toCopy
                
                // Timber.v("VlcDvdPlayer: ioRead() - Refilled buffer & read $toCopy bytes. Offset: $currentOffset")
                
                if (toCopy < size) {
                    Timber.w("VlcDvdPlayer: ioRead() - Partial read: requested $size, got $toCopy (EOF?)")
                }
                return toCopy
            } else {
                Timber.w("VlcDvdPlayer: ioRead() - EOF encountered (offsetInBlock $offsetInFirstBlock >= bytesRead $bytesRead)")
                return 0 // EOF
            }

        } catch (e: Exception) {
            Timber.e(e, "VlcDvdPlayer: ioRead() - Exception at offset=$currentOffset, size=$size")
            return -1
        }
    }
    
    /**
     * Reads VOB data using native DVD functions.
     * Maps currentOffset to VOB sectors and reads the appropriate data.
     */
    private fun ioReadVob(buffer: ByteArray, size: Int): Int {
        if (vobOffsets == null || currentVobFile <= 0) {
            Timber.e("VlcDvdPlayer: ioReadVob() - VOB data not available")
            return -1
        }
        
        try {
            // Map currentOffset to VOB sector
            // currentOffset is in bytes, we need to find which cell and sector
            var bytesRemaining = currentOffset
            var targetCell: com.ble1st.connectias.feature.dvd.native.VobOffsetInfo? = null
            var sectorOffsetInCell: Long = 0
            
            for (cell in vobOffsets!!) {
                val cellSizeBytes = (cell.lastSector - cell.firstSector + 1) * 2048L
                if (bytesRemaining < cellSizeBytes) {
                    targetCell = cell
                    sectorOffsetInCell = bytesRemaining / 2048L
                    break
                }
                bytesRemaining -= cellSizeBytes
            }
            
            if (targetCell == null) {
                Timber.w("VlcDvdPlayer: ioReadVob() - Offset $currentOffset beyond VOB data, returning EOF")
                return 0 // EOF
            }
            
            // Calculate VOB sector number (relative to VOB start)
            val vobSectorLong = targetCell.firstSector + sectorOffsetInCell
            val vobSector = vobSectorLong.toInt()
            val bytesOffsetInSector = (bytesRemaining % 2048).toInt()
            
            // Calculate how many sectors we need to read
            val totalBytesNeeded = size + bytesOffsetInSector
            val sectorsToRead = (totalBytesNeeded + 2047) / 2048  // Round up
            
            // Ensure we don't read beyond the current cell
            val maxSectorsInCell = (targetCell.lastSector - vobSectorLong + 1).toInt()
            val actualSectorsToRead = minOf(sectorsToRead, maxSectorsInCell)
            
            if (actualSectorsToRead <= 0) {
                Timber.w("VlcDvdPlayer: ioReadVob() - No sectors to read, returning EOF")
                return 0
            }
            
            // Allocate buffer for reading (sectors are 2048 bytes each)
            if (readBuffer == null || readBuffer!!.size < actualSectorsToRead * 2048) {
                readBuffer = ByteArray(actualSectorsToRead * 2048)
            }
            
            // Read VOB blocks using native function
            val bytesRead = com.ble1st.connectias.feature.dvd.native.DvdNative.dvdReadVobBlocks(
                currentVobFile, vobSector, actualSectorsToRead, readBuffer!!
            )
            
            if (bytesRead <= 0) {
                if (bytesRead == 0) {
                    Timber.d("VlcDvdPlayer: ioReadVob() - EOF reached")
                    return 0
                }
                Timber.e("VlcDvdPlayer: ioReadVob() - VOB read failed: $bytesRead")
                return -1
            }
            
            // Copy data to output buffer (skip bytesOffsetInSector if needed)
            val availableBytes = bytesRead - bytesOffsetInSector
            val toCopy = minOf(size, availableBytes)
            
            if (toCopy > 0 && bytesOffsetInSector < bytesRead) {
                System.arraycopy(readBuffer!!, bytesOffsetInSector, buffer, 0, toCopy)
                currentOffset += toCopy
            } else {
                Timber.w("VlcDvdPlayer: ioReadVob() - No data to copy (bytesOffsetInSector=$bytesOffsetInSector, bytesRead=$bytesRead)")
                return 0
            }
            
            return toCopy
            
        } catch (e: Exception) {
            Timber.e(e, "VlcDvdPlayer: ioReadVob() - Exception at offset=$currentOffset, size=$size")
            return -1
        }
    }

    /**
     * Called by JNI to seek.
     */
    fun ioSeek(offset: Long): Boolean {
        Timber.d("VlcDvdPlayer: ioSeek() called - seeking to offset: $offset")
        
        // Validate offset if VOB data is available
        if (vobOffsets != null && totalVobSize > 0) {
            if (offset !in 0..totalVobSize) {
                Timber.w("VlcDvdPlayer: ioSeek() - Offset $offset out of VOB range (0-$totalVobSize)")
                return false
            }
        }
        
        val oldOffset = currentOffset
        currentOffset = offset
        Timber.d("VlcDvdPlayer: ioSeek() - Offset changed from $oldOffset to $currentOffset")
        // Invalidate buffer on seek - data is no longer valid
        Timber.d("VlcDvdPlayer: ioSeek() - Invalidating read buffer")
        readBuffer = null
        readBufferOffset = -1
        readBufferSize = 0
        Timber.d("VlcDvdPlayer: ioSeek() - Buffer invalidated, returning true")
        return true
    }

    /**
     * Called by JNI to close.
     */
    fun ioClose() {
        Timber.d("VlcDvdPlayer: ioClose() called")
        Timber.d("VlcDvdPlayer: ioClose() - Current offset: $currentOffset")
        Timber.d("VlcDvdPlayer: ioClose() - Device size: $deviceSize")
        Timber.d("VlcDvdPlayer: ioClose() - Buffer state: ${readBuffer != null}, size: $readBufferSize")
        
        // Close VOB file if open
        if (currentVobFile > 0) {
            Timber.d("VlcDvdPlayer: ioClose() - Closing VOB file handle: $currentVobFile")
            com.ble1st.connectias.feature.dvd.native.DvdNative.dvdCloseVobFile(currentVobFile)
            currentVobFile = 0
        }
        
        // Reset VOB-related state
        vobOffsets = null
        vtsN = 0
        totalVobSize = 0
        
        // We don't close the device here, as we might reuse it.
        // Just reset state.
        Timber.d("VlcDvdPlayer: ioClose() - Stream closed (device remains open for reuse)")
    }

    // --- Lifecycle ---

    fun attachView(surfaceView: SurfaceView) {
        Timber.d("VlcDvdPlayer: attachView() called")
        Timber.d("VlcDvdPlayer: attachView() - surfaceView: $surfaceView")
        val vout = mediaPlayer?.vlcVout
        Timber.d("VlcDvdPlayer: attachView() - vout: $vout")
        if (vout != null) {
            Timber.d("VlcDvdPlayer: attachView() - Setting video view")
            vout.setVideoView(surfaceView)
            Timber.d("VlcDvdPlayer: attachView() - Attaching views")
            vout.attachViews()
            Timber.d("VlcDvdPlayer: attachView() - Views attached")

            // Center and fit video
            mediaPlayer?.scale = 0f // auto-scale to fit
            mediaPlayer?.aspectRatio = null // reset to default

            // Apply window size once layout is available to avoid 0x0
            surfaceView.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    val w = surfaceView.width
                    val h = surfaceView.height
                    if (w > 0 && h > 0) {
                        Timber.d("VlcDvdPlayer: attachView() - Setting window size after layout: ${w}x$h")
                        vout.setWindowSize(w, h)
                        mediaPlayer?.scale = 0f
                        mediaPlayer?.aspectRatio = null
                        surfaceView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    }
                }
            })
        } else {
            Timber.w("VlcDvdPlayer: attachView() - vout is null, cannot attach view")
        }
    }

    fun stop() {
        Timber.d("VlcDvdPlayer: stop() called")
        Timber.d("VlcDvdPlayer: stop() - Stopping media player")
        mediaPlayer?.stop()
        Timber.d("VlcDvdPlayer: stop() - Clearing currentBlockDevice")
        currentBlockDevice = null
        // Clear read buffer on stop
        Timber.d("VlcDvdPlayer: stop() - Clearing read buffer")
        readBuffer = null
        readBufferOffset = -1
        readBufferSize = 0
        Timber.d("VlcDvdPlayer: stop() - Complete")
    }

    fun pause() {
        Timber.d("VlcDvdPlayer: pause() called")
        mediaPlayer?.pause()
    }

    fun resume() {
        Timber.d("VlcDvdPlayer: resume() called")
        mediaPlayer?.play()
    }

    private fun applyTracks(
        player: MediaPlayer,
        audioStreamId: Int?,
        subtitleStreamId: Int?,
        audioLanguage: String?,
        subtitleLanguage: String?,
        attempt: Int = 1
    ) {
        if (tracksApplied) {
            Timber.d("VlcDvdPlayer: Track selection already applied, skipping (attempt $attempt)")
            return
        }

        val maxAttempts = 30
        val retryDelayMs = 500L

        try {
            val audioTracks = player.audioTracks
            val spuTracks = player.spuTracks
            Timber.d("VlcDvdPlayer: Available audio tracks: ${audioTracks?.size ?: 0}")
            audioTracks?.forEach { Timber.d("VlcDvdPlayer: Audio track id=${it.id}, name=${it.name}") }
            Timber.d("VlcDvdPlayer: Available subtitle tracks: ${spuTracks?.size ?: 0}")
            spuTracks?.forEach { Timber.d("VlcDvdPlayer: Subtitle track id=${it.id}, name=${it.name}") }

            val hasTracks = (audioTracks?.isNotEmpty() == true) || (spuTracks?.isNotEmpty() == true)
            if (!hasTracks && attempt < maxAttempts) {
                Timber.d("VlcDvdPlayer: Tracks not ready yet (attempt $attempt/$maxAttempts), retrying in ${retryDelayMs}ms")
                trackHandler.postDelayed({
                    applyTracks(player, audioStreamId, subtitleStreamId, audioLanguage, subtitleLanguage, attempt + 1)
                }, retryDelayMs)
                return
            } else if (!hasTracks && attempt >= maxAttempts) {
                Timber.w("VlcDvdPlayer: Tracks still unavailable after $maxAttempts attempts; will wait for future ES events")
                // Do not mark tracksApplied so ESAdded/LengthChanged can retrigger later
                return
            }

            // Resolve audio (prefer AC3 candidates for German: try 2 -> 3 -> 1 -> 4 -> -1)
            val preferredAudioIds: List<Int> = when (audioLanguage?.lowercase()) {
                "de", "ger", "german" -> listOf(2, 3, 1, 4, -1)
                else -> emptyList()
            }
            val audioId = preferredAudioIds.firstOrNull { id -> audioTracks?.any { it.id == id } == true }
                ?: resolveTrackId(audioStreamId, audioLanguage, audioTracks)
            val audioResolved = if (audioId != null) {
                Timber.i("VlcDvdPlayer: Setting audio track to $audioId (requested id=$audioStreamId, lang=$audioLanguage)")
                player.setAudioTrack(audioId)
                true
            } else if (audioStreamId == null && audioLanguage.isNullOrBlank()) {
                true // nothing requested
            } else {
                Timber.w("VlcDvdPlayer: Could not resolve audio track for id=$audioStreamId lang=$audioLanguage")
                false
            }

            // Resolve subtitles
            val subId = resolveTrackId(subtitleStreamId, subtitleLanguage, spuTracks, isSubtitle = true)
            val subtitleResolved = if (subId != null) {
                Timber.i("VlcDvdPlayer: Setting subtitle track to $subId (requested id=$subtitleStreamId, lang=$subtitleLanguage)")
                player.spuTrack = subId
                true
            } else if (subtitleStreamId == null && subtitleLanguage.isNullOrBlank()) {
                true // nothing requested
            } else {
                Timber.w("VlcDvdPlayer: Could not resolve subtitle track for id=$subtitleStreamId lang=$subtitleLanguage")
                false
            }

            if (audioResolved && subtitleResolved) {
                tracksApplied = true
                return
            }

            if (attempt < maxAttempts) {
                Timber.d("VlcDvdPlayer: Tracks partially resolved (audio=$audioResolved, sub=$subtitleResolved) (attempt $attempt/$maxAttempts), retrying in ${retryDelayMs}ms")
                trackHandler.postDelayed({
                    applyTracks(player, audioStreamId, subtitleStreamId, audioLanguage, subtitleLanguage, attempt + 1)
                }, retryDelayMs)
            } else {
                Timber.w("VlcDvdPlayer: Giving up after $maxAttempts attempts (audio=$audioResolved, sub=$subtitleResolved) but will keep waiting for ES events")
                // Keep tracksApplied=false so a later ESAdded can retry
            }
        } catch (e: Exception) {
            Timber.e(e, "VlcDvdPlayer: Failed to apply tracks")
        }
    }

    private fun scheduleTrackApply() {
        // Use last requested selections to retry applying tracks when VLC reports streams
        if (tracksApplied) return
        val player = mediaPlayer ?: return
        trackHandler.post {
            applyTracks(
                player,
                lastAudioStreamId,
                lastSubtitleStreamId,
                lastAudioLanguage,
                lastSubtitleLanguage,
                1
            )
        }
    }

    private fun resolveTrackId(
        requestedId: Int?,
        requestedLang: String?,
        tracks: Array<TrackDescription>?,
        isSubtitle: Boolean = false
    ): Int? {
        if (tracks == null || tracks.isEmpty()) return null
        // 1) Try direct id match
        if (requestedId != null && tracks.any { it.id == requestedId }) {
            return requestedId
        }
        // 2) Try id+1 (IFO index vs VLC id offset)
        if (requestedId != null && tracks.any { it.id == requestedId + 1 }) {
            return requestedId + 1
        }
        // 3) Subtitle-specific offsets: DVD SPU ids are often 0x20-based
        if (isSubtitle && requestedId != null) {
            val candidates = listOf(requestedId + 0x20, requestedId + 0x21)
            val matched = candidates.firstOrNull { cand -> tracks.any { it.id == cand } }
            if (matched != null) {
                Timber.d("VlcDvdPlayer: Subtitle id remapped via DVD offset: requested=$requestedId -> matched=$matched")
                return matched
            }
        }
        // 3) Try language match
        if (!requestedLang.isNullOrBlank()) {
            val lower = requestedLang.lowercase()
            val byLang = tracks.firstOrNull { it.name?.lowercase()?.contains(lower) == true }
            if (byLang != null) return byLang.id
        }
        return null
    }

    fun isPlaying(): Boolean {
        val playing = mediaPlayer?.isPlaying ?: false
        Timber.d("VlcDvdPlayer: isPlaying() -> $playing")
        return playing
    }

    fun getTimeMs(): Long {
        val time = mediaPlayer?.time ?: 0L
        Timber.v("VlcDvdPlayer: getTimeMs() -> $time")
        return time
    }

    fun getLengthMs(): Long {
        val length = mediaPlayer?.length ?: 0L
        Timber.v("VlcDvdPlayer: getLengthMs() -> $length")
        return length
    }

    fun seekTo(timeMs: Long) {
        val safeTime = if (timeMs < 0) 0L else timeMs
        Timber.d("VlcDvdPlayer: seekTo() called - target: $safeTime")
        mediaPlayer?.time = safeTime
    }

    fun seekBy(deltaMs: Long) {
        val current = mediaPlayer?.time ?: 0L
        val target = current + deltaMs
        Timber.d("VlcDvdPlayer: seekBy() called - delta: $deltaMs, target: $target")
        seekTo(target)
    }

    fun release() {
        Timber.d("VlcDvdPlayer: release() called")
        Timber.d("VlcDvdPlayer: release() - Releasing media player")
        mediaPlayer?.release()
        Timber.d("VlcDvdPlayer: release() - Releasing libVLC")
        libVlc?.release()
        Timber.d("VlcDvdPlayer: release() - Clearing references")
        mediaPlayer = null
        libVlc = null
        currentBlockDevice = null
        Timber.d("VlcDvdPlayer: release() - Complete")
    }
}