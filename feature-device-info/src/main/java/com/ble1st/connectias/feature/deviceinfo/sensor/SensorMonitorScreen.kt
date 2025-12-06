package com.ble1st.connectias.feature.deviceinfo.sensor

import android.hardware.SensorManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.common.ui.strings.getThemedString
import com.ble1st.connectias.feature.deviceinfo.R

@Composable
fun SensorMonitorScreen(
    state: SensorState,
    onRefresh: () -> Unit,
    onStartMonitoring: (Int) -> Unit,
    onStopMonitoring: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (state is SensorState.Data) {
            // Show active monitoring view
            ActiveMonitoringView(
                data = state.data,
                onStop = {
                    onStopMonitoring()
                    onRefresh() // Go back to list
                }
            )
        } else {
            // Show list of sensors
            SensorListView(
                state = state,
                onRefresh = onRefresh,
                onSensorClick = onStartMonitoring
            )
        }
    }
}

@Composable
private fun ActiveMonitoringView(
    data: SensorData,
    onStop: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = data.sensorName,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(getThemedString(stringResource(R.string.values)), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                
                data.values.forEachIndexed { index, value ->
                    Text(
                        text = "${getThemedString(stringResource(R.string.axis, index))}: ${String.format("%.4f", value)}",
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                val accuracyText = when (data.accuracy) {
                    SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> getThemedString(stringResource(R.string.accuracy_high))
                    SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> getThemedString(stringResource(R.string.accuracy_medium))
                    SensorManager.SENSOR_STATUS_ACCURACY_LOW -> getThemedString(stringResource(R.string.accuracy_low))
                    SensorManager.SENSOR_STATUS_UNRELIABLE -> getThemedString(stringResource(R.string.accuracy_unreliable))
                    else -> getThemedString(stringResource(R.string.unknown))
                }
                Text("${getThemedString(stringResource(R.string.accuracy))}: $accuracyText", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onStop,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Stop, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(getThemedString(stringResource(R.string.stop_monitor)))
        }
    }
}

@Composable
private fun SensorListView(
    state: SensorState,
    onRefresh: () -> Unit,
    onSensorClick: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = getThemedString(stringResource(R.string.sensor_monitor_title)),
                style = MaterialTheme.typography.headlineMedium
            )
            
            Button(
                onClick = onRefresh,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(getThemedString(stringResource(R.string.refresh_sensors)))
            }
        }

        if (state is SensorState.Sensors) {
            item {
                Text(
                    getThemedString(stringResource(R.string.sensors_found, state.sensors.size)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(state.sensors) { sensor ->
                SensorItem(sensor, onClick = { onSensorClick(sensor.type) })
            }
        } else if (state is SensorState.Idle) {
            item {
                 Text(
                    text = getThemedString(stringResource(R.string.tap_refresh_sensors)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SensorItem(sensor: SensorInfo, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = sensor.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Icon(Icons.Default.ChevronRight, contentDescription = null)
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${getThemedString(stringResource(R.string.vendor))}: ${sensor.vendor}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${getThemedString(stringResource(R.string.max_range))}: ${sensor.maxRange}", style = MaterialTheme.typography.bodySmall)
                Text("${getThemedString(stringResource(R.string.resolution))}: ${sensor.resolution}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
