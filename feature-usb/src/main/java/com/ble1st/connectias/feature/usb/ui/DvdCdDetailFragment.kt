package com.ble1st.connectias.feature.usb.ui

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
import androidx.navigation.fragment.findNavController
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.feature.usb.media.AudioCdProvider
import com.ble1st.connectias.feature.usb.media.DvdVideoProvider
import com.ble1st.connectias.feature.usb.models.AudioTrack
import com.ble1st.connectias.feature.usb.models.OpticalDrive
import com.ble1st.connectias.feature.usb.settings.DvdSettings
import com.ble1st.connectias.feature.usb.storage.OpticalDriveProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
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
    @Inject lateinit var dvdSettings: DvdSettings
    
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
                    val disclaimerText = try {
                        requireContext().resources.openRawResource(
                            com.ble1st.connectias.feature.usb.R.raw.css_disclaimer
                        ).bufferedReader().use { it.readText() }
                    } catch (e: Exception) {
                        Timber.e(e, "Error reading disclaimer text")
                        "CSS-Decryption Disclaimer - See documentation for full text"
                    }
                    
                    // Auto-detect optical drive
                    var drive by remember { mutableStateOf<com.ble1st.connectias.feature.usb.models.OpticalDrive?>(null) }
                    var isLoading by remember { mutableStateOf(true) }
                    
                    var errorMessage by remember { mutableStateOf<String?>(null) }
                    
                    LaunchedEffect(Unit) {
                        Timber.d("Auto-detecting optical drive...")
                        try {
                            drive = opticalDriveProvider.detectAndMountOpticalDrive()
                            if (drive == null) {
                                errorMessage = "No optical disc detected. Please ensure:\n" +
                                        "• A DVD/CD is inserted in the drive\n" +
                                        "• The drive is connected via USB\n" +
                                        "• USB permission is granted"
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
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
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
                            dvdSettings = dvdSettings,
                            disclaimerText = disclaimerText,
                            onPlayTitle = { titleNumber ->
                                Timber.d("Play title requested: $titleNumber")
                                // TODO: Navigate to player screen
                            },
                            onPlayTrack = { track ->
                                Timber.d("Play track requested: ${track.number}")
                                // TODO: Start audio playback
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
