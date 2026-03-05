package com.bleist.connectias.connectias

import android.app.Activity
import android.os.ParcelFileDescriptor
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import io.flutter.plugin.common.MethodChannel
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * DVD Player plugin using LibVLC with custom I/O via pipe.
 * Streams data from Rust/NativeBridge.dvdReadStream into a pipe; LibVLC reads from the pipe FD.
 * MethodChannel: com.bleist.connectias/dvd
 */
class DvdPlayerPlugin(private val activity: Activity) : MethodChannel.MethodCallHandler {

    private var dvdHandle: Long = -1
    private var streamId: Long = -1
    private var libVlc: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var surfaceView: SurfaceView? = null
    private var readerThread: Thread? = null
    private val readerRunning = AtomicBoolean(false)
    private var writeFd: ParcelFileDescriptor? = null

    override fun onMethodCall(call: io.flutter.plugin.common.MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "loadDvd" -> {
                dvdHandle = (call.arguments as? Number)?.toLong() ?: -1L
                result.success(null)
            }
            "playTitle" -> {
                val titleId = (call.arguments as? Map<*, *>)?.get("titleId") as? Number ?: 1
                if (dvdHandle < 0) {
                    result.error("DVD_ERROR", "No DVD loaded", null)
                    return
                }
                streamId = NativeBridge.dvdOpenTitleStream(dvdHandle, titleId.toInt())
                if (streamId < 0) {
                    result.error("DVD_ERROR", NativeBridge.lastError() ?: "Open stream failed", null)
                    return
                }
                try {
                    startPlayback()
                    result.success(null)
                } catch (e: Exception) {
                    stopPlayback()
                    result.error("DVD_ERROR", e.message ?: "Playback failed", null)
                }
            }
            "pause" -> {
                mediaPlayer?.pause()
                result.success(null)
            }
            "resume" -> {
                mediaPlayer?.play()
                result.success(null)
            }
            "seek" -> {
                val positionMs = (call.arguments as? Map<*, *>)?.get("positionMs") as? Number ?: 0
                val offset = positionMs.toLong() * 1000L
                if (streamId >= 0) {
                    NativeBridge.dvdSeekStream(streamId, offset)
                }
                mediaPlayer?.time = positionMs.toLong()
                result.success(null)
            }
            "stop" -> {
                stopPlayback()
                result.success(null)
            }
            else -> result.notImplemented()
        }
    }

    private fun startPlayback() {
        stopPlayback()

        val pipe = ParcelFileDescriptor.createPipe()
        val readFd = pipe!![0]
        writeFd = pipe[1]

        readerRunning.set(true)
        readerThread = thread(name = "dvd-stream-reader") {
            val buf = ByteArray(256 * 1024)
            try {
                writeFd?.fileDescriptor?.let { fd ->
                    java.io.FileOutputStream(fd).use { out ->
                        while (readerRunning.get() && streamId >= 0) {
                            val n = NativeBridge.dvdReadStream(streamId, buf)
                            if (n <= 0) break
                            out.write(buf, 0, n)
                            out.flush()
                        }
                    }
                }
            } finally {
                try { writeFd?.close() } catch (_: Exception) {}
                writeFd = null
            }
        }

        activity.runOnUiThread {
            val lib = LibVLC(activity, ArrayList()).also { libVlc = it }
            val player = MediaPlayer(lib).also { mediaPlayer = it }

            val sv = SurfaceView(activity).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(h: SurfaceHolder) {
                        player.attachViews(h, null, null, false)
                    }
                    override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, height: Int) {}
                    override fun surfaceDestroyed(h: SurfaceHolder) {
                        player.detachViews()
                    }
                })
            }
            surfaceView = sv

            val layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            val content = activity.window.decorView.findViewById<android.view.ViewGroup>(android.R.id.content)
            content?.addView(sv, layoutParams)

            val media = Media(lib, readFd.fileDescriptor)
            player.media = media
            media.release()
            readFd.close()
            player.play()
        }
    }

    private fun stopPlayback() {
        readerRunning.set(false)
        readerThread?.join(2000)
        readerThread = null

        activity.runOnUiThread {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            surfaceView?.let { sv ->
                val content = activity.window.decorView.findViewById<android.view.ViewGroup>(android.R.id.content)
                content?.removeView(sv)
            }
            surfaceView = null
            libVlc?.release()
            libVlc = null
        }

        try { writeFd?.close() } catch (_: Exception) {}
        writeFd = null

        if (streamId >= 0) {
            NativeBridge.dvdCloseStream(streamId)
            streamId = -1
        }
    }
}
