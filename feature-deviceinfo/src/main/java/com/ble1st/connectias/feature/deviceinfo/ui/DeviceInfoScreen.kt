package com.ble1st.connectias.feature.deviceinfo.ui

import android.graphics.Color
import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ble1st.connectias.feature.deviceinfo.data.BatteryInfo
import com.ble1st.connectias.feature.deviceinfo.data.StorageInfo
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DeviceInfoScreen(
    viewModel: DeviceViewModel
) {
    val uiState by viewModel.state.collectAsState()
    val context = LocalContext.current

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = "Device Info", style = MaterialTheme.typography.headlineSmall)

            uiState.batteryInfo?.let { BatteryCard(it) }
            uiState.storageInfo?.let { StorageCard(it) }
            
            TempHistoryCard(
                currentTemp = uiState.currentTemp,
                history = uiState.tempHistory,
                onClear = viewModel::clearHistory
            )
        }
    }
}

@Composable
private fun BatteryCard(info: BatteryInfo) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Battery", style = MaterialTheme.typography.titleMedium)
            Divider()
            InfoRow("Level", "${info.level}%")
            InfoRow("Status", info.status)
            InfoRow("Plugged", info.plugged)
            InfoRow("Health", info.health)
            InfoRow("Tech", info.technology)
            InfoRow("Temp", "${info.temperature}°C")
            InfoRow("Voltage", "${info.voltage} mV")
        }
    }
}

@Composable
private fun StorageCard(info: StorageInfo) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Storage & RAM", style = MaterialTheme.typography.titleMedium)
            Divider()
            InfoRow("RAM Total", Formatter.formatFileSize(context, info.totalRam))
            InfoRow("RAM Avail", Formatter.formatFileSize(context, info.availableRam))
            InfoRow("Internal Total", Formatter.formatFileSize(context, info.totalInternal))
            InfoRow("Internal Avail", Formatter.formatFileSize(context, info.availableInternal))
        }
    }
}

@Composable
private fun TempHistoryCard(
    currentTemp: Float,
    history: List<com.ble1st.connectias.feature.deviceinfo.data.TemperatureEntity>,
    onClear: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Temperature History", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onClear) { Text("Clear") }
            }
            Text("Current: $currentTemp°C", style = MaterialTheme.typography.bodyLarge)
            
            if (history.isNotEmpty()) {
                Box(modifier = Modifier.height(200.dp).fillMaxWidth()) {
                    AndroidView(
                        factory = { context ->
                            LineChart(context).apply {
                                description.isEnabled = false
                                xAxis.position = XAxis.XAxisPosition.BOTTOM
                                xAxis.setDrawGridLines(false)
                                xAxis.valueFormatter = object : com.github.mikephil.charting.formatter.ValueFormatter() {
                                    private val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                                    override fun getFormattedValue(value: Float): String {
                                        return sdf.format(Date(value.toLong()))
                                    }
                                }
                                axisRight.isEnabled = false
                                legend.isEnabled = false
                            }
                        },
                        update = { chart ->
                            val entries = history.map { Entry(it.timestamp.toFloat(), it.value) }
                            val dataSet = LineDataSet(entries, "Temperature").apply {
                                color = Color.RED
                                setDrawCircles(false)
                                mode = LineDataSet.Mode.CUBIC_BEZIER
                                setDrawFilled(true)
                                fillColor = Color.RED
                                fillAlpha = 50
                            }
                            chart.data = LineData(dataSet)
                            chart.invalidate()
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
