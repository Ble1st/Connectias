package com.ble1st.connectias.feature.usb.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.feature.usb.R
import com.ble1st.connectias.feature.usb.models.DvdTitle
import timber.log.Timber

@Composable
fun DvdTitleList(
    titles: List<DvdTitle>,
    onTitleSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (titles.isEmpty()) {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.dvd_cd_no_titles),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            titles.forEach { title ->
                DvdTitleCard(
                    title = title,
                    onClick = { onTitleSelected(title.number) }
                )
            }
        }
    }
}

@Composable
private fun DvdTitleCard(
    title: DvdTitle,
    onClick: () -> Unit
) {
    val formattedDuration = formatDuration(title.duration)
    val contentDescription = stringResource(
        R.string.dvd_title_content_description,
        title.number,
        title.chapterCount,
        formattedDuration
    )
    
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                this.contentDescription = contentDescription
            }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Title ${title.number}",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(
                    R.string.dvd_title_details,
                    title.chapterCount,
                    formattedDuration
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatDuration(millis: Long): String {
    if (millis < 0) {
        Timber.w("Negative duration encountered: $millis ms. Returning '0:00'.")
        return "0:00"
    }
    val seconds = millis / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes % 60, seconds % 60)
    } else {
        "%d:%02d".format(minutes, seconds % 60)
    }
}
