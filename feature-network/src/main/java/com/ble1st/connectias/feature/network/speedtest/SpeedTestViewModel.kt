package com.ble1st.connectias.feature.network.speedtest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.feature.network.speedtest.models.SpeedTestConfig
import com.ble1st.connectias.feature.network.speedtest.models.SpeedTestPhase
import com.ble1st.connectias.feature.network.speedtest.models.SpeedTestProgress
import com.ble1st.connectias.feature.network.speedtest.models.SpeedTestResult
import com.ble1st.connectias.feature.network.speedtest.models.SpeedTestServer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for Network Speed Test functionality.
 */
@HiltViewModel
class SpeedTestViewModel @Inject constructor(
    private val speedTestProvider: SpeedTestProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpeedTestUiState())
    val uiState: StateFlow<SpeedTestUiState> = _uiState.asStateFlow()

    val history: StateFlow<List<SpeedTestResult>> = speedTestProvider.history

    private var testJob: Job? = null

    init {
        _uiState.update { it.copy(
            servers = speedTestProvider.getServers(),
            selectedServer = speedTestProvider.getServers().firstOrNull(),
            connectionType = speedTestProvider.getConnectionType()
        ) }
    }

    /**
     * Starts a speed test.
     */
    fun startTest() {
        val server = _uiState.value.selectedServer ?: return

        testJob?.cancel()
        testJob = viewModelScope.launch {
            _uiState.update { it.copy(
                phase = SpeedTestPhase.CONNECTING,
                downloadProgress = 0f,
                uploadProgress = 0f,
                currentDownloadSpeed = 0.0,
                currentUploadSpeed = 0.0,
                error = null,
                lastResult = null
            ) }

            speedTestProvider.runFullTest(server).collect { progress ->
                handleProgress(progress)
            }
        }
    }

    /**
     * Stops the current speed test.
     */
    fun stopTest() {
        testJob?.cancel()
        testJob = null
        _uiState.update { it.copy(phase = SpeedTestPhase.IDLE) }
    }

    /**
     * Handles progress updates from the speed test.
     */
    private fun handleProgress(progress: SpeedTestProgress) {
        when (progress) {
            is SpeedTestProgress.Connecting -> {
                _uiState.update { it.copy(phase = SpeedTestPhase.CONNECTING) }
            }

            is SpeedTestProgress.MeasuringLatency -> {
                _uiState.update { it.copy(
                    phase = SpeedTestPhase.MEASURING_LATENCY,
                    currentLatency = progress.currentLatency
                ) }
            }

            is SpeedTestProgress.DownloadProgress -> {
                _uiState.update { it.copy(
                    phase = SpeedTestPhase.DOWNLOADING,
                    downloadProgress = progress.progress,
                    currentDownloadSpeed = progress.currentSpeed,
                    bytesDownloaded = progress.bytesTransferred
                ) }
            }

            is SpeedTestProgress.UploadProgress -> {
                _uiState.update { it.copy(
                    phase = SpeedTestPhase.UPLOADING,
                    uploadProgress = progress.progress,
                    currentUploadSpeed = progress.currentSpeed,
                    bytesUploaded = progress.bytesTransferred
                ) }
            }

            is SpeedTestProgress.Completed -> {
                _uiState.update { it.copy(
                    phase = SpeedTestPhase.COMPLETED,
                    lastResult = progress.result,
                    downloadProgress = 1f,
                    uploadProgress = 1f
                ) }
                Timber.d("Speed test completed: ${progress.result}")
            }

            is SpeedTestProgress.Error -> {
                _uiState.update { it.copy(
                    phase = SpeedTestPhase.ERROR,
                    error = progress.message
                ) }
                Timber.e("Speed test error: ${progress.message}")
            }
        }
    }

    /**
     * Selects a server for testing.
     */
    fun selectServer(server: SpeedTestServer) {
        _uiState.update { it.copy(selectedServer = server) }
    }

    /**
     * Clears test history.
     */
    fun clearHistory() {
        speedTestProvider.clearHistory()
    }

    /**
     * Clears the error state.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Resets to idle state.
     */
    fun reset() {
        _uiState.update { it.copy(
            phase = SpeedTestPhase.IDLE,
            downloadProgress = 0f,
            uploadProgress = 0f,
            currentDownloadSpeed = 0.0,
            currentUploadSpeed = 0.0,
            lastResult = null,
            error = null
        ) }
    }

    override fun onCleared() {
        super.onCleared()
        testJob?.cancel()
    }
}

/**
 * UI state for Speed Test screen.
 */
data class SpeedTestUiState(
    val phase: SpeedTestPhase = SpeedTestPhase.IDLE,
    val servers: List<SpeedTestServer> = emptyList(),
    val selectedServer: SpeedTestServer? = null,
    val connectionType: String = "Unknown",
    val downloadProgress: Float = 0f,
    val uploadProgress: Float = 0f,
    val currentDownloadSpeed: Double = 0.0,
    val currentUploadSpeed: Double = 0.0,
    val currentLatency: Long = 0,
    val bytesDownloaded: Long = 0,
    val bytesUploaded: Long = 0,
    val lastResult: SpeedTestResult? = null,
    val error: String? = null
) {
    val isRunning: Boolean
        get() = phase in listOf(
            SpeedTestPhase.CONNECTING,
            SpeedTestPhase.MEASURING_LATENCY,
            SpeedTestPhase.DOWNLOADING,
            SpeedTestPhase.UPLOADING
        )

    val canStart: Boolean
        get() = phase == SpeedTestPhase.IDLE || phase == SpeedTestPhase.COMPLETED || phase == SpeedTestPhase.ERROR
}
