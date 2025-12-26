package com.ble1st.connectias.feature.dvd.media

import android.content.Context
import android.hardware.usb.UsbManager
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.ble1st.connectias.feature.dvd.driver.scsi.ScsiDriver
import com.ble1st.connectias.feature.dvd.models.AudioTrack
import com.ble1st.connectias.feature.dvd.models.OpticalDrive
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.net.toUri

/**
 * Player for Audio CD tracks using direct SCSI access.
 */
@Singleton
@UnstableApi
class AudioCdPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val usbManager: UsbManager by lazy {
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    }

    private val exoPlayerLock = Any()
    private var exoPlayer: ExoPlayer? = null
    
    // Hold reference to the current driver to close it when playback stops
    private var currentScsiDriver: ScsiDriver? = null

    /**
     * Initializes ExoPlayer if not already initialized.
     */
    private fun ensurePlayerInitialized() {
        synchronized(exoPlayerLock) {
            if (exoPlayer == null) {
                exoPlayer = ExoPlayer.Builder(context).build().apply {
                    addListener(object : Player.Listener {
                        override fun onPlayerError(error: PlaybackException) {
                            Timber.e(error, "ExoPlayer error during Audio CD playback")
                            closeCurrentDriver()
                        }
                        
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
                                // Optionally close driver here if we want to release USB when not playing?
                                // Better to keep open for playlist playback, close on explicit stop or release.
                            }
                        }
                    })
                }
                Timber.d("ExoPlayer created")
            }
        }
    }
    
    /**
     * Plays an audio track from an Audio CD via SCSI.
     */
    fun playTrack(drive: OpticalDrive, track: AudioTrack) {
        try {
            Timber.d("Preparing to play Audio CD track ${track.number}: ${track.title}")
            
            // 1. Open USB Connection and create SCSI Driver
            // Close any existing driver first
            closeCurrentDriver()
            
            val androidDevice = usbManager.deviceList.values.find {
                it.vendorId == drive.device.vendorId && it.productId == drive.device.productId
            } ?: run {
                Timber.e("USB Device not found for playback")
                return
            }
            
            if (!usbManager.hasPermission(androidDevice)) {
                Timber.e("No permission to access USB device for playback")
                return
            }
            
            val connection = usbManager.openDevice(androidDevice) ?: run {
                Timber.e("Failed to open USB connection for playback")
                return
            }
            
            // Find Mass Storage Interface
            var interfaceIndex = 0
            for (i in 0 until androidDevice.interfaceCount) {
                if (androidDevice.getInterface(i).interfaceClass == 8) {
                    interfaceIndex = i
                    break
                }
            }
            val usbInterface = androidDevice.getInterface(interfaceIndex)
            
            val scsiDriver = ScsiDriver(connection, usbInterface)
            currentScsiDriver = scsiDriver
            
            // 2. Create Custom DataSource Factory
            val dataSourceFactory = DataSource.Factory {
                AudioCdDataSource(scsiDriver, track)
            }
            
            // 3. Configure ExoPlayer
            ensurePlayerInitialized()
            
            synchronized(exoPlayerLock) {
                val player = exoPlayer ?: return
                
                player.stop()
                player.clearMediaItems()
                
                // We need to use a MediaSourceFactory that uses our DataSource
                val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
                
                // Create a dummy URI (AudioCdDataSource ignores it or uses it for logging)
                val uri = "audiocd://${drive.device.uniqueId}/${track.number}".toUri()
                val mediaItem = MediaItem.fromUri(uri)
                
                val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)
                
                player.setMediaSource(mediaSource)
                player.prepare()
                player.play()
                
                Timber.i("Started playback of track ${track.number} via SCSI")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error playing audio track")
            closeCurrentDriver()
        }
    }
    
    private fun closeCurrentDriver() {
        try {
            currentScsiDriver?.close()
        } catch (e: Exception) {
            Timber.w(e, "Error closing SCSI driver")
        } finally {
            currentScsiDriver = null
        }
    }

    /**
     * Checks if audio is currently playing.
     */
    fun isPlaying(): Boolean {
        return synchronized(exoPlayerLock) {
            exoPlayer?.isPlaying ?: false
        }
    }
    
    /**
     * Pauses playback.
     */
    fun pause() {
        synchronized(exoPlayerLock) {
            exoPlayer?.pause()
        }
    }
    
    /**
     * Stops playback and releases USB resources.
     */
    fun stop() {
        synchronized(exoPlayerLock) {
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()
        }
        closeCurrentDriver()
    }

}
