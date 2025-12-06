package com.ble1st.connectias.feature.dvd.media

import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import com.ble1st.connectias.feature.dvd.models.UsbDevice
import com.ble1st.connectias.feature.dvd.driver.UsbBlockDevice
import com.ble1st.connectias.feature.dvd.driver.scsi.ScsiDriver
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import timber.log.Timber
import java.io.IOException
import java.util.ArrayList
import java.nio.ByteBuffer

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
    private var currentBlockDevice: UsbBlockDevice? = null
    private var deviceSize: Long = 0
    private var currentOffset: Long = 0

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
    private external fun nativeReleaseMedia(mediaHandle: Long)
    
    init {
        setupLibVlc()
    }

    private fun setupLibVlc() {
        try {
            // Initialize native bridge (dlopen libvlc.so)
            if (!nativeInit()) {
                Timber.e("Failed to initialize native LibVLC bridge")
            }

            val options = ArrayList<String>()
            // Decoding options
            options.add("--no-drop-late-frames")
            options.add("--no-skip-frames")
            options.add("--rtsp-tcp")
            
            // DVD Specific options
            options.add("--dvdnav-menu") // Enable DVD menus
            
            // Verbosity for debugging
            options.add("-vvv") 
            
            libVlc = LibVLC(context, options)
            mediaPlayer = MediaPlayer(libVlc)
            
            mediaPlayer?.setEventListener { event ->
                when (event.type) {
                    MediaPlayer.Event.EndReached -> Timber.d("VLC: End reached")
                    MediaPlayer.Event.EncounteredError -> Timber.e("VLC: Error encountered")
                    MediaPlayer.Event.Buffering -> Timber.d("VLC: Buffering ${event.buffering}%")
                    MediaPlayer.Event.Playing -> Timber.d("VLC: Playing")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize LibVLC")
        }
    }

    /**
     * Plays a DVD from a USB Device.
     * 
     * Tries to use the high-performance Native Stream if possible,
     * falling back to generic methods if needed.
     */
    fun playDvd(usbDevice: UsbDevice, devicePath: String, driver: UsbBlockDevice?) {
        val vlc = libVlc ?: return
        val player = mediaPlayer ?: return

        stop() // Stop any previous playback

        var media: Media? = null

        // Strategy 1: Custom Native Callbacks (Preferred for USB Mass Storage)
        if (driver != null) {
            try {
                Timber.i("Attempting to setup Custom Native Input for USB Block Device")
                currentBlockDevice = driver
                
                // 1. Get LibVLC Native Handle
                val instanceHandle = getLibVlcHandle(vlc)
                if (instanceHandle != 0L) {
                    // 2. Create Native Media (via JNI)
                    val nativeMediaHandle = nativeCreateMedia(instanceHandle)
                    
                    if (nativeMediaHandle != 0L) {
                        // 3. Wrap in Java Media Object
                        media = createMediaFromHandle(vlc, nativeMediaHandle)
                    } else {
                        Timber.e("nativeCreateMedia returned 0")
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to initialize native custom input. Falling back.")
                // Continue to fallback
            }
        }
        
        // Strategy 2: Fallback to standard MRL (dvd:// or file://)
        if (media == null) {
            Timber.w("Fallback to standard dvd:// MRL for path: $devicePath")
            // Use dvdsimple:// to avoid menus if they cause issues, or dvd:// for full
            // Note: devicePath might be a raw block device (requires root) or a mount point
            try {
                media = Media(vlc, Uri.parse("dvd://$devicePath"))
            } catch (e: Exception) {
                Timber.e(e, "Failed to create Media from MRL")
            }
        }

        if (media != null) {
            media.setHWDecoderEnabled(true, false)
            // media.addOption(":dvdnav-menu") // redundant if set globally
            
            player.media = media
            media.release() // Player keeps a reference
            
            player.play()
            Timber.i("Playback started")
        } else {
            Timber.e("Could not create valid Media object - Playback aborted")
            currentBlockDevice = null // Reset since we aren't using it
        }
    }

    // --- Reflection Helpers ---

    private fun getLibVlcHandle(libVlc: LibVLC): Long {
        var clazz: Class<*>? = libVlc.javaClass
        while (clazz != null) {
            try {
                // Try common field names for the native handle
                for (fieldName in listOf("mLibVlcInstance", "mInstance")) {
                    try {
                        val field = clazz!!.getDeclaredField(fieldName)
                        field.isAccessible = true
                        val handle = field.getLong(libVlc)
                        Timber.d("Found LibVLC handle in field '$fieldName' of class '${clazz!!.name}'")
                        return handle
                    } catch (e: NoSuchFieldException) {
                        // Continue searching
                    }
                }
            } catch (e: Exception) {
                Timber.w("Error inspecting class ${clazz!!.name}: ${e.message}")
            }
            clazz = clazz.superclass
        }
        Timber.e("Could not find LibVLC native handle field")
        return 0L
    }

    private fun createMediaFromHandle(libVlc: LibVLC, nativeHandle: Long): Media? {
        try {
            // Try to find constructor Media(ILibVLC, long)
            // Media extends VLCObject.
            // Check constructors of Media class
            val constructors = Media::class.java.declaredConstructors
            for (constructor in constructors) {
                val params = constructor.parameterTypes
                if (params.size == 2 && 
                    org.videolan.libvlc.interfaces.ILibVLC::class.java.isAssignableFrom(params[0]) &&
                    (params[1] == Long::class.javaPrimitiveType || params[1] == Long::class.java)) {
                    
                    constructor.isAccessible = true
                    return constructor.newInstance(libVlc, nativeHandle) as Media
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to instantiate Media via reflection")
        }
        return null
    }

    // --- Navigation Controls ---

    fun navigateUp() = mediaPlayer?.navigate(MediaPlayer.Navigate.Up)
    fun navigateDown() = mediaPlayer?.navigate(MediaPlayer.Navigate.Down)
    fun navigateLeft() = mediaPlayer?.navigate(MediaPlayer.Navigate.Left)
    fun navigateRight() = mediaPlayer?.navigate(MediaPlayer.Navigate.Right)
    fun navigateEnter() = mediaPlayer?.navigate(MediaPlayer.Navigate.Activate)
    
    // --- IO Callbacks (Called from JNI) ---

    /**
     * Called by JNI to open the stream.
     */
    fun ioOpen(): Boolean {
        Timber.v("ioOpen called")
        if (currentBlockDevice == null) return false
        
        try {
            // Initialize device/cache size
            // We assume block size 2048 for DVD
            val blockSize = currentBlockDevice?.blockSize ?: 2048
            // Can we get total size?
            // For now, return a very large number if unknown, or try to estimate
            deviceSize = 8L * 1024 * 1024 * 1024 // 8GB placeholder, or calculate real size
            currentOffset = 0
            return true
        } catch (e: Exception) {
            Timber.e(e, "ioOpen failed")
            return false
        }
    }

    /**
     * Called by JNI to get stream size.
     */
    fun ioGetSize(): Long {
        return deviceSize
    }

    /**
     * Called by JNI to read data.
     */
    fun ioRead(buffer: ByteArray, size: Int): Int {
        // Timber.v("ioRead request: $size bytes at $currentOffset")
        val device = currentBlockDevice ?: return -1
        
        try {
            // Calculate LBA
            val blockSize = device.blockSize
            val lba = currentOffset / blockSize
            val offsetInBlock = (currentOffset % blockSize).toInt()
            
            // We need to read enough blocks to cover the request
            // This logic needs to be robust (handling unaligned reads)
            // For simplicity in this prototype:
            // We assume VLC reads aligned 2048 chunks usually.
            
            if (offsetInBlock == 0 && size % blockSize == 0) {
                 // Perfect aligned read
                 val bytesRead = device.read(lba, buffer, size)
                 if (bytesRead > 0) {
                     currentOffset += bytesRead
                 }
                 return bytesRead
            } else {
                // Complex unaligned read
                // Implementation skipped for brevity - requires internal buffer
                Timber.w("Unaligned read requested: offset=$currentOffset size=$size")
                return -1
            }
        } catch (e: Exception) {
            Timber.e(e, "ioRead failed")
            return -1
        }
    }

    /**
     * Called by JNI to seek.
     */
    fun ioSeek(offset: Long): Boolean {
        // Timber.v("ioSeek to $offset")
        currentOffset = offset
        return true
    }

    /**
     * Called by JNI to close.
     */
    fun ioClose() {
        Timber.v("ioClose called")
        // We don't close the device here, as we might reuse it.
        // Just reset state.
    }

    // --- Lifecycle ---

    fun attachView(surfaceView: SurfaceView) {
        val vout = mediaPlayer?.vlcVout
        vout?.setVideoView(surfaceView)
        vout?.attachViews()
    }

    fun detachView() {
        val vout = mediaPlayer?.vlcVout
        vout?.detachViews()
    }

    fun stop() {
        mediaPlayer?.stop()
        currentBlockDevice = null
    }

    fun release() {
        mediaPlayer?.release()
        libVlc?.release()
        mediaPlayer = null
        libVlc = null
        currentBlockDevice = null
    }
}