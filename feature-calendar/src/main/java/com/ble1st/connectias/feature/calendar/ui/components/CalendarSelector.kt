package com.ble1st.connectias.feature.calendar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.feature.calendar.data.CalendarInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarSelector(
    calendars: List<CalendarInfo>,
    selectedCalendarIds: Set<Long>,
    onCalendarToggle: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (calendars.isEmpty()) return

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        items(calendars) { calendar ->
            CalendarChip(
                calendar = calendar,
                isSelected = selectedCalendarIds.contains(calendar.id),
                onClick = { onCalendarToggle(calendar.id) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarChip(
    calendar: CalendarInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(text = calendar.displayName) },
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(calendar.color))
            )
        },
        modifier = modifier
    )
}

