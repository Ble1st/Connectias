package com.ble1st.connectias.plugin;
import android.net.Uri;

/**
 * Callback interface for SAF file operation results
 */
interface ISAFResultCallback {
    /**
     * Called when file was successfully created via SAF
     * @param uri The URI of the created file
     */
    oneway void onSuccess(in android.net.Uri uri);
    
    /**
     * Called when file was successfully opened via SAF (with content)
     * @param uri The URI of the opened file
     * @param content File content as ByteArray
     * @param fileName The display name of the file (extracted in Main Process)
     */
    oneway void onSuccessWithContent(in android.net.Uri uri, in byte[] content, String fileName);
    
    /**
     * Called when file operation failed
     * @param errorMessage Error message describing what went wrong
     */
    oneway void onError(String errorMessage);
}
