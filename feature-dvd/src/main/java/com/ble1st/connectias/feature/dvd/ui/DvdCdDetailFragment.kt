package com.ble1st.connectias.feature.dvd.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.feature.dvd.media.AudioCdPlayer
import com.ble1st.connectias.feature.dvd.media.AudioCdProvider
import com.ble1st.connectias.feature.dvd.media.DvdVideoProvider
import com.ble1st.connectias.feature.dvd.models.AudioTrack
import com.ble1st.connectias.feature.dvd.models.DiscType
import com.ble1st.connectias.feature.dvd.models.DvdInfo
import com.ble1st.connectias.feature.dvd.models.OpticalDrive
import com.ble1st.connectias.feature.dvd.storage.OpticalDriveProvider
import dagger.hilt.android.AndroidEntryPoint
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Fragment for DVD/CD detail screen.
 */
@AndroidEntryPoint
class DvdCdDetailFragment : Fragment() {
    
    @Inject lateinit var opticalDriveProvider: OpticalDriveProvider
    @Inject lateinit var dvdVideoProvider: DvdVideoProvider
    @Inject lateinit var audioCdProvider: AudioCdProvider
    @Inject lateinit var audioCdPlayer: AudioCdPlayer
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Timber.d("DvdCdDetailFragment: onCreateView")
        
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ConnectiasTheme {
                    // Auto-detect optical drive
                    var drive by remember { mutableStateOf<OpticalDrive?>(null) }
                    var dvdInfo by remember { mutableStateOf<DvdInfo?>(null) }
                    var isLoading by remember { mutableStateOf(true) }
                    var errorMessage by remember { mutableStateOf<String?>(null) }
                    val coroutineScope = rememberCoroutineScope()
                    
                    LaunchedEffect(Unit) {
                        Timber.d("Auto-detecting optical drive...")
                        try {
                            drive = opticalDriveProvider.detectOpticalDrive()
                            if (drive == null) {
                                errorMessage = "No optical disc detected. Please ensure:\n" +
                                        "• A DVD/CD is inserted in the drive\n" +
                                        "• The drive is connected via USB\n" +
                                        "• USB permission is granted"
                            } else if (drive?.type == DiscType.VIDEO_DVD) {
                                val detectedDrive = drive
                                // Open DVD to get DvdInfo for navigation
                                try {
                                    dvdInfo = detectedDrive?.let { dvdVideoProvider.openDvd(it) }
                                    Timber.d("DVD opened successfully for navigation")
                                } catch (e: Exception) {
                                    Timber.e(e, "Error opening DVD for navigation")
                                }
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Error detecting optical drive")
                            errorMessage = "Error detecting optical drive: ${e.message}"
                        } finally {
                            isLoading = false
                        }
                    }
                    
                    if (isLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    text = "Detecting optical drive...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    } else if (drive != null) {
                        DvdCdDetailScreen(
                            drive = drive!!,
                            opticalDriveProvider = opticalDriveProvider,
                            dvdVideoProvider = dvdVideoProvider,
                            audioCdProvider = audioCdProvider,
                            onDvdInfoLoaded = { loadedDvdInfo ->
                                Timber.d("DvdCdDetailFragment: dvdInfo loaded from DvdCdDetailScreen with ${loadedDvdInfo.titles.size} titles")
                                dvdInfo = loadedDvdInfo
                            },
                            onPlayTitle = { screenDvdInfo, titleNumber ->
                                Timber.d("=== DvdCdDetailFragment: PLAY TITLE $titleNumber REQUESTED ===")
                                Timber.d("DvdCdDetailFragment: Received dvdInfo directly from screen with ${screenDvdInfo.titles.size} titles")
                                viewLifecycleOwner.lifecycleScope.launch {
                                    try {
                                        if (!isAdded) {
                                            Timber.w("DvdCdDetailFragment: Fragment not added, aborting playTitle")
                                            return@launch
                                        }
                                        
                                        // Use dvdInfo passed directly from the screen (guaranteed to be current)
                                        val currentDvdInfo = screenDvdInfo
                                        val currentDrive = drive
                                        Timber.d("DvdCdDetailFragment: currentDvdInfo=${currentDvdInfo.titles.size} titles, currentDrive=${currentDrive != null}")
                                        
                                        if (currentDrive == null) {
                                            Timber.e("DvdCdDetailFragment: Cannot play title: Drive is null")
                                            view?.let { v ->
                                                Snackbar.make(
                                                    v,
                                                    "Cannot play title: Drive information is missing",
                                                    Snackbar.LENGTH_LONG
                                                ).show()
                                            }
                                            return@launch
                                        }
                                        
                                        Timber.d("DvdCdDetailFragment: Creating VideoStream for title $titleNumber...")
                                        // Create VideoStream for the selected title
                                        val videoStream = dvdVideoProvider.playTitle(currentDvdInfo, titleNumber)
                                        Timber.d("DvdCdDetailFragment: VideoStream created:")
                                        Timber.d("  - codec: ${videoStream.codec}")
                                        Timber.d("  - uri: ${videoStream.uri}")
                                        Timber.d("  - width: ${videoStream.width}")
                                        Timber.d("  - height: ${videoStream.height}")
                                        
                                        if (!isAdded) {
                                            Timber.w("DvdCdDetailFragment: Fragment no longer added after creating VideoStream")
                                            return@launch
                                        }
                                        
                                        // Navigate to player screen with VideoStream
                                        Timber.d("DvdCdDetailFragment: Creating navigation arguments...")
                                        val args = DvdPlayerFragment.createArguments(videoStream)
                                        
                                        Timber.d("DvdCdDetailFragment: Looking up navigation ID 'nav_dvd_player'...")
                                        val navId = resources.getIdentifier("nav_dvd_player", "id", requireContext().packageName)
                                        Timber.d("DvdCdDetailFragment: Navigation ID = $navId")
                                        
                                        if (navId != 0) {
                                            Timber.d("=== DvdCdDetailFragment: NAVIGATING TO DVD PLAYER ===")
                                            findNavController().navigate(navId, args)
                                            Timber.d("DvdCdDetailFragment: Navigation completed")
                                        } else {
                                            Timber.e("DvdCdDetailFragment: Navigation ID nav_dvd_player not found!")
                                            view?.let { v ->
                                                Snackbar.make(
                                                    v,
                                                    "Navigation error: Player screen not found",
                                                    Snackbar.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Timber.e(e, "DvdCdDetailFragment: Error during playTitle")
                                        if (isAdded) {
                                            view?.let { v ->
                                                Snackbar.make(
                                                    v,
                                                    "Playback failed: ${e.message ?: "Unknown error"}",
                                                    Snackbar.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    }
                                }
                            },
                            onPlayTrack = { track ->
                                Timber.d("Play track requested: ${track.number}")
                                viewLifecycleOwner.lifecycleScope.launch {
                                    try {
                                        if (!isAdded) return@launch
                                        
                                        val currentDrive = drive
                                        if (currentDrive == null) {
                                            Timber.e("Cannot play track: Drive is null")
                                            view?.let { v ->
                                                Snackbar.make(
                                                    v,
                                                    "Cannot play track: Drive is not available",
                                                    Snackbar.LENGTH_LONG
                                                ).show()
                                            }
                                            return@launch
                                        }
                                        
                                        Timber.d("Starting audio playback for track ${track.number}")
                                        
                                        // Start audio playback using AudioCdPlayer in IO dispatcher
                                        withContext(Dispatchers.IO) {
                                            audioCdPlayer.playTrack(currentDrive, track)
                                        }
                                        
                                        Timber.d("Audio playback started for track ${track.number}")
                                    } catch (e: Exception) {
                                        Timber.e(e, "Error starting audio playback")
                                        if (isAdded) {
                                            view?.let { v ->
                                                Snackbar.make(
                                                    v,
                                                    "Playback failed: ${e.message ?: "Unknown error"}",
                                                    Snackbar.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    }
                                }
                            },
                            onEject = {
                                Timber.d("Eject callback invoked")
                                // Navigate back or refresh drive detection
                                viewLifecycleOwner.lifecycleScope.launch {
                                    try {
                                        // Close any open DVD handles first
                                        dvdInfo?.let {
                                            try {
                                                dvdVideoProvider.closeDvd(it)
                                                Timber.d("Closed DVD handle before eject")
                                            } catch (e: Exception) {
                                                Timber.w(e, "Error closing DVD handle")
                                            }
                                        }
                                        
                                        // Show message to user
                                        if (isAdded) {
                                            view?.let { v ->
                                                Snackbar.make(
                                                    v,
                                                    "Eject command sent. Please check if the disc was ejected.",
                                                    Snackbar.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                        
                                        // Optionally navigate back or refresh
                                        // findNavController().popBackStack()
                                    } catch (e: Exception) {
                                        Timber.e(e, "Error in eject callback")
                                    }
                                }
                            }
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = errorMessage ?: "No optical disc detected",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            if (errorMessage == null) {
                                Text(
                                    text = "Please ensure a DVD/CD is inserted and the drive is connected via USB.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
