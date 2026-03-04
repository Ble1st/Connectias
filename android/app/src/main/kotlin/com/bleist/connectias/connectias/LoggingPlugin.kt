package com.bleist.connectias.connectias

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import io.flutter.plugin.common.MethodChannel
import java.io.File

/**
 * Plugin for SAF export of logs and for opening/saving USB files (open in other app, save to device).
 */
class LoggingPlugin(private val activity: Activity) : MethodChannel.MethodCallHandler {

    var pendingExportContent: String? = null
    var pendingExportResult: MethodChannel.Result? = null
    var pendingSaveFilePath: String? = null
    var pendingSaveFileResult: MethodChannel.Result? = null

    override fun onMethodCall(call: io.flutter.plugin.common.MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "exportLogsToFile" -> {
                val content = call.argument<String>("content") ?: ""
                pendingExportContent = content
                pendingExportResult = result
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TITLE, "logs_export.txt")
                }
                activity.startActivityForResult(intent, REQUEST_CREATE_FILE)
            }
            "openFileInOtherApp" -> {
                val tempPath = call.argument<String>("tempPath") ?: ""
                val mimeType = call.argument<String>("mimeType") ?: "*/*"
                if (tempPath.isEmpty()) {
                    result.success(false)
                    return
                }
                try {
                    val file = File(tempPath)
                    if (!file.exists()) {
                        result.success(false)
                        return
                    }
                    val authority = "${activity.packageName}.fileprovider"
                    val uri: Uri = FileProvider.getUriForFile(activity, authority, file)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    activity.startActivity(Intent.createChooser(intent, null))
                    result.success(true)
                } catch (e: Exception) {
                    result.success(false)
                }
            }
            "saveFileToDevice" -> {
                val tempPath = call.argument<String>("tempPath") ?: ""
                val suggestedName = call.argument<String>("suggestedName") ?: "file"
                if (tempPath.isEmpty()) {
                    result.success(false)
                    return
                }
                pendingSaveFilePath = tempPath
                pendingSaveFileResult = result
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_TITLE, suggestedName)
                }
                activity.startActivityForResult(intent, REQUEST_SAVE_FILE)
            }
            else -> result.notImplemented()
        }
    }

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_CREATE_FILE -> {
                val result = pendingExportResult
                pendingExportResult = null
                val content = pendingExportContent
                pendingExportContent = null
                if (resultCode == Activity.RESULT_OK && data?.data != null && content != null) {
                    try {
                        activity.contentResolver.openOutputStream(data.data!!)!!.use { os ->
                            os.write(content.toByteArray(Charsets.UTF_8))
                        }
                        result?.success(true)
                    } catch (e: Exception) {
                        result?.success(false)
                    }
                } else {
                    result?.success(false)
                }
            }
            REQUEST_SAVE_FILE -> {
                val result = pendingSaveFileResult
                pendingSaveFileResult = null
                val path = pendingSaveFilePath
                pendingSaveFilePath = null
                if (resultCode == Activity.RESULT_OK && data?.data != null && path != null) {
                    try {
                        val file = File(path)
                        if (file.exists()) {
                            activity.contentResolver.openOutputStream(data.data!!)!!.use { os ->
                                file.inputStream().use { it.copyTo(os) }
                            }
                            file.delete()
                        }
                        result?.success(true)
                    } catch (e: Exception) {
                        result?.success(false)
                    }
                } else {
                    result?.success(false)
                }
            }
        }
    }

    companion object {
        const val REQUEST_CREATE_FILE = 9001
        const val REQUEST_SAVE_FILE = 9002
    }
}
