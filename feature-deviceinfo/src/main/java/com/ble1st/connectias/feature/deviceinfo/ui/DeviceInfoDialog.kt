package com.ble1st.connectias.feature.deviceinfo.ui

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceInformationDialog(onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    val deviceSpecs = remember { getDeviceInformation(context) }
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Geräteinformationen", "Sensoren")

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            PrimaryTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = { Text(title, style = MaterialTheme.typography.titleMedium) }
                    )
                }
            }

            when (selectedTabIndex) {
                0 -> InfoSpecsTab(deviceSpecs)
                1 -> SensorDashboardTab()
            }
        }
    }
}

@Composable
fun InfoSpecsTab(deviceSpecs: Map<String, List<Pair<String, String>>>) {
    LazyColumn(
        modifier = Modifier.padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        deviceSpecs.forEach { (category, specs) ->
            item {
                Text(
                    category,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
            }
            items(specs) { (name, value) ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(0.4f)
                        )
                        Text(
                            value,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(0.6f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SensorDashboardTab() {
    val context = LocalContext.current
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    
    // Define sensors to track
    val sensors = remember {
        listOf(
            Triple(Sensor.TYPE_ACCELEROMETER, "Accelerometer", Icons.Outlined.Speed),
            Triple(Sensor.TYPE_GYROSCOPE, "Gyroscope", Icons.Outlined.ScreenRotation),
            Triple(Sensor.TYPE_MAGNETIC_FIELD, "Magnetometer", Icons.Outlined.Explore),
            Triple(Sensor.TYPE_LIGHT, "Lumen Sensor", Icons.Outlined.Visibility),
            Triple(Sensor.TYPE_PRESSURE, "Barometer", Icons.Outlined.Compress),
            Triple(Sensor.TYPE_PROXIMITY, "Proximity", Icons.Outlined.Vibration)
        )
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(sensors) { (type, name, icon) ->
            val data = rememberSensorValues(sensorManager, type)
            SensorCard(name = name, icon = icon, data = data)
        }
    }
}

@Composable
fun SensorCard(name: String, icon: ImageVector, data: List<Float>?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    icon, 
                    contentDescription = null, 
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    name, 
                    style = MaterialTheme.typography.titleSmall, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (data == null || data.isEmpty()) {
                Text(
                    "Offline", 
                    style = MaterialTheme.typography.bodyMedium, 
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                data.forEachIndexed { index, value ->
                    val label = when(data.size) {
                        1 -> "Val"
                        3 -> listOf("X", "Y", "Z")[index]
                        else -> "#$index"
                    }
                    
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                label, 
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "%.2f".format(value), 
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        // Visual bar for values (normalized somewhat arbitrarily for visual feedback)
                        val progress = (value.absoluteValue / 20f).coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun rememberSensorValues(sensorManager: SensorManager, type: Int): List<Float>? {
    var values by remember { mutableStateOf<List<Float>?>(null) }

    DisposableEffect(type) {
        val sensor = sensorManager.getDefaultSensor(type)
        if (sensor == null) {
            values = null
            return@DisposableEffect onDispose {}
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event?.let {
                    values = it.values.toList()
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }
    return values
}

fun getDeviceInformation(context: Context): Map<String, List<Pair<String, String>>> {
    val info = mutableMapOf<String, MutableList<Pair<String, String>>>()

    // General Device Info
    info.getOrPut("General", ::mutableListOf).apply {
        add("Manufacturer" to Build.MANUFACTURER)
        add("Model" to Build.MODEL)
        add("Brand" to Build.BRAND)
        add("Device" to Build.DEVICE)
        add("Product" to Build.PRODUCT)
        add("Hardware" to Build.HARDWARE)
        add("Serial" to Build.SERIAL)
    }

    // Android OS Info
    info.getOrPut("Android OS", ::mutableListOf).apply {
        add("Version (Codename)" to Build.VERSION.CODENAME)
        add("Version (Release)" to Build.VERSION.RELEASE)
        add("Version (SDK Int)" to Build.VERSION.SDK_INT.toString())
        add("Build ID" to Build.ID)
        add("Build Type" to Build.TYPE)
        add("Fingerprint" to Build.FINGERPRINT)
    }

    // Display Info
    val displayMetrics = context.resources.displayMetrics
    info.getOrPut("Display", ::mutableListOf).apply {
        add("Screen Size (dp)" to "${displayMetrics.widthPixels / displayMetrics.density} x ${displayMetrics.heightPixels / displayMetrics.density}")
        add("Resolution (px)" to "${displayMetrics.widthPixels} x ${displayMetrics.heightPixels}")
        add("Density (dpi)" to displayMetrics.densityDpi.toString())
        add("Density (scale)" to displayMetrics.density.toString())
    }

    // CPU Info (limited via Build class)
    info.getOrPut("CPU", ::mutableListOf).apply {
        add("ABIs" to Build.SUPPORTED_ABIS.joinToString())
    }

    // Memory Info (approximate, requires careful parsing for detailed)
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memoryInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memoryInfo)
    info.getOrPut("Memory", ::mutableListOf).apply {
        add("Total RAM" to "${(memoryInfo.totalMem / (1024 * 1024 * 1024.0)).format(2)} GB")
        add("Available RAM" to "${(memoryInfo.availMem / (1024 * 1024 * 1024.0)).format(2)} GB")
    }

    // Storage Info (Internal)
    val internalStorage = Environment.getDataDirectory()
    val statFs = StatFs(internalStorage.path)
    val blockSize = statFs.blockSizeLong
    val totalBlocks = statFs.blockCountLong
    val availableBlocks = statFs.availableBlocksLong
    info.getOrPut("Storage (Internal)", ::mutableListOf).apply {
        add("Total Storage" to "${((totalBlocks * blockSize) / (1024 * 1024 * 1024.0)).format(2)} GB")
        add("Available Storage" to "${((availableBlocks * blockSize) / (1024 * 1024 * 1024.0)).format(2)} GB")
    }
    
    // Connectivity (basic checks, more advanced would need permissions)
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
    info.getOrPut("Connectivity", ::mutableListOf).apply {
        add("Network Type" to when {
            networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular"
            networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            else -> "Unknown"
        })
        // Add more basic connectivity info if needed (e.g., IP address - requires permissions and more complex logic)
    }

    return info
}

private fun Double.format(digits: Int) = "%.${digits}f".format(this)

