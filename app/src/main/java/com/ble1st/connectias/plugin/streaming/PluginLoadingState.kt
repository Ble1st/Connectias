package com.ble1st.connectias.plugin.streaming

/**
 * Sealed class representing different states of plugin loading
 */
sealed class PluginLoadingState {
    abstract val progress: Float
    abstract val statusMessage: String
    
    data class Downloading(
        override val progress: Float,
        val stage: String,
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val downloadSpeed: Float = 0f // bytes per second
    ) : PluginLoadingState() {
        override val statusMessage: String = "Downloading: $stage ${(progress * 100).toInt()}%"
    }
    
    data class Installing(
        val currentStep: Int,
        val totalSteps: Int,
        val currentOperation: String,
        val stepProgress: Float = 0f
    ) : PluginLoadingState() {
        override val progress: Float = (currentStep.toFloat() / totalSteps) * 0.7f + (stepProgress * 0.3f)
        override val statusMessage: String = "Installing ($currentStep/$totalSteps): $currentOperation"
    }
    
    data class Verifying(
        override val progress: Float,
        val verificationStep: String,
        val currentCheck: String
    ) : PluginLoadingState() {
        override val statusMessage: String = "Verifying: $verificationStep"
    }
    
    data class Extracting(
        override val progress: Float,
        val currentFile: String,
        val filesExtracted: Int,
        val totalFiles: Int
    ) : PluginLoadingState() {
        override val statusMessage: String = "Extracting: $currentFile ($filesExtracted/$totalFiles)"
    }
    
    object Completed : PluginLoadingState() {
        override val progress: Float = 1f
        override val statusMessage: String = "Installation completed"
    }
    
    data class Failed(
        val error: Throwable,
        val stage: String,
        val canRetry: Boolean = true
    ) : PluginLoadingState() {
        override val progress: Float = 0f
        override val statusMessage: String = "Failed during $stage: ${error.message ?: "Unknown error"}"
    }
    
    object Paused : PluginLoadingState() {
        override val progress: Float = 0f
        override val statusMessage: String = "Download paused"
    }
    
    data class Cancelled(
        val reason: String
    ) : PluginLoadingState() {
        override val progress: Float = 0f
        override val statusMessage: String = "Cancelled: $reason"
    }
    
    /**
     * Extension properties for convenient state checking
     */
    val isLoading: Boolean
        get() = this is Downloading || this is Installing || this is Verifying || this is Extracting

    val isTerminal: Boolean
        get() = this is Completed || this is Failed || this is Cancelled
}
