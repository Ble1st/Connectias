package com.ble1st.connectias.feature.calendar.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.feature.calendar.data.CalendarEvent
import com.ble1st.connectias.feature.calendar.ui.components.CalendarSelector
import com.ble1st.connectias.feature.calendar.ui.components.EventDetailDialog
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
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(permissions.allPermissionsGranted) {
        viewModel.setPermission(permissions.allPermissionsGranted)
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    Scaffold(
        floatingActionButton = {
            if (uiState.hasPermission) {
                FloatingActionButton(onClick = viewModel::showAddEventDialog) {
                    Icon(Icons.Default.Add, contentDescription = "Add Event")
                }
            }
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    actionColor = MaterialTheme.colorScheme.primary
                )
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
                if (uiState.isLoading && uiState.events.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                        CalendarContent(
                            uiState = uiState,
                            onPrevMonth = viewModel::onPrevMonth,
                            onNextMonth = viewModel::onNextMonth,
                            onDateSelected = viewModel::onDateSelected,
                            onTitleChange = viewModel::updateTitle,
                            onDescriptionChange = viewModel::updateDescription,
                            onLocationChange = viewModel::updateLocation,
                            onAllDayChange = viewModel::updateAllDay,
                            onCalendarSelected = viewModel::updateSelectedCalendarId,
                            onAddEvent = viewModel::addEvent,
                            onDismissAddEvent = viewModel::hideAddEventDialog,
                            onDeleteEvent = viewModel::deleteEvent,
                            onCalendarToggle = viewModel::toggleCalendarSelection,
                            onEventSelected = viewModel::selectEvent,
                            onShowEditDialog = viewModel::showEditEventDialog,
                            onHideEventDetails = viewModel::hideEventDetails,
                            onUpdateEvent = viewModel::updateEvent,
                            onHideEditDialog = viewModel::hideEditEventDialog,
                            onTodayClick = viewModel::jumpToToday
                        )
                    
                }
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
    onLocationChange: (String) -> Unit,
    onAllDayChange: (Boolean) -> Unit,
    onCalendarSelected: (Long?) -> Unit,
    onAddEvent: () -> Unit,
    onDismissAddEvent: () -> Unit,
    onDeleteEvent: (Long) -> Unit,
    onCalendarToggle: (Long) -> Unit,
    onEventSelected: (CalendarEvent) -> Unit,
    onShowEditDialog: () -> Unit,
    onHideEventDetails: () -> Unit,
    onUpdateEvent: () -> Unit,
    onHideEditDialog: () -> Unit,
    onTodayClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        MonthHeader(
            yearMonth = uiState.currentYearMonth,
            onPrevMonth = onPrevMonth,
            onNextMonth = onNextMonth,
            onTodayClick = onTodayClick
        )

        if (uiState.availableCalendars.isNotEmpty()) {
            CalendarSelector(
                calendars = uiState.availableCalendars,
                selectedCalendarIds = uiState.selectedCalendarIds,
                onCalendarToggle = onCalendarToggle
            )
        }

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
            calendarColors = uiState.calendarColors,
            onDateSelected = onDateSelected
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SelectedDayEvents(
            selectedDate = uiState.selectedDate,
            events = eventsByDate[uiState.selectedDate] ?: emptyList(),
            calendarColors = uiState.calendarColors,
            onDeleteEvent = onDeleteEvent,
            onEventSelected = onEventSelected
        )
    }

    if (uiState.isAddEventDialogVisible) {
        AddEventDialog(
            title = uiState.titleInput,
            description = uiState.descriptionInput,
            location = uiState.locationInput,
            allDay = uiState.allDayInput,
            availableCalendars = uiState.availableCalendars,
            selectedCalendarId = uiState.selectedCalendarIds.firstOrNull(),
            onTitleChange = onTitleChange,
            onDescriptionChange = onDescriptionChange,
            onLocationChange = onLocationChange,
            onAllDayChange = onAllDayChange,
            onCalendarSelected = onCalendarSelected,
            onConfirm = onAddEvent,
            onDismiss = onDismissAddEvent,
            dialogTitle = "Add Event",
            confirmButtonText = "Add"
        )
    }

    if (uiState.isEditEventDialogVisible) {
        AddEventDialog(
            title = uiState.titleInput,
            description = uiState.descriptionInput,
            location = uiState.locationInput,
            allDay = uiState.allDayInput,
            availableCalendars = uiState.availableCalendars,
            selectedCalendarId = uiState.selectedEvent?.calendarId,
            onTitleChange = onTitleChange,
            onDescriptionChange = onDescriptionChange,
            onLocationChange = onLocationChange,
            onAllDayChange = onAllDayChange,
            onCalendarSelected = { /* No-op for now as VM updateEvent doesn't support changing calendar yet */ },
            onConfirm = onUpdateEvent,
            onDismiss = onHideEditDialog,
            dialogTitle = "Edit Event",
            confirmButtonText = "Save"
        )
    }

    uiState.selectedEvent?.let { event ->
        EventDetailDialog(
            event = event,
            onEdit = onShowEditDialog,
            onDismiss = onHideEventDetails
        )
    }
}

@Composable
fun MonthHeader(
    yearMonth: YearMonth,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onTodayClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onPrevMonth,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Previous month",
                modifier = Modifier.size(24.dp)
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
        AnimatedContent(
            targetState = "${yearMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${yearMonth.year}",
            transitionSpec = {
                slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
            },
            label = "MonthHeader"
        ) { monthText ->
            Text(
                text = monthText,
                style = MaterialTheme.typography.headlineMedium
            )
        }
            TextButton(onClick = onTodayClick) {
                Icon(Icons.Default.Today, contentDescription = "Today", modifier = Modifier.size(16.dp))
                Text("Today", style = MaterialTheme.typography.labelSmall)
            }
        }
        IconButton(
            onClick = onNextMonth,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Next month",
                modifier = Modifier.size(24.dp)
            )
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
        DayOfWeek.entries.forEach { day ->
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
    calendarColors: Map<Long, Int>,
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
            val isWeekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
            val isToday = date == LocalDate.now()

            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isSelected -> MaterialTheme.colorScheme.primaryContainer
                            isWeekend -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            else -> Color.Transparent
                        }
                    )
                    .clickable(
                        onClick = { onDateSelected(date) },
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = day.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            isToday -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    if (hasEvent) {
                        val eventColor = eventsByDate[date]?.firstOrNull()?.let { event ->
                            calendarColors[event.calendarId]?.let { Color(it) }
                        } ?: MaterialTheme.colorScheme.secondary
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(eventColor)
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
    calendarColors: Map<Long, Int>,
    onDeleteEvent: (Long) -> Unit,
    onEventSelected: (CalendarEvent) -> Unit
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

        AnimatedVisibility(
            visible = events.isEmpty(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Text(
                text = "No events",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AnimatedVisibility(
            visible = events.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(
                    count = events.size,
                    key = { index -> events[index].id }
                ) { index ->
                    val event = events[index]
                    EventItem(
                        event = event,
                        calendarColor = calendarColors[event.calendarId]?.let { Color(it) },
                        onDelete = { onDeleteEvent(event.id) },
                        onClick = { onEventSelected(event) }
                    )
                }
            }
        }
    }
}

@Composable
fun EventItem(
    event: CalendarEvent,
    calendarColor: Color? = null,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    val start = Instant.ofEpochMilli(event.start).atZone(ZoneId.systemDefault())
    val end = Instant.ofEpochMilli(event.end).atZone(ZoneId.systemDefault())

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (calendarColor != null) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(calendarColor)
                        )
                    }
                    Text(text = event.title, style = MaterialTheme.typography.titleMedium)
                    if (event.allDay) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            ),
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Text(
                                text = "All Day",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(text = event.calendar, style = MaterialTheme.typography.bodySmall)
                if (event.location.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = event.location,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEventDialog(
    title: String,
    description: String,
    location: String,
    allDay: Boolean,
    availableCalendars: List<com.ble1st.connectias.feature.calendar.data.CalendarInfo>,
    selectedCalendarId: Long?,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onLocationChange: (String) -> Unit,
    onAllDayChange: (Boolean) -> Unit,
    onCalendarSelected: (Long?) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dialogTitle: String = "Add Event",
    confirmButtonText: String = "Add"
) {
    var expanded by androidx.compose.runtime.remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = onDescriptionChange,
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = onLocationChange,
                    label = { Text("Location") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = allDay,
                        onCheckedChange = onAllDayChange
                    )
                    Text(
                        text = "All Day",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                if (availableCalendars.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = availableCalendars.find { it.id == selectedCalendarId }?.displayName ?: "Select Calendar",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Calendar") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            availableCalendars.forEach { calendar ->
                                DropdownMenuItem(
                                    text = { Text(calendar.displayName) },
                                    onClick = {
                                        onCalendarSelected(calendar.id)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(confirmButtonText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
