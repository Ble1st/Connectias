// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.loggingtest

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ble1st.connectias.service.logging.ILoggingService
import timber.log.Timber

private const val PERMISSION_SUBMIT_LOGS = "com.ble1st.connectias.permission.SUBMIT_EXTERNAL_LOGS"

/**
 * Test app for LoggingService.
 * Uses runtime permission (dangerous, Shizuku-style): user must grant SUBMIT_EXTERNAL_LOGS.
 * After grant, binds to Connectias LoggingService and sends test logs.
 */
class MainActivity : ComponentActivity() {

    private var loggingService: ILoggingService? = null

    private val hasPermissionState = mutableStateOf(false)
    private val isBoundState = mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermissionState.value = granted
        if (granted) {
            Timber.i("[TEST_APP] Permission granted")
            bindToLoggingService()
        } else {
            Timber.w("[TEST_APP] Permission denied")
            Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_LONG).show()
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Timber.i("[TEST_APP] Service connected: $name")
            loggingService = ILoggingService.Stub.asInterface(service)
            isBoundState.value = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Timber.w("[TEST_APP] Service disconnected: $name")
            loggingService = null
            isBoundState.value = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Timber.treeCount == 0) {
            Timber.plant(Timber.DebugTree())
        }
        hasPermissionState.value = hasPermission()
        if (hasPermissionState.value) {
            bindToLoggingService()
        }

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LoggingTestScreen(
                        hasPermission = hasPermissionState.value,
                        isBound = isBoundState.value,
                        onRequestPermission = { requestPermissionIfNeeded() },
                        onOpenSettings = { openAppSettings() },
                        onSendLog = { level, tag, message ->
                            sendLog(level, tag, message)
                        },
                        onSendLogWithException = { level, tag, message, exception ->
                            sendLogWithException(level, tag, message, exception)
                        },
                        onSendBatch = { sendBatchLogs() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasPermissionState.value = hasPermission()
        if (hasPermissionState.value && !isBoundState.value) {
            bindToLoggingService()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBoundState.value) {
            try {
                unbindService(serviceConnection)
                isBoundState.value = false
            } catch (e: Exception) {
                Timber.e(e, "[TEST_APP] Error unbinding service")
            }
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        startActivity(intent)
    }

    private fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, PERMISSION_SUBMIT_LOGS) ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissionIfNeeded() {
        when {
            hasPermission() -> bindToLoggingService()
            else -> permissionLauncher.launch(PERMISSION_SUBMIT_LOGS)
        }
    }

    private fun bindToLoggingService() {
        if (!hasPermission()) return
        try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.ble1st.connectias",
                    "com.ble1st.connectias.service.logging.LoggingService"
                )
            }
            val bound = bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            if (!bound) {
                Timber.e("[TEST_APP] Failed to bind to LoggingService")
            }
        } catch (e: Exception) {
            Timber.e(e, "[TEST_APP] Exception during bind")
        }
    }

    private fun sendLog(level: String, tag: String, message: String) {
        val service = loggingService
        if (service == null) {
            Timber.e("[TEST_APP] Not connected")
            Toast.makeText(this, "Not connected to service", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            service.submitLog(packageName, level, tag, message)
            Timber.i("[TEST_APP] Log sent: $level | $tag | $message")
            Toast.makeText(this, "Log sent", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Timber.e(e, "[TEST_APP] Failed to send log")
            Toast.makeText(this, "Send failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun sendLogWithException(
        level: String,
        tag: String,
        message: String,
        exception: String
    ) {
        val service = loggingService ?: return
        try {
            service.submitLogWithException(packageName, level, tag, message, exception)
            Toast.makeText(this, "Log with exception sent", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Timber.e(e, "[TEST_APP] Failed to send log with exception")
        }
    }

    private fun sendBatchLogs() {
        val service = loggingService ?: return
        Thread {
            try {
                val levels = listOf("DEBUG", "INFO", "WARN", "ERROR")
                repeat(10) { i ->
                    service.submitLog(
                        packageName,
                        levels[i % levels.size],
                        "BatchTest",
                        "Batch log #$i from test app"
                    )
                }
                runOnUiThread {
                    Toast.makeText(this, "Batch sent (10 logs)", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Timber.e(e, "[TEST_APP] Batch send failed")
            }
        }.start()
    }
}

@androidx.compose.runtime.Composable
private fun LoggingTestScreen(
    hasPermission: Boolean,
    isBound: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onSendLog: (String, String, String) -> Unit,
    onSendLogWithException: (String, String, String, String) -> Unit,
    onSendBatch: () -> Unit
) {
    var selectedLevel by mutableStateOf("INFO")
    var tag by mutableStateOf("TestApp")
    var message by mutableStateOf("Test log message")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Logging Service Test",
            style = MaterialTheme.typography.headlineMedium
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = when {
                    !hasPermission -> MaterialTheme.colorScheme.errorContainer
                    !isBound -> MaterialTheme.colorScheme.tertiaryContainer
                    else -> MaterialTheme.colorScheme.primaryContainer
                }
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = when {
                        !hasPermission -> "Permission required"
                        !isBound -> "Permission OK – tap to connect"
                        else -> "Connected to LoggingService"
                    },
                    color = when {
                        !hasPermission -> MaterialTheme.colorScheme.onErrorContainer
                        !isBound -> MaterialTheme.colorScheme.onTertiaryContainer
                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                    }
                )
                if (!hasPermission || !isBound) {
                    Button(
                        onClick = onRequestPermission,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(if (!hasPermission) "Grant permission" else "Connect")
                    }
                }
                if (!hasPermission) {
                    val ctx = LocalContext.current
                    OutlinedButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(ctx.getString(R.string.open_settings))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("DEBUG", "INFO", "WARN", "ERROR").forEach { level ->
                FilterChip(
                    selected = selectedLevel == level,
                    onClick = { selectedLevel = level },
                    label = { Text(level) }
                )
            }
        }

        OutlinedTextField(
            value = tag,
            onValueChange = { tag = it },
            label = { Text("Tag") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            label = { Text("Message") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3
        )

        Button(
            onClick = { onSendLog(selectedLevel, tag, message) },
            enabled = hasPermission && isBound,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Send Log")
        }

        Button(
            onClick = {
                onSendLogWithException(
                    selectedLevel, tag, message,
                    "java.lang.RuntimeException: Test\n    at Test.method(Test.kt:42)"
                )
            },
            enabled = hasPermission && isBound,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Send Log with Exception")
        }

        Button(
            onClick = onSendBatch,
            enabled = hasPermission && isBound,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary
            )
        ) {
            Text("Send Batch (10 logs)")
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "Connectias must be installed. Permission is granted by you at runtime (Shizuku-style).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
