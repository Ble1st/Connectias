package com.ble1st.connectias.feature.deviceinfo.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * ViewModel for Storage Analyzer.
 */
@HiltViewModel
class StorageAnalyzerViewModel @Inject constructor(
    private val storageAnalyzerProvider: StorageAnalyzerProvider
) : ViewModel() {

    private val _storageState = MutableStateFlow<StorageState>(StorageState.Idle)
    val storageState: StateFlow<StorageState> = _storageState.asStateFlow()

    /**
     * Gets storage information.
     */
    fun getStorageInfo() {
        viewModelScope.launch {
            _storageState.value = StorageState.Loading
            val info = storageAnalyzerProvider.getStorageInfo()
            _storageState.value = StorageState.Info(info)
        }
    }

    /**
     * Finds large files.
     */
    fun findLargeFiles(minSizeMB: Int = 10) {
        viewModelScope.launch {
            _storageState.value = StorageState.Loading
            val minSizeBytes = minSizeMB * 1024L * 1024L
            val largeFiles = storageAnalyzerProvider.findLargeFiles(
                minSizeBytes = minSizeBytes
            )
            _storageState.value = StorageState.LargeFiles(largeFiles)
        }
    }

    /**
     * Resets the state to idle.
     */
    fun resetState() {
        _storageState.value = StorageState.Idle
    }
}

/**
 * State representation for storage operations.
 */
sealed class StorageState {
    object Idle : StorageState()
    object Loading : StorageState()
    data class Info(val info: StorageInfo) : StorageState()
    data class LargeFiles(val files: List<LargeFile>) : StorageState()
    data class Error(val message: String) : StorageState()
}

