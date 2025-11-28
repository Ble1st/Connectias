package com.ble1st.connectias.feature.utilities.api

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for API Tester.
 */
@HiltViewModel
class ApiTesterViewModel @Inject constructor(
    private val apiTesterProvider: ApiTesterProvider
) : ViewModel() {

    private val _apiState = MutableStateFlow<ApiState>(ApiState.Idle)
    val apiState: StateFlow<ApiState> = _apiState.asStateFlow()

    /**
     * Executes an API request.
     */
    fun executeRequest(
        url: String,
        method: ApiTesterProvider.HttpMethod,
        headers: Map<String, String> = emptyMap(),
        body: String? = null
    ) {
        if (url.isBlank()) {
            _apiState.value = ApiState.Error("URL cannot be empty")
            return
        }

        viewModelScope.launch {
            _apiState.value = ApiState.Loading
            val response = apiTesterProvider.executeRequest(url, method, headers, body)
            _apiState.value = ApiState.Success(response)
        }
    }

    /**
     * Resets the state to idle.
     */
    fun resetState() {
        _apiState.value = ApiState.Idle
    }
}

/**
 * State representation for API operations.
 */
sealed class ApiState {
    object Idle : ApiState()
    object Loading : ApiState()
    data class Success(val response: ApiResponse) : ApiState()
    data class Error(val message: String) : ApiState()
}

