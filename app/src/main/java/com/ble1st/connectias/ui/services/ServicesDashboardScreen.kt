// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.ui.services

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.core.servicestate.ServiceIds

/**
 * Display labels for service IDs in the Services Dashboard.
 */
fun serviceIdToLabel(serviceId: String): String = when (serviceId) {
    ServiceIds.PLUGIN_SANDBOX -> "Plugin Sandbox"
    ServiceIds.HARDWARE_BRIDGE -> "Hardware Bridge"
    ServiceIds.FILE_SYSTEM_BRIDGE -> "File System Bridge"
    ServiceIds.PLUGIN_MESSAGING -> "Plugin Messaging"
    ServiceIds.PLUGIN_UI -> "Plugin UI"
    else -> serviceId
}

/** Service IDs that can be toggled (bind/unbind) from the dashboard. */
val TOGGLEABLE_SERVICE_IDS: List<String> = listOf(
    ServiceIds.PLUGIN_SANDBOX,
    ServiceIds.HARDWARE_BRIDGE,
    ServiceIds.FILE_SYSTEM_BRIDGE,
    ServiceIds.PLUGIN_MESSAGING,
    ServiceIds.PLUGIN_UI
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServicesDashboardScreen(
    state: Map<String, Boolean>,
    onToggle: (serviceId: String, enabled: Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    loadingServiceIds: Set<String> = emptySet(),
    errorMessage: String? = null
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Services") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(snackbarData = data)
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(16.dp)),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Enable or disable app services. Changes take effect immediately.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            TOGGLEABLE_SERVICE_IDS.forEach { serviceId ->
                val isLoading = serviceId in loadingServiceIds
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = serviceIdToLabel(serviceId),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Switch(
                            checked = state[serviceId] == true,
                            onCheckedChange = { onToggle(serviceId, it) },
                            enabled = !isLoading
                        )
                    }
                }
            }
        }
    }
}
