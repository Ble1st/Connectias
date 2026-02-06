package com.ble1st.connectias.core.plugin

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.ble1st.connectias.plugin.ISAFResultCallback
import timber.log.Timber
import java.io.InputStream
import java.io.OutputStream

/**
 * Transparent Activity for handling Storage Access Framework (SAF) file operations.
 * 
 * This Activity is started by FileSystemBridgeService when a plugin requests
 * to save or open a file via SAF. It handles the SAF dialog and performs
 * the file operation (write or read).
 * 
 * Architecture:
 * - Service cannot have ActivityResultLauncher → Activity has the launcher
 * - Callback is passed as IBinder in Intent extras
 * - Activity performs file operation and calls callback via AIDL
 * 
 * Uses ComponentActivity (not AppCompatActivity) to support ActivityResultLauncher
 * without requiring AppCompat theme.
 */
class SAFFilePickerActivity : ComponentActivity() {
    
    private var callback: ISAFResultCallback? = null
    private var fileName: String? = null
    private var mimeType: String? = null
    private var content: ByteArray? = null
    private var operationType: String? = null // "CREATE" or "OPEN"
    
    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            writeFileToUri(uri)
        } else {
            // User cancelled or error occurred
            callback?.onError("User cancelled file selection or error occurred")
            finish()
        }
    }
    
    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            readFileFromUri(uri)
        } else {
            // User cancelled or error occurred
            callback?.onError("User cancelled file selection or error occurred")
            finish()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Extract Intent extras
        // getBinder is available on Bundle in API 18+ (we're on API 33+)
        val extras = intent.extras
        val callbackBinder: IBinder? = extras?.getBinder(EXTRA_CALLBACK)
        
        val extractedFileName = intent.getStringExtra(EXTRA_FILE_NAME)
        val extractedMimeType = intent.getStringExtra(EXTRA_MIME_TYPE)
        val extractedContent = intent.getByteArrayExtra(EXTRA_CONTENT)
        val extractedOperationType = intent.getStringExtra(EXTRA_OPERATION_TYPE) ?: OPERATION_CREATE
        
        // Store in class properties
        fileName = extractedFileName
        mimeType = extractedMimeType
        content = extractedContent
        operationType = extractedOperationType
        
        if (callbackBinder != null) {
            callback = ISAFResultCallback.Stub.asInterface(callbackBinder)
        }
        
        // Validate callback
        if (callback == null) {
            Timber.e("[SAF_PICKER] Missing callback")
            finish()
            return
        }
        
        // Route to appropriate operation
        when (extractedOperationType) {
            OPERATION_CREATE -> {
                // Validate required parameters for CREATE
                if (extractedFileName == null || extractedMimeType == null || extractedContent == null) {
                    Timber.e("[SAF_PICKER] Missing required parameters for CREATE")
                    callback?.onError("Missing required parameters")
                    finish()
                    return
                }
                
                // Validate content size (max 10MB)
                if (extractedContent.size > MAX_CONTENT_SIZE) {
                    Timber.e("[SAF_PICKER] Content too large: ${extractedContent.size} bytes (max: $MAX_CONTENT_SIZE)")
                    callback?.onError("File content too large (max ${MAX_CONTENT_SIZE / 1024 / 1024}MB)")
                    finish()
                    return
                }
                
                Timber.d("[SAF_PICKER] Starting SAF CREATE dialog for file: $extractedFileName (${extractedContent.size} bytes, mimeType: $extractedMimeType)")
                
                // Launch SAF dialog with suggested file name
                // CreateDocument contract accepts file name as parameter
                createDocumentLauncher.launch(extractedFileName)
            }
            
            OPERATION_OPEN -> {
                // Validate required parameters for OPEN
                val mimeTypeFilter = extractedMimeType ?: "*/*"
                
                Timber.d("[SAF_PICKER] Starting SAF OPEN dialog with mimeType filter: $mimeTypeFilter")
                
                // Launch SAF dialog with MIME type filter
                // OpenDocument contract accepts MIME type array as parameter
                openDocumentLauncher.launch(arrayOf(mimeTypeFilter))
            }
            
            else -> {
                Timber.e("[SAF_PICKER] Unknown operation type: $extractedOperationType")
                callback?.onError("Unknown operation type: $extractedOperationType")
                finish()
            }
        }
    }
    
    /**
     * Writes file content to the selected URI
     */
    private fun writeFileToUri(uri: Uri) {
        val contentToWrite = content ?: run {
            Timber.e("[SAF_PICKER] Content is null")
            callback?.onError("Content is null")
            finish()
            return
        }
        
        try {
            contentResolver.openOutputStream(uri, "w")?.use { outputStream: OutputStream ->
                outputStream.write(contentToWrite)
                outputStream.flush()
                
                Timber.d("[SAF_PICKER] Successfully wrote ${contentToWrite.size} bytes to $uri")
                callback?.onSuccess(uri)
            } ?: run {
                Timber.e("[SAF_PICKER] Failed to open output stream for URI: $uri")
                callback?.onError("Failed to open output stream")
            }
        } catch (e: Exception) {
            Timber.e(e, "[SAF_PICKER] Error writing file to URI: $uri")
            callback?.onError("Error writing file: ${e.message}")
        } finally {
            finish()
        }
    }
    
    /**
     * Reads file content from the selected URI
     */
    private fun readFileFromUri(uri: Uri) {
        try {
            // Take persistent URI permission (required for SAF)
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, takeFlags)
            
            // Read file content
            contentResolver.openInputStream(uri)?.use { inputStream: InputStream ->
                // Read content into ByteArray
                val contentBytes = inputStream.readBytes()
                
                // Validate file size (max 10MB)
                if (contentBytes.size > MAX_CONTENT_SIZE) {
                    Timber.e("[SAF_PICKER] File too large: ${contentBytes.size} bytes (max: $MAX_CONTENT_SIZE)")
                    callback?.onError("File too large (max ${MAX_CONTENT_SIZE / 1024 / 1024}MB)")
                    finish()
                    return
                }
                
                Timber.d("[SAF_PICKER] Successfully read ${contentBytes.size} bytes from $uri")
                
                // Extract filename in Main Process (isolated processes cannot use ContentResolver.query)
                val fileName = try {
                    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { cursor ->
                            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (idx >= 0 && cursor.moveToFirst()) {
                                cursor.getString(idx)
                            } else {
                                // Fallback: extract from URI path
                                uri.lastPathSegment ?: "file_${System.currentTimeMillis()}"
                            }
                        } ?: run {
                            // Fallback: extract from URI path
                            uri.lastPathSegment ?: "file_${System.currentTimeMillis()}"
                        }
                } catch (e: Exception) {
                    Timber.w(e, "[SAF_PICKER] Failed to extract filename, using fallback")
                    // Fallback: extract from URI path
                    uri.lastPathSegment ?: "file_${System.currentTimeMillis()}"
                }
                
                Timber.d("[SAF_PICKER] Extracted filename: $fileName")
                
                // Return URI, content, and filename via callback
                // Content is read here in Main Process where we have URI permission
                val callbackToUse = callback
                if (callbackToUse == null) {
                    Timber.e("[SAF_PICKER] Callback is null, cannot return file content")
                    finish()
                } else {
                    try {
                        Timber.d("[SAF_PICKER] Calling onSuccessWithContent with ${contentBytes.size} bytes, fileName=$fileName")
                        callbackToUse.onSuccessWithContent(uri, contentBytes, fileName)
                        Timber.d("[SAF_PICKER] Successfully called onSuccessWithContent callback")
                        // Give the callback time to be transmitted (oneway methods are async)
                        // Small delay to ensure callback is sent before Activity finishes
                        Handler(Looper.getMainLooper()).postDelayed({
                            Timber.d("[SAF_PICKER] Finishing activity after callback transmission")
                            finish()
                        }, 100) // 100ms delay to ensure callback is transmitted
                    } catch (e: Exception) {
                        Timber.e(e, "[SAF_PICKER] Error calling onSuccessWithContent callback: ${e.javaClass.simpleName}")
                        try {
                            callbackToUse.onError("Error returning file content: ${e.message ?: e.javaClass.simpleName}")
                            finish()
                        } catch (e2: Exception) {
                            Timber.e(e2, "[SAF_PICKER] Failed to call onError callback")
                            finish()
                        }
                    }
                }
            } ?: run {
                Timber.e("[SAF_PICKER] Failed to open input stream for URI: $uri")
                callback?.onError("Failed to open input stream")
                finish()
            }
        } catch (e: Exception) {
            Timber.e(e, "[SAF_PICKER] Error reading file from URI: $uri")
            callback?.onError("Error reading file: ${e.message}")
            finish()
        }
    }
    
    companion object {
        const val EXTRA_CALLBACK = "callback"
        const val EXTRA_FILE_NAME = "fileName"
        const val EXTRA_MIME_TYPE = "mimeType"
        const val EXTRA_CONTENT = "content"
        const val EXTRA_OPERATION_TYPE = "operationType"
        const val EXTRA_MIME_TYPE_FILTER = "mimeTypeFilter"
        
        const val OPERATION_CREATE = "CREATE"
        const val OPERATION_OPEN = "OPEN"
        
        // Maximum content size: 10MB
        private const val MAX_CONTENT_SIZE = 10 * 1024 * 1024
    }
}
