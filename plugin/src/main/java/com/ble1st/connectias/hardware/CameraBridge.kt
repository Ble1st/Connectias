package com.ble1st.connectias.hardware

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.SharedMemory
import android.system.OsConstants
import android.view.Surface
import androidx.core.content.ContextCompat
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Camera Bridge for isolated process camera access
 * 
 * Provides camera capture and preview capabilities to plugins
 * running in isolated sandbox via IPC.
 * 
 * ARCHITECTURE:
 * - Uses Camera2 API for modern camera access
 * - Captures to temp files, returns ParcelFileDescriptor
 * - Preview uses SharedMemory (future implementation)
 * 
 * SECURITY:
 * - Requires CAMERA permission check before access
 * - Manages camera lifecycle per plugin
 * - Auto-cleanup on plugin unload
 * 
 * @since 2.0.0
 */
class CameraBridge(private val context: Context) {
    
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val activeCaptures = ConcurrentHashMap<String, CameraDevice>()
    private val activePreviews = ConcurrentHashMap<String, CaptureSession>()
    
    private val cameraThread = HandlerThread("CameraBridge").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)
    
    private data class CaptureSession(
        val device: CameraDevice,
        val captureSession: CameraCaptureSession,
        val imageReader: ImageReader
    )
    
    /**
     * Capture single image from camera
     * 
     * @param pluginId Plugin requesting capture
     * @return HardwareResponseParcel with image file descriptor
     */
    fun captureImage(pluginId: String): HardwareResponseParcel {
        return try {
            val cameraId = getCameraId() ?: return HardwareResponseParcel.failure("No camera available")
            
            Timber.d("[CAMERA BRIDGE] Starting capture for $pluginId using camera $cameraId")
            
            // Create temp file for image
            val imageFile = createTempImageFile(pluginId)
            
            // Open camera and capture (simplified synchronous version)
            val imageData = captureImageSync(cameraId)
            
            // Write to file
            FileOutputStream(imageFile).use { it.write(imageData) }
            
            // Return file descriptor
            val fd = ParcelFileDescriptor.open(
                imageFile,
                ParcelFileDescriptor.MODE_READ_ONLY
            )
            
            Timber.i("[CAMERA BRIDGE] Image captured for $pluginId: ${imageFile.length()} bytes")
            
            HardwareResponseParcel.success(
                fileDescriptor = fd,
                metadata = mapOf(
                    "format" to "jpeg",
                    "size" to imageFile.length().toString(),
                    "path" to imageFile.absolutePath
                )
            )
            
        } catch (e: Exception) {
            Timber.e(e, "[CAMERA BRIDGE] Capture failed for $pluginId")
            HardwareResponseParcel.failure(e)
        }
    }
    
    /**
     * Start camera preview using SharedMemory
     * Returns a SharedMemory region that the plugin can read frames from
     * 
     * Frame format: YUV_420_888
     * Frame size: 640x480
     * Buffer size: 640 * 480 * 3 / 2 = 460,800 bytes per frame
     * 
     * @param pluginId Plugin requesting preview
     * @return HardwareResponseParcel with SharedMemory FD
     */
    fun startPreview(pluginId: String): HardwareResponseParcel {
        return try {
            val cameraId = getCameraId() 
                ?: return HardwareResponseParcel.failure("No camera available")
            
            Timber.i("[CAMERA BRIDGE] Starting preview for $pluginId using camera $cameraId")
            
            // Preview dimensions
            val previewWidth = 640
            val previewHeight = 480
            val frameSize = previewWidth * previewHeight * 3 / 2 // YUV_420_888
            
            // Create SharedMemory for frame buffer (double buffering)
            val sharedMemory = SharedMemory.create("camera_preview_$pluginId", frameSize * 2)
            sharedMemory.setProtect(OsConstants.PROT_READ or OsConstants.PROT_WRITE)
            
            // Create ImageReader for preview frames
            val imageReader = ImageReader.newInstance(
                previewWidth,
                previewHeight,
                ImageFormat.YUV_420_888,
                2 // Double buffering
            )
            
            // Set up frame callback to write to SharedMemory
            var frameIndex = 0
            imageReader.setOnImageAvailableListener({ reader ->
                var image: android.media.Image? = null
                try {
                    image = reader.acquireLatestImage()
                    if (image == null) return@setOnImageAvailableListener
                    
                    // Map SharedMemory for writing
                    val byteBuffer = sharedMemory.mapReadWrite()
                    
                    // Write frame data to SharedMemory
                    val offset = (frameIndex % 2) * frameSize
                    byteBuffer.position(offset)
                    
                    // Copy YUV planes with proper stride handling
                    val planes = image.planes
                    for (i in planes.indices) {
                        val plane = planes[i]
                        val buffer = plane.buffer
                        val rowStride = plane.rowStride
                        val pixelStride = plane.pixelStride
                        
                        // Calculate plane dimensions
                        val width = if (i == 0) previewWidth else previewWidth / 2
                        val height = if (i == 0) previewHeight else previewHeight / 2
                        
                        // Copy row by row if stride doesn't match width
                        if (pixelStride == 1 && rowStride == width) {
                            // Contiguous data, copy directly
                            val data = ByteArray(width * height)
                            buffer.get(data)
                            byteBuffer.put(data)
                        } else {
                            // Need to copy row by row
                            val rowData = ByteArray(width)
                            for (row in 0 until height) {
                                buffer.position(row * rowStride)
                                buffer.get(rowData)
                                byteBuffer.put(rowData)
                            }
                        }
                    }
                    
                    SharedMemory.unmap(byteBuffer)
                    frameIndex++
                    
                } catch (e: Exception) {
                    Timber.w(e, "[CAMERA BRIDGE] Frame processing error")
                } finally {
                    // Always close image to prevent buffer leak
                    image?.close()
                }
            }, cameraHandler)
            
            // Open camera (simplified - production would use proper callback)
            val cameraDevice = openCameraSync(cameraId)
            
            // Create capture session
            val surface = imageReader.surface
            val captureSession = createCaptureSessionSync(cameraDevice, listOf(surface))
            
            // Start preview
            val captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
            }.build()
            
            captureSession.setRepeatingRequest(captureRequest, null, cameraHandler)
            
            // Store session
            activePreviews[pluginId] = CaptureSession(cameraDevice, captureSession, imageReader)
            
            // Get file descriptor from SharedMemory
            // Use reflection to get the underlying int fd from SharedMemory
            val getFdMethod = SharedMemory::class.java.getDeclaredMethod("getFd")
            getFdMethod.isAccessible = true
            val fd = getFdMethod.invoke(sharedMemory) as Int
            
            // CRITICAL: Use dup() instead of adoptFd() to avoid closing the original FD
            // adoptFd() takes ownership and closes the FD when ParcelFileDescriptor is closed
            // But we still need the FD for SharedMemory operations!
            val parcelFd = ParcelFileDescriptor.fromFd(fd)
            
            Timber.i("[CAMERA BRIDGE] Preview started for $pluginId: ${previewWidth}x${previewHeight}")
            
            HardwareResponseParcel.success(
                fileDescriptor = parcelFd,
                metadata = mapOf(
                    "width" to previewWidth.toString(),
                    "height" to previewHeight.toString(),
                    "format" to "YUV_420_888",
                    "frameSize" to frameSize.toString(),
                    "bufferSize" to (frameSize * 2).toString()
                )
            )
            
        } catch (e: Exception) {
            Timber.e(e, "[CAMERA BRIDGE] Preview failed for $pluginId")
            HardwareResponseParcel.failure(e)
        }
    }
    
    /**
     * Stop camera preview
     * 
     * @param pluginId Plugin ID
     */
    fun stopPreview(pluginId: String) {
        try {
            activePreviews.remove(pluginId)?.let { session ->
                session.captureSession.close()
                session.imageReader.close()
                session.device.close()
                Timber.i("[CAMERA BRIDGE] Preview stopped for $pluginId")
            }
        } catch (e: Exception) {
            Timber.e(e, "[CAMERA BRIDGE] Stop preview failed for $pluginId")
        }
    }
    
    /**
     * Cleanup all camera resources
     */
    fun cleanup() {
        try {
            Timber.i("[CAMERA BRIDGE] Cleanup started")
            
            activeCaptures.values.forEach { device ->
                try {
                    device.close()
                } catch (e: Exception) {
                    Timber.w(e, "[CAMERA BRIDGE] Device close error")
                }
            }
            activeCaptures.clear()
            
            activePreviews.values.forEach { session ->
                try {
                    session.captureSession.close()
                    session.imageReader.close()
                    session.device.close()
                } catch (e: Exception) {
                    Timber.w(e, "[CAMERA BRIDGE] Session close error")
                }
            }
            activePreviews.clear()
            
            cameraThread.quitSafely()
            
            Timber.i("[CAMERA BRIDGE] Cleanup completed")
            
        } catch (e: Exception) {
            Timber.e(e, "[CAMERA BRIDGE] Cleanup error")
        }
    }
    
    // ════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ════════════════════════════════════════════════════════
    
    private fun getCameraId(): String? {
        return try {
            cameraManager.cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                facing == CameraCharacteristics.LENS_FACING_BACK
            } ?: cameraManager.cameraIdList.firstOrNull()
        } catch (e: Exception) {
            Timber.e(e, "[CAMERA BRIDGE] Failed to get camera ID")
            null
        }
    }
    
    private fun createTempImageFile(pluginId: String): File {
        val tempDir = File(context.cacheDir, "camera_bridge/$pluginId")
        tempDir.mkdirs()
        return File.createTempFile("capture_", ".jpg", tempDir)
    }
    
    /**
     * Capture image synchronously using Camera2 API
     * Opens camera, captures single JPEG image, and returns the data
     */
    private fun captureImageSync(cameraId: String): ByteArray {
        Timber.d("[CAMERA BRIDGE] Starting synchronous capture for camera $cameraId")
        
        var capturedData: ByteArray? = null
        val captureLatch = java.util.concurrent.CountDownLatch(1)
        
        // Create ImageReader for JPEG capture
        val imageReader = ImageReader.newInstance(
            1920, 1080, // Resolution
            ImageFormat.JPEG,
            2 // Need 2 for pre-capture + actual capture
        )
        
        // Set up image available callback
        imageReader.setOnImageAvailableListener({ reader ->
            try {
                val image = reader.acquireLatestImage()
                if (image != null) {
                    val buffer = image.planes[0].buffer
                    val data = ByteArray(buffer.remaining())
                    buffer.get(data)
                    capturedData = data
                    image.close()
                    Timber.d("[CAMERA BRIDGE] Image data captured: ${data.size} bytes")
                }
            } catch (e: Exception) {
                Timber.e(e, "[CAMERA BRIDGE] Error reading image data")
            } finally {
                captureLatch.countDown()
            }
        }, cameraHandler)
        
        try {
            // Open camera
            val device = openCameraSync(cameraId)
            
            // Create capture session
            val session = createCaptureSessionSync(device, listOf(imageReader.surface))
            
            // Run pre-capture sequence for auto-exposure and auto-focus
            Timber.d("[CAMERA BRIDGE] Running pre-capture sequence for AE/AF")
            val precaptureRequest = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(imageReader.surface)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
                set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START)
            }.build()
            
            session.capture(precaptureRequest, null, cameraHandler)
            
            // Wait for sensor to stabilize
            Thread.sleep(500)
            
            // Capture single image
            val captureRequest = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(imageReader.surface)
                set(CaptureRequest.JPEG_QUALITY, 95.toByte())
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)
            }.build()
            
            session.capture(captureRequest, object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    Timber.d("[CAMERA BRIDGE] Capture completed")
                }
                
                override fun onCaptureFailed(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    failure: CaptureFailure
                ) {
                    Timber.e("[CAMERA BRIDGE] Capture failed: ${failure.reason}")
                    captureLatch.countDown()
                }
            }, cameraHandler)
            
            // Wait for capture to complete (max 5 seconds)
            val success = captureLatch.await(5, java.util.concurrent.TimeUnit.SECONDS)
            
            // Cleanup
            session.close()
            device.close()
            imageReader.close()
            
            if (!success || capturedData == null) {
                Timber.w("[CAMERA BRIDGE] Capture timeout or no data")
                throw IllegalStateException("Failed to capture image")
            }
            
            Timber.i("[CAMERA BRIDGE] Image captured successfully: ${capturedData.size} bytes")
            return capturedData
            
        } catch (e: Exception) {
            Timber.e(e, "[CAMERA BRIDGE] Capture failed")
            imageReader.close()
            throw e
        }
    }
    
    /**
     * Open camera synchronously (simplified)
     * Production code should use proper callbacks
     */
    @Suppress("DEPRECATION")
    @SuppressLint("MissingPermission") // Permission checked above
    private fun openCameraSync(cameraId: String): CameraDevice {
        // Check camera permission before opening camera
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("Camera permission not granted")
        }
        
        var device: CameraDevice? = null
        val lock = java.util.concurrent.CountDownLatch(1)
        
        try {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                device = camera
                lock.countDown()
            }
            
            override fun onDisconnected(camera: CameraDevice) {
                camera.close()
                lock.countDown()
            }
            
            override fun onError(camera: CameraDevice, error: Int) {
                camera.close()
                lock.countDown()
            }
        }, cameraHandler)
        } catch (e: CameraAccessException) {
            throw IllegalStateException("Failed to open camera", e)
        }
        
        lock.await()
        return device ?: throw IllegalStateException("Failed to open camera")
    }
    
    /**
     * Create capture session synchronously (simplified)
     */
    private fun createCaptureSessionSync(device: CameraDevice, surfaces: List<Surface>): CameraCaptureSession {
        var session: CameraCaptureSession? = null
        val lock = java.util.concurrent.CountDownLatch(1)
        
        device.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                session = cameraCaptureSession
                lock.countDown()
            }
            
            override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                lock.countDown()
            }
        }, cameraHandler)
        
        lock.await()
        return session ?: throw IllegalStateException("Failed to create capture session")
    }
}
