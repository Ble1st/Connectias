package com.ble1st.connectias.feature.usb.browser

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.feature.usb.browser.models.BrowserNavigationState
import com.ble1st.connectias.feature.usb.browser.models.FileOperationResult
import com.ble1st.connectias.feature.usb.browser.models.FilePreview
import com.ble1st.connectias.feature.usb.browser.models.SortOrder
import com.ble1st.connectias.feature.usb.browser.models.UsbFileEntry
import com.ble1st.connectias.feature.usb.browser.models.UsbStorageDevice
import com.ble1st.connectias.feature.usb.browser.models.ViewMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * ViewModel for USB Storage Browser functionality.
 */
@HiltViewModel
class UsbStorageBrowserViewModel @Inject constructor(
    private val usbStorageProvider: UsbStorageBrowserProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(UsbBrowserUiState())
    val uiState: StateFlow<UsbBrowserUiState> = _uiState.asStateFlow()

    val connectedDevices: StateFlow<List<UsbStorageDevice>> = usbStorageProvider.connectedDevices
    val currentDevice: StateFlow<UsbStorageDevice?> = usbStorageProvider.currentDevice

    init {
        scanDevices()
    }

    /**
     * Scans for USB devices.
     */
    fun scanDevices() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            usbStorageProvider.scanDevices()
            _uiState.update { it.copy(isScanning = false) }
        }
    }

    /**
     * Mounts a device.
     */
    fun mountDevice(device: UsbStorageDevice) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            usbStorageProvider.mountDevice(device).fold(
                onSuccess = { mountedDevice ->
                    loadDirectory("/")
                },
                onFailure = { error ->
                    _uiState.update { it.copy(
                        isLoading = false,
                        error = "Failed to mount device: ${error.message}"
                    ) }
                }
            )
        }
    }

    /**
     * Unmounts the current device.
     */
    fun unmountDevice() {
        usbStorageProvider.unmount()
        _uiState.update { it.copy(
            files = emptyList(),
            navigationState = BrowserNavigationState()
        ) }
    }

    /**
     * Loads a directory.
     */
    fun loadDirectory(path: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            val state = _uiState.value.navigationState
            usbStorageProvider.listDirectory(
                path = path,
                sortOrder = state.sortOrder,
                showHidden = state.showHiddenFiles
            ).fold(
                onSuccess = { files ->
                    _uiState.update { it.copy(
                        isLoading = false,
                        files = files,
                        navigationState = state.copy(
                            currentPath = path,
                            pathStack = if (path == "/") listOf("/") 
                                       else state.pathStack + path
                        )
                    ) }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(
                        isLoading = false,
                        error = "Failed to load directory: ${error.message}"
                    ) }
                }
            )
        }
    }

    /**
     * Navigates into a directory.
     */
    fun navigateInto(entry: UsbFileEntry) {
        if (entry.isDirectory) {
            loadDirectory(entry.path)
        } else {
            showFilePreview(entry)
        }
    }

    /**
     * Navigates back one level.
     */
    fun navigateBack(): Boolean {
        val pathStack = _uiState.value.navigationState.pathStack
        if (pathStack.size <= 1) return false
        
        val newStack = pathStack.dropLast(1)
        val newPath = newStack.lastOrNull() ?: "/"
        loadDirectory(newPath)
        _uiState.update { it.copy(
            navigationState = it.navigationState.copy(pathStack = newStack)
        ) }
        return true
    }

    /**
     * Shows file preview.
     */
    fun showFilePreview(entry: UsbFileEntry) {
        viewModelScope.launch {
            _uiState.update { it.copy(
                previewFile = entry,
                isLoadingPreview = true
            ) }

            usbStorageProvider.getFilePreview(entry.path).fold(
                onSuccess = { preview ->
                    _uiState.update { it.copy(
                        filePreview = preview,
                        isLoadingPreview = false
                    ) }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(
                        isLoadingPreview = false,
                        error = "Failed to load preview: ${error.message}"
                    ) }
                }
            )
        }
    }

    /**
     * Closes file preview.
     */
    fun closePreview() {
        _uiState.update { it.copy(
            previewFile = null,
            filePreview = null
        ) }
    }

    /**
     * Copies a file to internal storage.
     */
    fun copyToInternal(entry: UsbFileEntry) {
        viewModelScope.launch {
            val downloadDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )
            
            _uiState.update { it.copy(
                copyingFile = entry,
                copyProgress = 0f
            ) }

            usbStorageProvider.copyToInternal(entry.path, downloadDir).collect { result ->
                when (result) {
                    is FileOperationResult.Progress -> {
                        _uiState.update { it.copy(copyProgress = result.percentage) }
                    }
                    is FileOperationResult.Success -> {
                        _uiState.update { it.copy(
                            copyingFile = null,
                            copyProgress = 0f,
                            message = "File copied to Downloads"
                        ) }
                    }
                    is FileOperationResult.Error -> {
                        _uiState.update { it.copy(
                            copyingFile = null,
                            copyProgress = 0f,
                            error = result.message
                        ) }
                    }
                }
            }
        }
    }

    /**
     * Searches for files.
     */
    fun search(query: String) {
        viewModelScope.launch {
            if (query.isBlank()) {
                loadDirectory(_uiState.value.navigationState.currentPath)
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, searchQuery = query) }

            usbStorageProvider.searchFiles(query).fold(
                onSuccess = { results ->
                    _uiState.update { it.copy(
                        isLoading = false,
                        files = results,
                        isSearching = true
                    ) }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(
                        isLoading = false,
                        error = "Search failed: ${error.message}"
                    ) }
                }
            )
        }
    }

    /**
     * Clears search and returns to normal browsing.
     */
    fun clearSearch() {
        _uiState.update { it.copy(isSearching = false, searchQuery = "") }
        loadDirectory(_uiState.value.navigationState.currentPath)
    }

    /**
     * Sets the sort order.
     */
    fun setSortOrder(sortOrder: SortOrder) {
        _uiState.update { it.copy(
            navigationState = it.navigationState.copy(sortOrder = sortOrder)
        ) }
        loadDirectory(_uiState.value.navigationState.currentPath)
    }

    /**
     * Toggles hidden files visibility.
     */
    fun toggleHiddenFiles() {
        _uiState.update { it.copy(
            navigationState = it.navigationState.copy(
                showHiddenFiles = !it.navigationState.showHiddenFiles
            )
        ) }
        loadDirectory(_uiState.value.navigationState.currentPath)
    }

    /**
     * Sets the view mode.
     */
    fun setViewMode(viewMode: ViewMode) {
        _uiState.update { it.copy(
            navigationState = it.navigationState.copy(viewMode = viewMode)
        ) }
    }

    /**
     * Toggles file selection.
     */
    fun toggleSelection(entry: UsbFileEntry) {
        _uiState.update { state ->
            val currentSelection = state.navigationState.selectedFiles
            val newSelection = if (entry.path in currentSelection) {
                currentSelection - entry.path
            } else {
                currentSelection + entry.path
            }
            state.copy(
                navigationState = state.navigationState.copy(selectedFiles = newSelection)
            )
        }
    }

    /**
     * Clears selection.
     */
    fun clearSelection() {
        _uiState.update { it.copy(
            navigationState = it.navigationState.copy(selectedFiles = emptySet())
        ) }
    }

    /**
     * Clears error.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Clears message.
     */
    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    override fun onCleared() {
        super.onCleared()
        usbStorageProvider.unmount()
    }
}

/**
 * UI state for USB Browser.
 */
data class UsbBrowserUiState(
    val isScanning: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingPreview: Boolean = false,
    val isSearching: Boolean = false,
    val files: List<UsbFileEntry> = emptyList(),
    val navigationState: BrowserNavigationState = BrowserNavigationState(),
    val previewFile: UsbFileEntry? = null,
    val filePreview: FilePreview? = null,
    val copyingFile: UsbFileEntry? = null,
    val copyProgress: Float = 0f,
    val searchQuery: String = "",
    val error: String? = null,
    val message: String? = null
) {
    val hasSelection: Boolean
        get() = navigationState.selectedFiles.isNotEmpty()

    val selectionCount: Int
        get() = navigationState.selectedFiles.size

    val canGoBack: Boolean
        get() = navigationState.pathStack.size > 1
}
