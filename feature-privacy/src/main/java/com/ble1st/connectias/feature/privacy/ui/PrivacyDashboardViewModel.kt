package com.ble1st.connectias.feature.privacy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.feature.privacy.models.AppPermissionInfo
import com.ble1st.connectias.feature.privacy.models.BackgroundActivityInfo
import com.ble1st.connectias.feature.privacy.models.LocationPrivacyInfo
import com.ble1st.connectias.feature.privacy.models.NetworkPrivacyInfo
import com.ble1st.connectias.feature.privacy.models.PrivacyStatus
import com.ble1st.connectias.feature.privacy.models.SensorPrivacyInfo
import com.ble1st.connectias.feature.privacy.models.StoragePrivacyInfo
import com.ble1st.connectias.feature.privacy.repository.PrivacyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for Privacy Dashboard managing state and privacy checks.
 */
@HiltViewModel
class PrivacyDashboardViewModel @Inject constructor(
    private val privacyRepository: PrivacyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<PrivacyDashboardState>(PrivacyDashboardState.Loading)
    val uiState: StateFlow<PrivacyDashboardState> = _uiState.asStateFlow()

    init {
        loadPrivacyData()
    }

    /**
     * Loads all privacy data from repository.
     */
    fun loadPrivacyData() {
        viewModelScope.launch {
            _uiState.value = PrivacyDashboardState.Loading

            try {
                // Load all privacy information in parallel
                coroutineScope {
                    val networkInfoDeferred = async {
                        try {
                            privacyRepository.getNetworkPrivacyInfo()
                        } catch (e: Exception) {
                            Timber.e(e, "Error loading network privacy info")
                            null
                        }
                    }
                    val sensorInfoDeferred = async {
                        try {
                            privacyRepository.getSensorPrivacyInfo()
                        } catch (e: Exception) {
                            Timber.e(e, "Error loading sensor privacy info")
                            null
                        }
                    }
                    val locationInfoDeferred = async {
                        try {
                            privacyRepository.getLocationPrivacyInfo()
                        } catch (e: Exception) {
                            Timber.e(e, "Error loading location privacy info")
                            null
                        }
                    }
                    val permissionsInfoDeferred = async {
                        try {
                            privacyRepository.getAppPermissionsInfo()
                        } catch (e: Exception) {
                            Timber.e(e, "Error loading permissions info")
                            null
                        }
                    }
                    val backgroundInfoDeferred = async {
                        try {
                            privacyRepository.getBackgroundActivityInfo()
                        } catch (e: Exception) {
                            Timber.e(e, "Error loading background activity info")
                            null
                        }
                    }
                    val storageInfoDeferred = async {
                        try {
                            privacyRepository.getStoragePrivacyInfo()
                        } catch (e: Exception) {
                            Timber.e(e, "Error loading storage privacy info")
                            null
                        }
                    }

                    // Await all results
                    val networkInfo = networkInfoDeferred.await()
                    val sensorInfo = sensorInfoDeferred.await()
                    val locationInfo = locationInfoDeferred.await()
                    val permissionsInfo = permissionsInfoDeferred.await()
                    val backgroundInfo = backgroundInfoDeferred.await()
                    val storageInfo = storageInfoDeferred.await()

                    // Check if any required fetch failed
                    if (networkInfo == null) {
                        _uiState.value = PrivacyDashboardState.Error("Failed to load network privacy info")
                        return@coroutineScope
                    }
                    if (sensorInfo == null) {
                        _uiState.value = PrivacyDashboardState.Error("Failed to load sensor privacy info")
                        return@coroutineScope
                    }
                    if (locationInfo == null) {
                        _uiState.value = PrivacyDashboardState.Error("Failed to load location privacy info")
                        return@coroutineScope
                    }
                    if (permissionsInfo == null) {
                        _uiState.value = PrivacyDashboardState.Error("Failed to load permissions info")
                        return@coroutineScope
                    }
                    if (backgroundInfo == null) {
                        _uiState.value = PrivacyDashboardState.Error("Failed to load background activity info")
                        return@coroutineScope
                    }
                    if (storageInfo == null) {
                        _uiState.value = PrivacyDashboardState.Error("Failed to load storage privacy info")
                        return@coroutineScope
                    }

                    // Get overall privacy status
                    val overallStatus = privacyRepository.getOverallPrivacyStatus()

                    _uiState.value = PrivacyDashboardState.Success(
                        UiState(
                            networkPrivacy = networkInfo,
                            sensorPrivacy = sensorInfo,
                            locationPrivacy = locationInfo,
                            appPermissions = permissionsInfo,
                            backgroundActivity = backgroundInfo,
                            storagePrivacy = storageInfo,
                            overallStatus = overallStatus
                        )
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading privacy data")
                _uiState.value = PrivacyDashboardState.Error("Failed to load privacy data. Please try again.")
            }
        }
    }

    /**
     * Refreshes all privacy data.
     */
    fun refresh() {
        privacyRepository.refreshAll()
        loadPrivacyData()
    }
}

/**
 * Sealed class representing the state of the Privacy Dashboard.
 */
sealed class PrivacyDashboardState {
    object Loading : PrivacyDashboardState()
    data class Success(val data: UiState) : PrivacyDashboardState()
    data class Error(val message: String) : PrivacyDashboardState()
}

/**
 * UI state containing all privacy information.
 */
data class UiState(
    val networkPrivacy: NetworkPrivacyInfo,
    val sensorPrivacy: SensorPrivacyInfo,
    val locationPrivacy: LocationPrivacyInfo,
    val appPermissions: List<AppPermissionInfo>,
    val backgroundActivity: BackgroundActivityInfo,
    val storagePrivacy: StoragePrivacyInfo,
    val overallStatus: PrivacyStatus
)

