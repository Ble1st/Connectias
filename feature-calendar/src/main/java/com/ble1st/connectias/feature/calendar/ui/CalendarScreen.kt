package com.ble1st.connectias.feature.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.feature.calendar.R
import com.ble1st.connectias.feature.calendar.data.CalendarEvent
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

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

    Scaffold(
        floatingActionButton = {
            if (uiState.hasPermission) {
                FloatingActionButton(onClick = viewModel::showAddEventDialog) {
                    Icon(Icons.Default.Add, contentDescription = "Add Event")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (!uiState.hasPermission) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Calendar permission is required to view and add events.")
                    Button(onClick = { permissions.launchMultiplePermissionRequest() }) {
                        Text("Grant Permission")
                    }
                }
            } else {
                CalendarContent(
                    uiState = uiState,
                    onPrevMonth = viewModel::onPrevMonth,
                    onNextMonth = viewModel::onNextMonth,
                    onDateSelected = viewModel::onDateSelected,
                    onTitleChange = viewModel::updateTitle,
                    onDescriptionChange = viewModel::updateDescription,
                    onAddEvent = viewModel::addEvent,
                    onDismissAddEvent = viewModel::hideAddEventDialog,
                    onDeleteEvent = viewModel::deleteEvent
                )
            }
        }
    }
}

@Composable
fun CalendarContent(
    uiState: CalendarUiState,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onAddEvent: () -> Unit,
    onDismissAddEvent: () -> Unit,
    onDeleteEvent: (Long) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        MonthHeader(
            yearMonth = uiState.currentYearMonth,
            onPrevMonth = onPrevMonth,
            onNextMonth = onNextMonth
        )

        DaysOfWeekHeader()

        val eventsByDate = remember(uiState.events) {
            uiState.events.groupBy {
                Instant.ofEpochMilli(it.start)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
            }
        }

        MonthGrid(
            yearMonth = uiState.currentYearMonth,
            selectedDate = uiState.selectedDate,
            eventsByDate = eventsByDate,
            onDateSelected = onDateSelected
        )

        Divider(modifier = Modifier.padding(vertical = 8.dp))

        SelectedDayEvents(
            selectedDate = uiState.selectedDate,
            events = eventsByDate[uiState.selectedDate] ?: emptyList(),
            onDeleteEvent = onDeleteEvent
        )
    }

    if (uiState.isAddEventDialogVisible) {
        AddEventDialog(
            title = uiState.titleInput,
            description = uiState.descriptionInput,
            onTitleChange = onTitleChange,
            onDescriptionChange = onDescriptionChange,
            onConfirm = onAddEvent,
            onDismiss = onDismissAddEvent
        )
    }
}

@Composable
fun MonthHeader(
    yearMonth: YearMonth,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevMonth) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous month")
        }
        Text(
            text = "${yearMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${yearMonth.year}",
            style = MaterialTheme.typography.titleMedium
        )
        IconButton(onClick = onNextMonth) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next month")
        }
    }
}

@Composable
fun DaysOfWeekHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        DayOfWeek.values().forEach { day ->
            Text(
                text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun MonthGrid(
    yearMonth: YearMonth,
    selectedDate: LocalDate?,
    eventsByDate: Map<LocalDate, List<CalendarEvent>>,
    onDateSelected: (LocalDate) -> Unit
) {
    val daysInMonth = yearMonth.lengthOfMonth()
    val firstDay = LocalDate.of(yearMonth.year, yearMonth.month, 1)
    val startOffset = firstDay.dayOfWeek.ordinal

    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        userScrollEnabled = false
    ) {
        items(startOffset) {
            Spacer(modifier = Modifier.size(0.dp))
        }
        items(daysInMonth) { index ->
            val day = index + 1
            val date = LocalDate.of(yearMonth.year, yearMonth.month, day)
            val isSelected = selectedDate == date
            val hasEvent = eventsByDate[date]?.isNotEmpty() == true

            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else -> Color.Transparent
                        }
                    )
                    .clickable { onDateSelected(date) },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = day.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    if (hasEvent) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SelectedDayEvents(
    selectedDate: LocalDate,
    events: List<CalendarEvent>,
    onDeleteEvent: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (events.isEmpty()) {
            Text(
                text = "No events",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(events.size) { index ->
                    val event = events[index]
                    EventItem(event = event) { onDeleteEvent(event.id) }
                }
            }
        }
    }
}

@Composable
fun EventItem(event: CalendarEvent, onDelete: () -> Unit) {
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    val start = Instant.ofEpochMilli(event.start).atZone(ZoneId.systemDefault())
    val end = Instant.ofEpochMilli(event.end).atZone(ZoneId.systemDefault())

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = event.title, style = MaterialTheme.typography.titleMedium)
                Text(text = event.calendar, style = MaterialTheme.typography.bodySmall)
                if (event.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = event.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = start.format(formatter), style = MaterialTheme.typography.bodyMedium)
                Text(text = end.format(formatter), style = MaterialTheme.typography.bodySmall)
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Event",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun AddEventDialog(
    title: String,
    description: String,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Event") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text("Title") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = onDescriptionChange,
                    label = { Text("Description") }
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
