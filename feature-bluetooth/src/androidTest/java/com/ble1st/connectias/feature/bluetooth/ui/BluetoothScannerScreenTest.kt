@file:OptIn(ExperimentalPermissionsApi::class)

package com.ble1st.connectias.feature.bluetooth.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ble1st.connectias.feature.bluetooth.ui.model.BluetoothUiState
import com.ble1st.connectias.feature.bluetooth.ui.model.PermissionStatus
import com.ble1st.connectias.feature.bluetooth.ui.model.UiDevice
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.PermissionState
import org.junit.Rule
import org.junit.Test

class BluetoothScannerScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun clickingSignalStrengthShowsRadarDialog() {
        val device = UiDevice(
            title = "Test Device",
            address = "AA:BB:CC",
            rssi = -55,
            fillLevel = 0.7f
        )
        composeRule.setContent {
            var uiState by remember {
                mutableStateOf(
                    BluetoothUiState(
                        devices = listOf(device),
                        permissionStatus = PermissionStatus.Granted
                    )
                )
            }
            BluetoothScannerScreen(
                state = uiState,
                permissionsState = GrantedPermissionsState,
                onRequestPermissions = {},
                onToggleScan = {},
                onSignalSelected = {},
                onDismissRadar = {}
            )
        }

        composeRule.onNodeWithText("RSSI: -55 dBm").performClick()
        composeRule.onNodeWithText("Annäherung: 70%").assertIsDisplayed()
    }
}

private object GrantedPermissionsState : MultiplePermissionsState {
    override val allPermissionsGranted: Boolean = true
    override val permissions: List<PermissionState> = emptyList()
    override val revokedPermissions: List<PermissionState> = emptyList()
    override val shouldShowRationale: Boolean = false
    override fun launchMultiplePermissionRequest() = Unit
}
