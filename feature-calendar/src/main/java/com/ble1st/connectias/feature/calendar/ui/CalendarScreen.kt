package com.ble1st.connectias.feature.calendar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.feature.calendar.R
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel
) {
    val uiState by viewModel.state.collectAsState()
    val permissions = rememberMultiplePermissionsState(
        listOf(
            android.Manifest.permission.READ_CALENDAR,
            android.Manifest.permission.WRITE_CALENDAR
        )
    )

    LaunchedEffect(permissions.allPermissionsGranted) {
        viewModel.setPermission(permissions.allPermissionsGranted)
    }

    ConnectiasTheme {
        Scaffold { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(text = stringResource(R.string.calendar_title), style = MaterialTheme.typography.headlineSmall)

                if (!uiState.hasPermission) {
                    Button(onClick = { permissions.launchMultiplePermissionRequest() }) {
                        Text(text = stringResource(R.string.calendar_request_permission))
                    }
                } else {
                    CreateEventSection(
                        uiState = uiState,
                        onTitleChange = viewModel::updateTitle,
                        onDescriptionChange = viewModel::updateDescription,
                        onStartOffsetChange = viewModel::updateStartOffset,
                        onDurationChange = viewModel::updateDuration,
                        onAddEvent = viewModel::addEvent,
                        onRefresh = viewModel::refresh
                    )
                    EventsList(uiState = uiState)
                }

                uiState.errorMessage?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error)
                }
                uiState.successMessage?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun CreateEventSection(
    uiState: CalendarUiState,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onStartOffsetChange: (Int) -> Unit,
    onDurationChange: (Int) -> Unit,
    onAddEvent: () -> Unit,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = stringResource(R.string.calendar_create_title), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = uiState.titleInput,
                onValueChange = onTitleChange,
                label = { Text(stringResource(R.string.calendar_title_label)) },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = uiState.descriptionInput,
                onValueChange = onDescriptionChange,
                label = { Text(stringResource(R.string.calendar_description_label)) },
                modifier = Modifier.fillMaxWidth()
            )
            Text(text = stringResource(R.string.calendar_start_offset, uiState.startOffsetMinutes))
            Slider(
                value = uiState.startOffsetMinutes.toFloat(),
                onValueChange = { onStartOffsetChange(it.toInt()) },
                valueRange = 0f..1440f,
                steps = 23
            )
            Text(text = stringResource(R.string.calendar_duration, uiState.durationMinutes))
            Slider(
                value = uiState.durationMinutes.toFloat(),
                onValueChange = { onDurationChange(it.toInt()) },
                valueRange = 15f..360f,
                steps = 23
            )
            RowButtons(onAddEvent = onAddEvent, onRefresh = onRefresh, enabled = !uiState.isLoading)
        }
    }
}

@Composable
private fun RowButtons(
    onAddEvent: () -> Unit,
    onRefresh: () -> Unit,
    enabled: Boolean
) {
    androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onAddEvent, enabled = enabled) {
            Text(text = stringResource(R.string.calendar_add_event))
        }
        Button(onClick = onRefresh, enabled = enabled) {
            Text(text = stringResource(R.string.calendar_refresh))
        }
    }
}

@Composable
private fun EventsList(
    uiState: CalendarUiState
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(R.string.calendar_events_title), style = MaterialTheme.typography.titleMedium)
            if (uiState.events.isEmpty()) {
                Text(text = stringResource(R.string.calendar_empty))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.events) { event ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = event.title, style = MaterialTheme.typography.bodyLarge)
                            Text(text = event.calendar, style = MaterialTheme.typography.bodySmall)
                            Text(text = "Start: ${event.start}")
                            Text(text = "End: ${event.end}")
                        }
                    }
                }
            }
        }
    }
}
