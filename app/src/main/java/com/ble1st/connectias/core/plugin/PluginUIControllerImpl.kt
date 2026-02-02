// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.core.plugin

import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Base64
import com.ble1st.connectias.core.plugin.ui.UIStateDiffer
import com.ble1st.connectias.plugin.IFileSystemBridge
import com.ble1st.connectias.plugin.ui.IPluginUIController
import com.ble1st.connectias.plugin.ui.UIEventParcel
import com.ble1st.connectias.plugin.ui.UIStateParcel
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Implementation of IPluginUIController in Sandbox Process.
 *
 * Three-Process Architecture:
 * - Sandbox Process: Runs plugin business logic, sends UI state updates
 * - UI Process: Renders UI based on state from sandbox
 * - Main Process: Orchestrates lifecycle
 *
 * This controller is used by plugins to send UI updates to the UI Process.
 * It runs in the isolated sandbox process and forwards all calls to the
 * UI Process via AIDL IPC.
 */
class PluginUIControllerImpl : IPluginUIController.Stub() {

    // Reference to the actual UI controller in UI Process
    private var remoteUIController: IPluginUIController? = null

    // Optional bridge to persist large UI blobs (e.g. images) without Binder overflows.
    private var fileSystemBridge: IFileSystemBridge? = null

    // Main-process session tokens (per plugin) used for validating bridge calls in main process.
    private val ipcSessionTokens = ConcurrentHashMap<String, Long>()

    // State cache for debugging and recovery
    private val stateCache = mutableMapOf<String, UIStateParcel>()

    companion object {
        /**
         * Keep this comfortably below the Binder transaction limit (~1MB) to account for overhead.
         * Large images should be offloaded to file via FileSystemBridge.
         */
        private const val MAX_INLINE_IMAGE_BYTES = 128 * 1024

        private val IMAGE_VALUE_KEYS = listOf(
            "base64Data",
            "data",
            "base64",
            "imageData",
            "image"
        )
    }

    /**
     * Sets the remote UI controller (called by PluginSandboxService).
     *
     * @param controller IPluginUIController from UI Process
     */
    fun setRemoteController(controller: IPluginUIController) {
        this.remoteUIController = controller
        Timber.i("[SANDBOX] Remote UI controller connected")
    }

    /**
     * Sets file system bridge (called by PluginSandboxService).
     * This enables safe offloading of large UI payloads (e.g., images) to disk.
     */
    fun setFileSystemBridge(bridge: IFileSystemBridge?) {
        this.fileSystemBridge = bridge
        Timber.d("[SANDBOX] File system bridge updated for UI controller")
    }

    /**
     * Registers the main-process session token for a plugin so sandbox->main IPC can be verified.
     */
    fun registerPluginSession(pluginId: String, sessionToken: Long) {
        ipcSessionTokens[pluginId] = sessionToken
    }

    override fun updateUIState(pluginId: String, state: UIStateParcel) {
        Timber.d("[SANDBOX] Update UI state: $pluginId -> ${state.screenId}")

        // Performance Optimization: State Diffing
        val previousState = stateCache[pluginId]
        val diff = UIStateDiffer.diff(previousState, state)

        // Log diff statistics for monitoring
        UIStateDiffer.logDiffStats(pluginId, diff)

        // Only send update if state actually changed
        if (!UIStateDiffer.shouldUpdate(diff)) {
            Timber.v("[SANDBOX] State unchanged for $pluginId - skipping IPC")
            return
        }

        // Cache new state for next diff
        stateCache[pluginId] = state

        // Create optimized update (currently returns full state, future: partial updates)
        val optimizedState = UIStateDiffer.createOptimizedUpdate(state, diff)

        // SECURITY/PERF: Prevent Binder TransactionTooLargeException by offloading large blobs to file.
        val stateForIpc = sanitizeStateForIpc(pluginId, optimizedState)

        // Forward to UI Process
        try {
            remoteUIController?.updateUIState(pluginId, stateForIpc)
                ?: Timber.w("[SANDBOX] Remote UI controller not connected - state update dropped")
        } catch (e: Exception) {
            Timber.e(e, "[SANDBOX] Failed to update UI state for $pluginId")
        }
    }

    private fun sanitizeStateForIpc(pluginId: String, state: UIStateParcel): UIStateParcel {
        return try {
            val components = state.components
            val sanitizedComponents = components
                ?.map { sanitizeComponentForIpc(pluginId, it) }
                ?.toTypedArray()

            UIStateParcel().apply {
                screenId = state.screenId
                title = state.title
                data = state.data?.let { Bundle(it) } ?: Bundle()
                this.components = sanitizedComponents
                timestamp = state.timestamp
            }
        } catch (e: Exception) {
            // Fail-safe: if sanitization fails, send original state and rely on existing exception handling.
            Timber.w(e, "[SANDBOX] Failed to sanitize UI state for IPC (plugin=$pluginId)")
            state
        }
    }

    private fun sanitizeComponentForIpc(
        pluginId: String,
        component: com.ble1st.connectias.plugin.ui.UIComponentParcel
    ): com.ble1st.connectias.plugin.ui.UIComponentParcel {
        val sanitizedProps = try {
            val propsCopy = component.properties?.let { Bundle(it) } ?: Bundle()
            sanitizeImagePropertiesIfNeeded(pluginId, component.type, propsCopy)
        } catch (e: Exception) {
            Timber.w(e, "[SANDBOX] Failed to sanitize component properties (plugin=$pluginId, id=${component.id})")
            component.properties
        }

        val sanitizedChildren = component.children
            ?.map { sanitizeComponentForIpc(pluginId, it) }
            ?.toTypedArray()

        return com.ble1st.connectias.plugin.ui.UIComponentParcel().apply {
            id = component.id
            type = component.type
            properties = sanitizedProps
            children = sanitizedChildren
        }
    }

    private fun sanitizeImagePropertiesIfNeeded(
        pluginId: String,
        componentType: String?,
        props: Bundle
    ): Bundle {
        // Only attempt offload for explicit IMAGE components or when common image keys are present.
        val looksLikeImage = componentType == "IMAGE" || IMAGE_VALUE_KEYS.any { props.containsKey(it) }
        if (!looksLikeImage) return props

        val extracted = extractImageBytesFromProperties(props) ?: return props
        val (bytes, ext, usedKey) = extracted

        if (bytes.size <= MAX_INLINE_IMAGE_BYTES) return props

        val relPath = writeBlobToPluginFile(pluginId, bytes, ext) ?: return props

        // Replace large inline image data with file reference.
        props.putString("filePath", relPath)

        // Remove the potentially huge inline keys to keep Binder payload small.
        IMAGE_VALUE_KEYS.forEach { props.remove(it) }

        // If url/src were used as a data-URI carrier, drop them too.
        if (usedKey == "url") props.remove("url")
        if (usedKey == "src") props.remove("src")

        return props
    }

    private data class ExtractedImage(
        val bytes: ByteArray,
        val extension: String,
        val usedKey: String
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ExtractedImage

            if (!bytes.contentEquals(other.bytes)) return false
            if (extension != other.extension) return false
            if (usedKey != other.usedKey) return false

            return true
        }

        override fun hashCode(): Int {
            var result = bytes.contentHashCode()
            result = 31 * result + extension.hashCode()
            result = 31 * result + usedKey.hashCode()
            return result
        }
    }

    private fun extractImageBytesFromProperties(props: Bundle): ExtractedImage? {
        // 1) Prefer ByteArray to avoid Base64 overhead.
        for (k in IMAGE_VALUE_KEYS) {
            val v = props.get(k)
            if (v is ByteArray && v.isNotEmpty()) {
                return ExtractedImage(v, ".bin", k)
            }
        }

        // 2) Try Base64 strings (including data URI).
        val candidateKeys = IMAGE_VALUE_KEYS + listOf("url", "src")
        for (k in candidateKeys) {
            val s = props.getString(k) ?: continue
            if (s.isBlank()) continue

            val (cleanBase64, ext) = if (s.startsWith("data:", ignoreCase = true)) {
                val header = s.substringBefore("base64,", missingDelimiterValue = "")
                val guessedExt = when {
                    header.contains("image/png", ignoreCase = true) -> ".png"
                    header.contains("image/jpeg", ignoreCase = true) || header.contains("image/jpg", ignoreCase = true) -> ".jpg"
                    header.contains("image/webp", ignoreCase = true) -> ".webp"
                    else -> ".bin"
                }
                s.substringAfter("base64,", missingDelimiterValue = "") to guessedExt
            } else {
                s to ".bin"
            }

            if (cleanBase64.isBlank()) continue

            val decoded = try {
                Base64.decode(cleanBase64, Base64.DEFAULT)
            } catch (_: Exception) {
                null
            } ?: continue

            if (decoded.isNotEmpty()) {
                return ExtractedImage(decoded, ext, k)
            }
        }

        return null
    }

    private fun writeBlobToPluginFile(pluginId: String, bytes: ByteArray, extension: String): String? {
        val bridge = fileSystemBridge ?: return null
        val token = ipcSessionTokens[pluginId] ?: return null

        val relPath = "ui_blobs/${UUID.randomUUID()}$extension"
        return try {
            val pfd = bridge.openFile(
                pluginId,
                token,
                relPath,
                ParcelFileDescriptor.MODE_CREATE or
                    ParcelFileDescriptor.MODE_TRUNCATE or
                    ParcelFileDescriptor.MODE_WRITE_ONLY
            ) ?: return null

            ParcelFileDescriptor.AutoCloseOutputStream(pfd).use { out ->
                out.write(bytes)
                out.flush()
            }

            relPath
        } catch (e: Exception) {
            Timber.w(e, "[SANDBOX] Failed to offload UI blob to file (plugin=$pluginId, path=$relPath)")
            null
        }
    }

    override fun showDialog(
        pluginId: String,
        title: String,
        message: String,
        dialogType: Int
    ) {
        Timber.d("[SANDBOX] Show dialog: $pluginId -> $title")

        try {
            remoteUIController?.showDialog(pluginId, title, message, dialogType)
                ?: Timber.w("[SANDBOX] Remote UI controller not connected - dialog request dropped")
        } catch (e: Exception) {
            Timber.e(e, "[SANDBOX] Failed to show dialog for $pluginId")
        }
    }

    override fun showToast(pluginId: String, message: String, duration: Int) {
        Timber.d("[SANDBOX] Show toast: $pluginId -> $message")

        try {
            remoteUIController?.showToast(pluginId, message, duration)
                ?: Timber.w("[SANDBOX] Remote UI controller not connected - toast request dropped")
        } catch (e: Exception) {
            Timber.e(e, "[SANDBOX] Failed to show toast for $pluginId")
        }
    }

    override fun navigateToScreen(pluginId: String, screenId: String, args: Bundle) {
        Timber.d("[SANDBOX] Navigate: $pluginId -> $screenId")

        try {
            remoteUIController?.navigateToScreen(pluginId, screenId, args)
                ?: Timber.w("[SANDBOX] Remote UI controller not connected - navigation request dropped")
        } catch (e: Exception) {
            Timber.e(e, "[SANDBOX] Failed to navigate for $pluginId")
        }
    }

    override fun navigateBack(pluginId: String) {
        Timber.d("[SANDBOX] Navigate back: $pluginId")

        try {
            remoteUIController?.navigateBack(pluginId)
                ?: Timber.w("[SANDBOX] Remote UI controller not connected - back navigation dropped")
        } catch (e: Exception) {
            Timber.e(e, "[SANDBOX] Failed to navigate back for $pluginId")
        }
    }

    override fun setLoading(pluginId: String, loading: Boolean, message: String?) {
        Timber.d("[SANDBOX] Set loading: $pluginId -> $loading")

        try {
            remoteUIController?.setLoading(pluginId, loading, message)
                ?: Timber.w("[SANDBOX] Remote UI controller not connected - loading state dropped")
        } catch (e: Exception) {
            Timber.e(e, "[SANDBOX] Failed to set loading for $pluginId")
        }
    }

    override fun sendUIEvent(pluginId: String, event: UIEventParcel) {
        Timber.d("[SANDBOX] Send UI event: $pluginId -> ${event.eventType}")

        try {
            remoteUIController?.sendUIEvent(pluginId, event)
                ?: Timber.w("[SANDBOX] Remote UI controller not connected - UI event dropped")
        } catch (e: Exception) {
            Timber.e(e, "[SANDBOX] Failed to send UI event for $pluginId")
        }
    }

    /**
     * Gets cached state for a plugin (for recovery/debugging).
     *
     * @param pluginId Plugin identifier
     * @return Cached UI state or null
     */
    fun getCachedState(pluginId: String): UIStateParcel? {
        return stateCache[pluginId]
    }

    /**
     * Clears cached state for a plugin.
     *
     * @param pluginId Plugin identifier
     */
    fun clearCachedState(pluginId: String) {
        stateCache.remove(pluginId)
        Timber.d("[SANDBOX] Cleared cached state for $pluginId")
    }

    /**
     * Disconnects remote controller.
     */
    fun disconnect() {
        remoteUIController = null
        stateCache.clear()
        Timber.i("[SANDBOX] UI controller disconnected")
    }

    /**
     * Checks if remote controller is connected.
     */
    fun isConnected(): Boolean = remoteUIController != null

    /**
     * Resends cached UI state for a plugin.
     * This is useful when a fragment is recreated after being destroyed.
     * Forces update even if state hasn't changed (bypasses state diffing).
     *
     * @param pluginId Plugin identifier
     */
    fun resendCachedState(pluginId: String) {
        val cachedState = stateCache[pluginId]
        if (cachedState != null) {
            Timber.d("[SANDBOX] Resending cached UI state for plugin: $pluginId -> ${cachedState.screenId}")
            try {
                // Force send cached state (bypass state diffing)
                remoteUIController?.updateUIState(pluginId, cachedState)
                    ?: Timber.w("[SANDBOX] Remote UI controller not connected - cached state resend dropped")
            } catch (e: Exception) {
                Timber.e(e, "[SANDBOX] Failed to resend cached UI state for $pluginId")
            }
        } else {
            Timber.d("[SANDBOX] No cached state found for plugin: $pluginId - cannot resend")
        }
    }
}
