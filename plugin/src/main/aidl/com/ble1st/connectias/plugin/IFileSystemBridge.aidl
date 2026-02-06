package com.ble1st.connectias.plugin;

import android.os.ParcelFileDescriptor;
import com.ble1st.connectias.plugin.ISAFResultCallback;

/**
 * AIDL interface for file system access in isolated process
 * Provides controlled file operations for plugins
 */
interface IFileSystemBridge {
    /**
     * Create a new file with specified mode
     * @param pluginId Plugin identifier for permission checking
     * @param sessionToken Session token for identity verification (prevents pluginId spoofing)
     * @param path Relative path within plugin's sandbox directory
     * @param mode File creation mode (e.g., 0600 for private read/write)
     * @return ParcelFileDescriptor for the created file, or null if failed
     */
    ParcelFileDescriptor createFile(String pluginId, long sessionToken, String path, int mode);
    
    /**
     * Open an existing file
     * @param pluginId Plugin identifier for permission checking
     * @param sessionToken Session token for identity verification (prevents pluginId spoofing)
     * @param path Relative path within plugin's sandbox directory
     * @param mode File open mode (e.g., ParcelFileDescriptor.MODE_READ_WRITE)
     * @return ParcelFileDescriptor for the opened file, or null if failed
     */
    ParcelFileDescriptor openFile(String pluginId, long sessionToken, String path, int mode);
    
    /**
     * Delete a file
     * @param pluginId Plugin identifier for permission checking
     * @param sessionToken Session token for identity verification (prevents pluginId spoofing)
     * @param path Relative path within plugin's sandbox directory
     * @return true if deletion was successful, false otherwise
     */
    boolean deleteFile(String pluginId, long sessionToken, String path);
    
    /**
     * Check if a file exists
     * @param pluginId Plugin identifier for permission checking
     * @param sessionToken Session token for identity verification (prevents pluginId spoofing)
     * @param path Relative path within plugin's sandbox directory
     * @return true if file exists, false otherwise
     */
    boolean fileExists(String pluginId, long sessionToken, String path);
    
    /**
     * List files in a directory
     * @param pluginId Plugin identifier for permission checking
     * @param sessionToken Session token for identity verification (prevents pluginId spoofing)
     * @param path Relative path within plugin's sandbox directory
     * @return Array of file names, or empty array if directory doesn't exist
     */
    String[] listFiles(String pluginId, long sessionToken, String path);
    
    /**
     * Get file size
     * @param pluginId Plugin identifier for permission checking
     * @param sessionToken Session token for identity verification (prevents pluginId spoofing)
     * @param path Relative path within plugin's sandbox directory
     * @return File size in bytes, or -1 if file doesn't exist
     */
    long getFileSize(String pluginId, long sessionToken, String path);
    
    /**
     * Create a file via Storage Access Framework (SAF)
     * Opens Android file picker for user to select save location
     * @param pluginId Plugin identifier for permission checking
     * @param sessionToken Session token for identity verification (prevents pluginId spoofing)
     * @param fileName Suggested file name (e.g., "test.txt")
     * @param mimeType MIME type (e.g., "text/plain")
     * @param content File content as ByteArray
     * @param callback Callback for async result (Uri or error)
     */
    void createFileViaSAF(String pluginId, long sessionToken, String fileName, String mimeType, in byte[] content, ISAFResultCallback callback);
    
    /**
     * Open a file via Storage Access Framework (SAF)
     * Opens Android file picker for user to select a file to read
     * @param pluginId Plugin identifier for permission checking
     * @param sessionToken Session token for identity verification (prevents pluginId spoofing)
     * @param mimeType MIME type filter (e.g., "text/plain" or "all files")
     * @param callback Callback for async result (Uri or error)
     */
    void openFileViaSAF(String pluginId, long sessionToken, String mimeType, ISAFResultCallback callback);
}
