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
import androidx.compose.ui.unit.dp

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
            text = "Battery Analyzer",
            style = MaterialTheme.typography.headlineMedium
        )

        // Control Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onGetInfo, modifier = Modifier.weight(1f)) {
                Text("Refresh Info")
            }
            Button(onClick = onEstimateTime, modifier = Modifier.weight(1f)) {
                Text("Estimate Time")
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
                Text("Start Monitor")
            }
            Button(
                onClick = onStopMonitoring,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Stop Monitor")
            }
        }

        when (state) {
            is BatteryState.Info -> {
                val info = state.info
                
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Battery Status", style = MaterialTheme.typography.titleMedium)
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
                        InfoRow("Status", if (info.isCharging) "Charging" else "Not Charging")
                        InfoRow("Health", info.healthStatus.name)
                        InfoRow("Charge Type", info.chargeType.name)
                        InfoRow("Voltage", "${info.voltage} mV")
                        InfoRow("Temperature", "${info.temperature}°C")
                        InfoRow("Capacity", if (info.capacity > 0) "${info.capacity / 1000} mAh" else "Unknown")
                        InfoRow("Current", "${info.currentAverage / 1000} mA")
                    }
                }
            }
            is BatteryState.TimeEstimate -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Time Estimate", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        if (state.timeMs != null) {
                            val hours = state.timeMs / (1000 * 60 * 60)
                            val minutes = (state.timeMs % (1000 * 60 * 60)) / (1000 * 60)
                            val label = if (state.isCharging) "Time to full charge" else "Time to empty"
                            
                            Text(label, style = MaterialTheme.typography.labelMedium)
                            Text(
                                "${hours}h ${minutes}m",
                                style = MaterialTheme.typography.headlineSmall
                            )
                        } else {
                            Text("Unable to estimate time. Ensure device is discharging or charging steadily.")
                        }
                    }
                }
            }
            is BatteryState.Idle -> {
                Text(
                    "Tap 'Refresh Info' to view battery details.",
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
