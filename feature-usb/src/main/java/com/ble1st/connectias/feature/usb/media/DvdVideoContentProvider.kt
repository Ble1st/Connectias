package com.ble1st.connectias.feature.usb.media

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.ble1st.connectias.feature.usb.native.DvdNative
import dagger.hilt.android.EntryPoints
import timber.log.Timber
import java.io.FileNotFoundException

/**
 * ContentProvider for streaming DVD video content.
 * 
 * Handles content:// URIs with the pattern:
 * content://com.ble1st.connectias.dvd/video/{mountPoint}/{titleNumber}/{chapterNumber}
 * 
 * The mountPoint is URL-encoded in the URI and must be decoded before use.
 * 
 * Security: This provider enforces runtime permission checks and validates
 * all input parameters to prevent path traversal attacks.
 */
class DvdVideoContentProvider : ContentProvider() {
    
    companion object {
        private const val AUTHORITY = "com.ble1st.connectias.dvd"
        private const val VIDEO_PATH = "video"
        private const val MIME_TYPE_VIDEO = "video/mp2t" // MPEG-2 Transport Stream (common DVD format)
        
        /**
         * Builds a content URI for DVD video streaming.
         * 
         * @param mountPoint The DVD mount point (will be URL-encoded)
         * @param titleNumber The title number (>= 1)
         * @param chapterNumber The chapter number (>= 1)
         * @return The content URI
         */
        fun buildUri(mountPoint: String, titleNumber: Int, chapterNumber: Int): Uri {
            val encodedMountPoint = Uri.encode(mountPoint)
            return Uri.parse("content://$AUTHORITY/$VIDEO_PATH/$encodedMountPoint/$titleNumber/$chapterNumber")
        }
    }
    
    private var handleRegistry: DvdHandleRegistry? = null
    
    override fun onCreate(): Boolean {
        Timber.d("DvdVideoContentProvider onCreate")
        
        // Get DvdHandleRegistry from Hilt entry point
        // Note: ContentProvider is created before Application, so we need to use
        // EntryPoints.get() with the application context
        val context = context ?: return false
        val appContext = context.applicationContext
        
        try {
            // Use EntryPoints.get() which works even if Application is not yet initialized
            // This requires the application context to have Hilt initialized
            val entryPoint = EntryPoints.get(
                appContext,
                DvdHandleRegistryEntryPoint::class.java
            )
            handleRegistry = entryPoint.dvdHandleRegistry()
            Timber.d("DvdVideoContentProvider initialized with registry")
            return true
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize DvdVideoContentProvider")
            // If initialization fails, we'll try to get it lazily on first access
            // This allows the ContentProvider to be created even if Hilt isn't ready yet
            return true
        }
    }
    
    /**
     * Gets the handle registry, initializing it lazily if needed.
     * 
     * @return The DvdHandleRegistry instance
     * @throws IllegalStateException if registry cannot be initialized
     */
    private fun getHandleRegistry(): DvdHandleRegistry {
        if (handleRegistry != null) {
            return handleRegistry!!
        }
        
        // Lazy initialization if onCreate() failed
        val context = context ?: throw IllegalStateException("ContentProvider context is null")
        val appContext = context.applicationContext
        
        try {
            val entryPoint = EntryPoints.get(
                appContext,
                DvdHandleRegistryEntryPoint::class.java
            )
            handleRegistry = entryPoint.dvdHandleRegistry()
            Timber.d("DvdVideoContentProvider registry initialized lazily")
            return handleRegistry!!
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize DvdVideoContentProvider registry")
            throw IllegalStateException("ContentProvider registry not available", e)
        }
    }
    
    /**
     * Parses the URI to extract mount point, title number, and chapter number.
     * 
     * @param uri The content URI
     * @return ParsedUriData or null if URI is invalid
     */
    private fun parseUri(uri: Uri): ParsedUriData? {
        val pathSegments = uri.pathSegments ?: return null
        
        // Expected format: /video/{mountPoint}/{titleNumber}/{chapterNumber}
        // pathSegments includes all segments after the authority, so:
        // pathSegments[0] = "video"
        // pathSegments[1] = "{mountPoint}" (encoded)
        // pathSegments[2] = "{titleNumber}"
        // pathSegments[3] = "{chapterNumber}"
        if (pathSegments.size != 4) {
            Timber.w("Invalid URI path segments count: ${pathSegments.size}, expected 4")
            return null
        }
        
        if (pathSegments[0] != VIDEO_PATH) {
            Timber.w("Invalid URI path prefix: ${pathSegments[0]}, expected '$VIDEO_PATH'")
            return null
        }
        
        val mountPointEncoded = pathSegments[1]
        val mountPoint = try {
            Uri.decode(mountPointEncoded)
        } catch (e: Exception) {
            Timber.e(e, "Failed to decode mount point: $mountPointEncoded")
            return null
        }
        
        val titleNumber = try {
            pathSegments[2].toInt()
        } catch (e: NumberFormatException) {
            Timber.e(e, "Invalid title number: ${pathSegments[2]}")
            return null
        }
        
        val chapterNumber = try {
            pathSegments[3].toInt()
        } catch (e: NumberFormatException) {
            Timber.e(e, "Invalid chapter number: ${pathSegments[3]}")
            return null
        }
        
        // Validate parameters
        if (titleNumber < 1) {
            Timber.w("Invalid title number: $titleNumber (must be >= 1)")
            return null
        }
        
        if (chapterNumber < 1) {
            Timber.w("Invalid chapter number: $chapterNumber (must be >= 1)")
            return null
        }
        
        // Security: Validate mount point to prevent path traversal
        // Mount points typically start with "/" and should be absolute paths
        // We check for ".." to prevent path traversal, but allow "/" as mount points are absolute paths
        if (mountPoint.contains("..")) {
            Timber.w("Invalid mount point (path traversal attempt): $mountPoint")
            return null
        }
        
        // Ensure mount point is an absolute path (starts with "/")
        if (!mountPoint.startsWith("/")) {
            Timber.w("Invalid mount point (not an absolute path): $mountPoint")
            return null
        }
        
        return ParsedUriData(mountPoint, titleNumber, chapterNumber)
    }
    
    /**
     * Checks if the caller has permission to read DVD content.
     * 
     * @param uri The content URI
     * @param mode The access mode (ignored, always checks read permission)
     * @return true if permission is granted, false otherwise
     */
    private fun checkPermission(uri: Uri, mode: String): Boolean {
        val context = context ?: return false
        
        // Check if caller has READ_EXTERNAL_STORAGE permission
        // For Android 13+, check READ_MEDIA_VIDEO
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_VIDEO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        
        val result = context.checkCallingOrSelfPermission(permission)
        val hasPermission = result == PackageManager.PERMISSION_GRANTED
        
        if (!hasPermission) {
            Timber.w("Caller does not have permission: $permission")
        }
        
        return hasPermission
    }
    
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        Timber.d("openFile called for URI: $uri, mode: $mode")
        
        // Only support read mode
        if (mode != "r" && mode != "rt") {
            Timber.w("Unsupported file mode: $mode (only 'r' and 'rt' are supported)")
            throw FileNotFoundException("Only read mode is supported")
        }
        
        // Check permissions
        if (!checkPermission(uri, mode)) {
            throw SecurityException("Caller does not have permission to read DVD content")
        }
        
        // Parse URI
        val parsedData = parseUri(uri) ?: throw FileNotFoundException("Invalid URI format")
        
        // Get DVD handle from registry (lazy initialization if needed)
        val registry = getHandleRegistry()
        val handle = registry.getHandle(parsedData.mountPoint)
            ?: throw FileNotFoundException("DVD not found for mount point: ${parsedData.mountPoint}")
        
        // Validate handle
        if (handle <= 0) {
            throw FileNotFoundException("Invalid DVD handle")
        }
        
        // Extract video stream using native library
        // Note: The native implementation may not be complete yet, but we prepare the structure
        try {
            val videoStream = DvdNative.dvdExtractVideoStream(
                handle,
                parsedData.titleNumber,
                parsedData.chapterNumber
            )
            
            if (videoStream == null) {
                Timber.w("Video stream extraction returned null for title ${parsedData.titleNumber}, chapter ${parsedData.chapterNumber}")
                throw FileNotFoundException("Video stream not available")
            }
            
            // TODO: Create a ParcelFileDescriptor that streams the video data
            // For now, we need to implement a custom InputStream that reads from the native library
            // This is a placeholder - the actual implementation depends on how dvdExtractVideoStream works
            // If it returns metadata only, we need a separate method to stream the actual video bytes
            
            // Since the native implementation is not complete, we throw UnsupportedOperationException
            // Once the native code is implemented, this should create a ParcelFileDescriptor
            // that wraps an InputStream reading from the DVD video stream
            throw UnsupportedOperationException(
                "Video streaming not yet implemented. Native dvdExtractVideoStream needs to return " +
                "an InputStream or file descriptor for streaming."
            )
            
        } catch (e: UnsupportedOperationException) {
            // Re-throw as-is
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to extract video stream")
            throw FileNotFoundException("Failed to extract video stream: ${e.message}")
        }
    }
    
    override fun getType(uri: Uri): String? {
        val parsedData = parseUri(uri)
        return if (parsedData != null) {
            MIME_TYPE_VIDEO
        } else {
            null
        }
    }
    
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        // This provider doesn't support query operations
        return null
    }
    
    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        // This provider doesn't support insert operations
        throw UnsupportedOperationException("Insert not supported")
    }
    
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        // This provider doesn't support delete operations
        throw UnsupportedOperationException("Delete not supported")
    }
    
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        // This provider doesn't support update operations
        throw UnsupportedOperationException("Update not supported")
    }
    
    /**
     * Data class for parsed URI information.
     */
    private data class ParsedUriData(
        val mountPoint: String,
        val titleNumber: Int,
        val chapterNumber: Int
    )
    
    /**
     * Hilt entry point for accessing DvdHandleRegistry.
     * This is needed because ContentProvider is created before Application.
     */
    interface DvdHandleRegistryEntryPoint {
        fun dvdHandleRegistry(): DvdHandleRegistry
    }
}
