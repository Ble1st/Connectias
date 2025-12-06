package com.ble1st.connectias.feature.deviceinfo.battery

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.common.ui.strings.getThemedString
import com.ble1st.connectias.feature.deviceinfo.R

@Composable
fun BatteryAnalyzerScreen(
    state: BatteryState,
    onGetInfo: () -> Unit,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit,
    onEstimateTime: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = getThemedString(stringResource(R.string.battery_analyzer_title)),
            style = MaterialTheme.typography.headlineMedium
        )

        // Control Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onGetInfo, modifier = Modifier.weight(1f)) {
                Text(getThemedString(stringResource(R.string.refresh_info)))
            }
            Button(onClick = onEstimateTime, modifier = Modifier.weight(1f)) {
                Text(getThemedString(stringResource(R.string.estimate_time)))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onStartMonitoring,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text(getThemedString(stringResource(R.string.start_monitor)))
            }
            Button(
                onClick = onStopMonitoring,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(getThemedString(stringResource(R.string.stop_monitor)))
            }
        }

        when (state) {
            is BatteryState.Info -> {
                val info = state.info
                
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(getThemedString(stringResource(R.string.battery_status)), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${info.percentage}%",
                                style = MaterialTheme.typography.displayMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Icon(
                                imageVector = if (info.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = if (info.isCharging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        InfoRow(
                            getThemedString(stringResource(R.string.status)),
                            if (info.isCharging) getThemedString(stringResource(R.string.charging)) else getThemedString(stringResource(R.string.not_charging))
                        )
                        InfoRow(getThemedString(stringResource(R.string.health)), info.healthStatus.name)
                        InfoRow(getThemedString(stringResource(R.string.charge_type)), info.chargeType.name)
                        InfoRow(getThemedString(stringResource(R.string.voltage)), "${info.voltage} mV")
                        InfoRow(getThemedString(stringResource(R.string.temperature)), "${info.temperature}°C")
                        InfoRow(
                            getThemedString(stringResource(R.string.capacity)),
                            if (info.capacity > 0) "${info.capacity / 1000} mAh" else getThemedString(stringResource(R.string.unknown))
                        )
                        InfoRow(getThemedString(stringResource(R.string.current)), "${info.currentAverage / 1000} mA")
                    }
                }
            }
            is BatteryState.TimeEstimate -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(getThemedString(stringResource(R.string.time_estimate)), style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (state.timeMs != null) {
                            val hours = state.timeMs / (1000 * 60 * 60)
                            val minutes = (state.timeMs % (1000 * 60 * 60)) / (1000 * 60)
                            val label = if (state.isCharging) getThemedString(stringResource(R.string.time_to_full_charge)) else getThemedString(stringResource(R.string.time_to_empty))
                            
                            Text(label, style = MaterialTheme.typography.labelMedium)
                            Text(
                                "${hours}h ${minutes}m",
                                style = MaterialTheme.typography.headlineSmall
                            )
                        } else {
                            Text(getThemedString(stringResource(R.string.unable_to_estimate_time)))
                        }
                    }
                }
            }
            is BatteryState.Idle -> {
                Text(
                    getThemedString(stringResource(R.string.tap_refresh_battery)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
