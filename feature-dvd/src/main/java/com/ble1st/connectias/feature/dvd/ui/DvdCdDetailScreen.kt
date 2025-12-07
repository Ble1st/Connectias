package com.ble1st.connectias.feature.dvd.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eject
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.feature.dvd.models.AudioTrack
import com.ble1st.connectias.feature.dvd.models.DiscType
import com.ble1st.connectias.feature.dvd.models.DvdAudioTrack
import com.ble1st.connectias.feature.dvd.models.DvdInfo
import com.ble1st.connectias.feature.dvd.models.DvdSubtitleTrack
import com.ble1st.connectias.feature.dvd.models.FileInfo
import com.ble1st.connectias.feature.dvd.models.OpticalDrive
import com.ble1st.connectias.feature.dvd.media.AudioCdProvider
import com.ble1st.connectias.feature.dvd.media.AudioCdResult
import com.ble1st.connectias.feature.dvd.media.DvdVideoProvider
import com.ble1st.connectias.feature.dvd.storage.OpticalDriveProvider

import com.ble1st.connectias.feature.dvd.ui.components.AudioTrackList
import com.ble1st.connectias.feature.dvd.ui.components.DiscInfoCard
import com.ble1st.connectias.feature.dvd.ui.components.DvdTitleList
import com.ble1st.connectias.feature.dvd.ui.components.FileBrowser
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import timber.log.Timber

@Composable
fun DvdCdDetailScreen(
    drive: OpticalDrive,
    opticalDriveProvider: OpticalDriveProvider,
    dvdVideoProvider: DvdVideoProvider,
    audioCdProvider: AudioCdProvider,
    onPlayTitle: (DvdInfo, Int, Int?, Int?) -> Unit = { _, _, _, _ -> },
    onPlayTrack: (AudioTrack) -> Unit = {},
    onEject: () -> Unit = {},
    onDvdInfoLoaded: (DvdInfo) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val LOG_VERBOSE = false
    fun vLog(msg: () -> String) { if (LOG_VERBOSE) Timber.d(msg()) }
    vLog { "=== DvdCdDetailScreen: COMPOSABLE CREATED ===" }
    vLog { "DvdCdDetailScreen: drive.type=${drive.type}, drive.device=${drive.device.product}" }
    
    var dvdInfo by remember { mutableStateOf<DvdInfo?>(null) }
    var audioTracks by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }
    var files by remember { mutableStateOf<List<FileInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }  // Start with loading=true
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isEjecting by remember { mutableStateOf(false) }
    var detectedDiscType by remember { mutableStateOf(drive.type) }
    var selectedAudioByTitle by remember { mutableStateOf<Map<Int, Int?>>(emptyMap()) }
    var selectedSubtitleByTitle by remember { mutableStateOf<Map<Int, Int?>>(emptyMap()) }
    val coroutineScope = rememberCoroutineScope()

    fun selectedAudioId(title: com.ble1st.connectias.feature.dvd.models.DvdTitle): Int? =
        selectedAudioByTitle[title.number] ?: title.audioTracks.firstOrNull()?.streamId

    fun selectedSubtitleId(title: com.ble1st.connectias.feature.dvd.models.DvdTitle): Int? =
        selectedSubtitleByTitle[title.number] ?: title.subtitleTracks.firstOrNull()?.streamId
    
    LaunchedEffect(drive) {
        vLog { "=== DvdCdDetailScreen: LaunchedEffect STARTED ===" }
        
        // Optimization: If we already have content for this drive instance, don't reload
        if ((dvdInfo != null || audioTracks.isNotEmpty() || files.isNotEmpty()) && !isLoading) {
            vLog { "DvdCdDetailScreen: Content already loaded, skipping detection." }
            return@LaunchedEffect
        }

        vLog { "DvdCdDetailScreen: Starting disc detection for type: ${drive.type}" }
        isLoading = true
        errorMessage = null
        selectedAudioByTitle = emptyMap()
        selectedSubtitleByTitle = emptyMap()
        // Don't clear data yet to avoid flickering if we just refresh
        // dvdInfo = null 
        // audioTracks = emptyList()
        // files = emptyList()
        
        try {
            when (drive.type) {
                DiscType.VIDEO_DVD -> {
                    vLog { "DvdCdDetailScreen: Opening Video DVD..." }
                    dvdInfo = dvdVideoProvider.openDvd(drive)
                    detectedDiscType = DiscType.VIDEO_DVD
                    Timber.i("DvdCdDetailScreen: Video DVD opened: ${dvdInfo?.titles?.size} titles")
                    dvdInfo?.let { info ->
                        onDvdInfoLoaded(info)
                        if (LOG_VERBOSE) {
                        info.titles.forEach { title ->
                            vLog { "  Title ${title.number}: duration=${title.duration}ms, chapters=${title.chapterCount}" }
                        }
                        }
                    }
                }
                DiscType.UNKNOWN -> {
                    // Try to detect disc type by attempting to open as Video DVD first
                    vLog { "DvdCdDetailScreen: Disc type UNKNOWN, attempting auto-detection..." }
                    try {
                        vLog { "DvdCdDetailScreen: Trying to open as Video DVD..." }
                        val detectedDvdInfo = dvdVideoProvider.openDvd(drive)
                        vLog { "DvdCdDetailScreen: openDvd returned: ${detectedDvdInfo?.titles?.size} titles" }
                        
                        if (detectedDvdInfo != null && detectedDvdInfo.titles.isNotEmpty()) {
                            dvdInfo = detectedDvdInfo
                            detectedDiscType = DiscType.VIDEO_DVD
                            onDvdInfoLoaded(detectedDvdInfo)
                            Timber.i("=== DvdCdDetailScreen: Successfully detected Video DVD: ${detectedDvdInfo.titles.size} titles ===")
                            detectedDvdInfo.titles.forEach { title ->
                                vLog { "  Title ${title.number}: duration=${title.duration}ms, chapters=${title.chapterCount}" }
                            }
                        } else {
                            vLog { "DvdCdDetailScreen: Not a Video DVD (no titles), trying Audio CD..." }
                            when (val result = audioCdProvider.getTracks(drive)) {
                                is AudioCdResult.Success -> {
                                    audioTracks = result.tracks
                                    detectedDiscType = DiscType.AUDIO_CD
                                    Timber.i("DvdCdDetailScreen: Successfully detected Audio CD: ${audioTracks.size} tracks")
                                }
                                is AudioCdResult.Error -> {
                                    vLog { "DvdCdDetailScreen: Not an Audio CD, trying data disc..." }
                                    files = opticalDriveProvider.listFiles(drive)
                                    if (files.isNotEmpty()) {
                                        detectedDiscType = DiscType.DATA_DVD
                                        Timber.i("DvdCdDetailScreen: Successfully detected data disc: ${files.size} files")
                                    } else {
                                        Timber.w("DvdCdDetailScreen: Could not determine disc type - disc may be empty or unreadable")
                                        errorMessage = "Could not read disc. Please ensure:\n" +
                                                "• A disc is inserted\n" +
                                                "• The disc is readable\n" +
                                                "• The drive has proper permissions"
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Timber.e(e, "DvdCdDetailScreen: Error detecting disc type")
                        // Try Audio CD as fallback
                        try {
                            when (val result = audioCdProvider.getTracks(drive)) {
                                is AudioCdResult.Success -> {
                                    audioTracks = result.tracks
                                    detectedDiscType = DiscType.AUDIO_CD
                                    Timber.i("DvdCdDetailScreen: Detected Audio CD after Video DVD failed: ${audioTracks.size} tracks")
                                }
                                is AudioCdResult.Error -> {
                    vLog { "DvdCdDetailScreen: Trying data disc as last resort..." }
                                    files = opticalDriveProvider.listFiles(drive)
                                    if (files.isEmpty()) {
                                        errorMessage = "Could not read disc: ${e.message ?: "Unknown error"}"
                                    }
                                }
                            }
                        } catch (e2: Exception) {
                            if (e2 is CancellationException) throw e2
                            Timber.e(e2, "DvdCdDetailScreen: All disc detection methods failed")
                            errorMessage = "Failed to detect disc type: ${e.message ?: "Unknown error"}"
                        }
                    }
                }
                DiscType.AUDIO_CD -> {
                    vLog { "DvdCdDetailScreen: Reading Audio CD tracks..." }
                    when (val result = audioCdProvider.getTracks(drive)) {
                        is AudioCdResult.Success -> {
                            audioTracks = result.tracks
                            detectedDiscType = DiscType.AUDIO_CD
                            Timber.i("DvdCdDetailScreen: Audio CD read: ${audioTracks.size} tracks")
                        }
                        is AudioCdResult.Error -> {
                            errorMessage = result.message
                            Timber.e("DvdCdDetailScreen: Error reading Audio CD: ${result.message}", result.throwable)
                            audioTracks = emptyList()
                        }
                    }
                }
                DiscType.DATA_DVD, DiscType.DATA_CD -> {
                    vLog { "DvdCdDetailScreen: Listing files on data disc..." }
                    files = opticalDriveProvider.listFiles(drive)
                    detectedDiscType = drive.type
                    Timber.i("DvdCdDetailScreen: Data disc read: ${files.size} files")
                }
                else -> {
                    Timber.w("DvdCdDetailScreen: Unsupported disc type: ${drive.type}")
                    errorMessage = "Unsupported disc type: ${drive.type}"
                }
            }
        } catch (e: CancellationException) {
            vLog { "DvdCdDetailScreen: LaunchedEffect cancelled" }
            throw e
        } catch (e: Exception) {
            Timber.e(e, "DvdCdDetailScreen: Error during disc detection")
            errorMessage = "Failed to read disc: ${e.message ?: "Unknown error"}"
        } finally {
            isLoading = false
            vLog { "=== DvdCdDetailScreen: Disc detection COMPLETE ===" }
            vLog { "DvdCdDetailScreen: Final state - detectedDiscType=$detectedDiscType, dvdInfo=${dvdInfo != null}, titles=${dvdInfo?.titles?.size ?: 0}" }
        }
    }
    
    // Log UI state on every recomposition
    vLog { "DvdCdDetailScreen: UI recomposition - isLoading=$isLoading, detectedDiscType=$detectedDiscType, dvdInfo=${dvdInfo != null}, titles=${dvdInfo?.titles?.size ?: 0}" }
    
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                // Show DVD name if available, otherwise show disc type
                val displayText = when {
                    dvdInfo?.name != null -> dvdInfo!!.name!!
                    detectedDiscType == DiscType.VIDEO_DVD -> "Video DVD"
                    detectedDiscType == DiscType.AUDIO_CD -> "Audio CD"
                    detectedDiscType == DiscType.DATA_DVD -> "Data DVD"
                    detectedDiscType == DiscType.DATA_CD -> "Data CD"
                    dvdInfo != null -> "Video DVD"
                    else -> "Optical Disc"
                }
                
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.headlineMedium
                )
                
                // Show disc type as subtitle if DVD name is shown
                if (dvdInfo?.name != null && detectedDiscType == DiscType.VIDEO_DVD) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Video DVD",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        item {
            DiscInfoCard(drive = drive)
        }
        
        // Eject button
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Drive Control",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Eject the disc from the drive",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            vLog { "DvdCdDetailScreen: Eject button clicked" }
                            coroutineScope.launch {
                                isEjecting = true
                                try {
                                    val success = opticalDriveProvider.ejectDrive(drive)
                                    if (success) {
                                        Timber.i("DvdCdDetailScreen: Drive ejected successfully")
                                        onEject()
                                    } else {
                                        Timber.w("DvdCdDetailScreen: Eject failed or not supported")
                                        errorMessage = "Eject failed. The drive may require manual ejection or root access."
                                    }
                                } catch (e: Exception) {
                                    Timber.e(e, "DvdCdDetailScreen: Error ejecting drive")
                                    errorMessage = "Error ejecting drive: ${e.message}"
                                } finally {
                                    isEjecting = false
                                }
                            }
                        },
                        enabled = !isEjecting,
                        modifier = Modifier.padding(start = 16.dp)
                    ) {
                        if (isEjecting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Eject,
                                contentDescription = "Eject",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Eject")
                    }
                }
            }
        }
        
        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Reading disc...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        } else {
            // Show error message if present
            errorMessage?.let { error ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = "Error: $error",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            
            // Show content based on detected disc type OR dvdInfo presence
            // This fixes the issue where disc was UNKNOWN but video DVD was detected
            if (dvdInfo != null && dvdInfo!!.titles.isNotEmpty()) {
                // Video DVD detected - show title list
                vLog { "DvdCdDetailScreen: Rendering ${dvdInfo!!.titles.size} titles" }
                
                item {
                    Text(
                        text = "Titles (${dvdInfo!!.titles.size})",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                
                // Show main movie title prominently (usually the longest title)
                val mainTitle = dvdInfo!!.titles.maxByOrNull { it.duration }
                if (mainTitle != null && mainTitle.duration > 60000) { // > 1 minute
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Text(
                                    text = "Main Feature (Title ${mainTitle.number})",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Duration: ${formatDuration(mainTitle.duration)} • ${mainTitle.chapterCount} chapters",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                                if (mainTitle.audioTracks.isNotEmpty() || mainTitle.subtitleTracks.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    AudioSubtitleSelector(
                                        title = mainTitle,
                                        selectedAudioId = selectedAudioId(mainTitle),
                                        selectedSubtitleId = selectedSubtitleId(mainTitle),
                                        onAudioSelected = { trackId ->
                                            selectedAudioByTitle = selectedAudioByTitle + (mainTitle.number to trackId)
                                        },
                                        onSubtitleSelected = { trackId ->
                                            selectedSubtitleByTitle = selectedSubtitleByTitle + (mainTitle.number to trackId)
                                        },
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        vLog { "=== DvdCdDetailScreen: PLAY MAIN TITLE ${mainTitle.number} CLICKED ===" }
                                        dvdInfo?.let { info ->
                                            onPlayTitle(
                                                info,
                                                mainTitle.number,
                                                selectedAudioId(mainTitle),
                                                selectedSubtitleId(mainTitle)
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Play Movie")
                                }
                            }
                        }
                    }
                }
                
                item {
                    Text(
                        text = "All Titles",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                
                item {
                    DvdTitleList(
                        titles = dvdInfo!!.titles,
                        onTitleSelected = { titleNumber ->
                            vLog { "=== DvdCdDetailScreen: TITLE $titleNumber SELECTED ===" }
                            dvdInfo?.let { info ->
                                val title = info.titles.find { it.number == titleNumber }
                                val audioId = title?.let { selectedAudioId(it) }
                                val subId = title?.let { selectedSubtitleId(it) }
                                onPlayTitle(info, titleNumber, audioId, subId)
                            }
                        }
                    )
                }
            } else if (audioTracks.isNotEmpty()) {
                // Audio CD
                item {
                    Text(
                        text = "Tracks (${audioTracks.size})",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                item {
                    AudioTrackList(
                        tracks = audioTracks,
                        onTrackSelected = { track ->
                            vLog { "DvdCdDetailScreen: Track ${track.number} selected" }
                            onPlayTrack(track)
                        }
                    )
                }
            } else if (files.isNotEmpty()) {
                // Data disc
                item {
                    Text(
                        text = "Files (${files.size})",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                item {
                    FileBrowser(files = files)
                }
            } else if (errorMessage == null) {
                // No content detected
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = "No content found on disc",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioSubtitleSelector(
    title: com.ble1st.connectias.feature.dvd.models.DvdTitle,
    selectedAudioId: Int?,
    selectedSubtitleId: Int?,
    onAudioSelected: (Int?) -> Unit,
    onSubtitleSelected: (Int?) -> Unit,
    colors: CardColors = CardDefaults.cardColors()
) {
    var audioExpanded by remember { mutableStateOf(false) }
    var subExpanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title.audioTracks.isNotEmpty()) {
            Box {
                Button(
                    onClick = { audioExpanded = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.containerColor,
                        contentColor = colors.contentColor
                    )
                ) {
                    Text(text = audioLabel(title.audioTracks, selectedAudioId))
                }
                DropdownMenu(
                    expanded = audioExpanded,
                    onDismissRequest = { audioExpanded = false }
                ) {
                    title.audioTracks.forEach { track ->
                        DropdownMenuItem(
                            text = { Text(audioLabel(listOf(track), track.streamId)) },
                            onClick = {
                                onAudioSelected(track.streamId)
                                audioExpanded = false
                            }
                        )
                    }
                }
            }
        }

        if (title.subtitleTracks.isNotEmpty()) {
            Box {
                Button(
                    onClick = { subExpanded = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.containerColor,
                        contentColor = colors.contentColor
                    )
                ) {
                    Text(text = subtitleLabel(title.subtitleTracks, selectedSubtitleId))
                }
                DropdownMenu(
                    expanded = subExpanded,
                    onDismissRequest = { subExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Subtitles: Off") },
                        onClick = {
                            onSubtitleSelected(null)
                            subExpanded = false
                        }
                    )
                    title.subtitleTracks.forEach { track ->
                        DropdownMenuItem(
                            text = { Text(subtitleLabel(listOf(track), track.streamId)) },
                            onClick = {
                                onSubtitleSelected(track.streamId)
                                subExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun audioLabel(tracks: List<DvdAudioTrack>, selectedId: Int?): String {
    val track = tracks.find { it.streamId == selectedId } ?: tracks.firstOrNull()
    return if (track != null) {
        val lang = track.language?.uppercase() ?: "UND"
        "Audio: $lang • ${track.codec} • ${track.channels}ch"
    } else {
        "Audio"
    }
}

private fun subtitleLabel(tracks: List<DvdSubtitleTrack>, selectedId: Int?): String {
    val track = tracks.find { it.streamId == selectedId } ?: tracks.firstOrNull()
    return when {
        selectedId == null -> "Subtitles: Off"
        track != null -> {
            val lang = track.language?.uppercase() ?: "UND"
            "Subtitles: $lang"
        }
        else -> "Subtitles"
    }
}

private fun formatDuration(millis: Long): String {
    if (millis < 0) return "0:00"
    val seconds = millis / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes % 60, seconds % 60)
    } else {
        "%d:%02d".format(minutes, seconds % 60)
    }
}
