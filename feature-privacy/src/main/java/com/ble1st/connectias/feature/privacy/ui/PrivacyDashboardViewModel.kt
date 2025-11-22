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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
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
                // Load all privacy information sequentially
                val networkInfo = privacyRepository.getNetworkPrivacyInfo().catch { e ->
                    Timber.e(e, "Error loading network privacy info")
                }.firstOrNull() ?: run {
                    _uiState.value = PrivacyDashboardState.Error("Failed to load network privacy info")
                    return@launch
                }

                val sensorInfo = privacyRepository.getSensorPrivacyInfo().catch { e ->
                    Timber.e(e, "Error loading sensor privacy info")
                }.firstOrNull() ?: run {
                    _uiState.value = PrivacyDashboardState.Error("Failed to load sensor privacy info")
                    return@launch
                }

                val locationInfo = privacyRepository.getLocationPrivacyInfo().catch { e ->
                    Timber.e(e, "Error loading location privacy info")
                }.firstOrNull() ?: run {
                    _uiState.value = PrivacyDashboardState.Error("Failed to load location privacy info")
                    return@launch
                }

                val permissionsInfo = privacyRepository.getAppPermissionsInfo().catch { e ->
                    Timber.e(e, "Error loading permissions info")
                }.firstOrNull() ?: run {
                    _uiState.value = PrivacyDashboardState.Error("Failed to load permissions info")
                    return@launch
                }

                val backgroundInfo = privacyRepository.getBackgroundActivityInfo().catch { e ->
                    Timber.e(e, "Error loading background activity info")
                }.firstOrNull() ?: run {
                    _uiState.value = PrivacyDashboardState.Error("Failed to load background activity info")
                    return@launch
                }

                val storageInfo = privacyRepository.getStoragePrivacyInfo().catch { e ->
                    Timber.e(e, "Error loading storage privacy info")
                }.firstOrNull() ?: run {
                    _uiState.value = PrivacyDashboardState.Error("Failed to load storage privacy info")
                    return@launch
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
            } catch (e: Exception) {
                Timber.e(e, "Error loading privacy data")
                _uiState.value = PrivacyDashboardState.Error(e.message ?: "Unknown error")
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

