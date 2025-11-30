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
import androidx.compose.ui.unit.dp

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
                Text("Values", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                
                data.values.forEachIndexed { index, value ->
                    Text(
                        text = "Axis $index: ${String.format("%.4f", value)}",
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                val accuracyText = when (data.accuracy) {
                    SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "High"
                    SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Medium"
                    SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Low"
                    SensorManager.SENSOR_STATUS_UNRELIABLE -> "Unreliable"
                    else -> "Unknown"
                }
                Text("Accuracy: $accuracyText", style = MaterialTheme.typography.bodyMedium)
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
            Text("Stop Monitoring")
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
                text = "Sensor Monitor",
                style = MaterialTheme.typography.headlineMedium
            )
            
            Button(
                onClick = onRefresh,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Refresh Sensors")
            }
        }

        if (state is SensorState.Sensors) {
            item {
                Text(
                    "${state.sensors.size} sensors found",
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
                    text = "Tap 'Refresh Sensors' to list available sensors.",
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
                text = "Vendor: ${sensor.vendor}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Max Range: ${sensor.maxRange}", style = MaterialTheme.typography.bodySmall)
                Text("Resolution: ${sensor.resolution}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
