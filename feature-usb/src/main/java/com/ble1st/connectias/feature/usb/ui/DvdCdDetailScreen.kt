package com.ble1st.connectias.feature.usb.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.feature.usb.models.AudioTrack
import com.ble1st.connectias.feature.usb.models.DiscType
import com.ble1st.connectias.feature.usb.models.DvdInfo
import com.ble1st.connectias.feature.usb.models.OpticalDrive
import com.ble1st.connectias.feature.usb.media.AudioCdProvider
import com.ble1st.connectias.feature.usb.media.AudioCdResult
import com.ble1st.connectias.feature.usb.media.DvdVideoProvider
import com.ble1st.connectias.feature.usb.settings.DvdSettings
import com.ble1st.connectias.feature.usb.storage.OpticalDriveProvider
import com.ble1st.connectias.feature.usb.ui.components.*
import timber.log.Timber

@Composable
fun DvdCdDetailScreen(
    drive: OpticalDrive,
    opticalDriveProvider: OpticalDriveProvider,
    dvdVideoProvider: DvdVideoProvider,
    audioCdProvider: AudioCdProvider,
    dvdSettings: DvdSettings,
    disclaimerText: String,
    onPlayTitle: (Int) -> Unit = {},
    onPlayTrack: (AudioTrack) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var dvdInfo by remember { mutableStateOf<DvdInfo?>(null) }
    var audioTracks by remember { mutableStateOf<List<AudioTrack>>(emptyList()) }
    var files by remember { mutableStateOf<List<com.ble1st.connectias.feature.usb.storage.FileInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(drive) {
        Timber.d("DvdCdDetailScreen: Starting disc detection for type: ${drive.type}")
        isLoading = true
        errorMessage = null
        
        try {
            when (drive.type) {
                DiscType.VIDEO_DVD -> {
                    Timber.d("Opening Video DVD...")
                    dvdInfo = dvdVideoProvider.openDvd(drive)
                    Timber.i("Video DVD opened: ${dvdInfo?.titles?.size} titles")
                }
                DiscType.AUDIO_CD -> {
                    Timber.d("Reading Audio CD tracks...")
                    when (val result = audioCdProvider.getTracks(drive)) {
                        is AudioCdResult.Success -> {
                            audioTracks = result.tracks
                            Timber.i("Audio CD read: ${audioTracks.size} tracks")
                        }
                        is AudioCdResult.Error -> {
                            errorMessage = result.message
                            Timber.e("Error reading Audio CD: ${result.message}", result.throwable)
                            audioTracks = emptyList()
                        }
                    }
                }
                DiscType.DATA_DVD, DiscType.DATA_CD -> {
                    Timber.d("Listing files on data disc...")
                    files = opticalDriveProvider.listFiles(drive)
                    Timber.i("Data disc read: ${files.size} files")
                }
                else -> {
                    Timber.w("Unknown disc type: ${drive.type}")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error during disc detection")
            errorMessage = "Failed to read disc: ${e.message ?: "Unknown error"}"
        } finally {
            isLoading = false
            Timber.d("Disc detection complete")
        }
    }
    
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = when (drive.type) {
                    DiscType.VIDEO_DVD -> "Video DVD"
                    DiscType.AUDIO_CD -> "Audio CD"
                    DiscType.DATA_DVD -> "Data DVD"
                    DiscType.DATA_CD -> "Data CD"
                    else -> "Optical Disc"
                },
                style = MaterialTheme.typography.headlineMedium
            )
        }
        
        item {
            DiscInfoCard(drive = drive)
        }
        
        // CSS-Decryption Settings (only for Video DVDs)
        if (drive.type == DiscType.VIDEO_DVD) {
            item {
                CssDecryptionSettingsCard(
                    dvdSettings = dvdSettings,
                    disclaimerText = disclaimerText
                )
            }
        }
        
        if (isLoading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    CircularProgressIndicator()
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
            
            when (drive.type) {
                DiscType.VIDEO_DVD -> {
                    dvdInfo?.let { info ->
                        item {
                            Text(
                                text = "Titles",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        item {
                            DvdTitleList(
                                titles = info.titles,
                                onTitleSelected = { titleNumber ->
                                    Timber.d("Title selected: $titleNumber")
                                    onPlayTitle(titleNumber)
                                }
                            )
                        }
                    }
                }
                DiscType.AUDIO_CD -> {
                    item {
                        Text(
                            text = "Tracks",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    item {
                        AudioTrackList(
                            tracks = audioTracks,
                            onTrackSelected = { track ->
                                Timber.d("Track selected: ${track.number}")
                                onPlayTrack(track)
                            }
                        )
                    }
                }
                DiscType.DATA_DVD, DiscType.DATA_CD -> {
                    item {
                        Text(
                            text = "Files",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    item {
                        FileBrowser(files = files)
                    }
                }
                else -> {
                    item {
                        Text(
                            text = "Unknown disc type",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}
