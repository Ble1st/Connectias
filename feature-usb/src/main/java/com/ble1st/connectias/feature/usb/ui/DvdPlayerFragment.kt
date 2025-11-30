package com.ble1st.connectias.feature.usb.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.feature.usb.R
import com.ble1st.connectias.feature.usb.media.DvdPlayer
import com.ble1st.connectias.feature.usb.models.VideoStream
import com.ble1st.connectias.feature.usb.settings.DvdSettings
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * Fragment for Video DVD player screen.
 */
@AndroidEntryPoint
class DvdPlayerFragment : Fragment() {
    
    companion object {
        private const val ARG_VIDEO_STREAM = "videoStream"
        
        /**
         * Creates a Bundle with VideoStream argument for navigation.
         */
        fun createArguments(videoStream: VideoStream): Bundle {
            return Bundle().apply {
                putParcelable(ARG_VIDEO_STREAM, videoStream)
            }
        }
    }
    
    @Inject lateinit var dvdPlayer: DvdPlayer
    @Inject lateinit var dvdSettings: DvdSettings
    
    override fun onDestroy() {
        super.onDestroy()
        Timber.d("DvdPlayerFragment: onDestroy - releasing player")
        dvdPlayer.release()
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Timber.d("DvdPlayerFragment: onCreateView")
        
        // Read VideoStream from arguments
        val videoStream = arguments?.getParcelable<VideoStream>(ARG_VIDEO_STREAM)
        
        // Validate video stream and URI
        if (videoStream == null) {
            Timber.e("DvdPlayerFragment: VideoStream argument is missing")
            return ComposeView(requireContext()).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    ConnectiasTheme {
                        ErrorScreen(
                            message = stringResource(R.string.dvd_player_error_no_stream),
                            onBack = {
                                Timber.d("Back button clicked from error screen")
                                findNavController().popBackStack()
                            }
                        )
                    }
                }
            }
        }
        
        if (videoStream.uri == null) {
            Timber.e("DvdPlayerFragment: VideoStream URI is null")
            return ComposeView(requireContext()).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    ConnectiasTheme {
                        ErrorScreen(
                            message = stringResource(R.string.dvd_player_error_no_uri),
                            onBack = {
                                Timber.d("Back button clicked from error screen")
                                findNavController().popBackStack()
                            }
                        )
                    }
                }
            }
        }
        
        Timber.d("DvdPlayerFragment: VideoStream loaded - codec=${videoStream.codec}, uri=${videoStream.uri}")
        
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ConnectiasTheme {
                    DvdPlayerScreen(
                        videoStream = videoStream,
                        dvdPlayer = dvdPlayer,
                        dvdSettings = dvdSettings,
                        onBack = {
                            Timber.d("Back button clicked")
                            findNavController().popBackStack()
                        }
                    )
                }
            }
        }
    }
}

/**
 * Error screen shown when video stream is missing or invalid.
 */
@Composable
private fun ErrorScreen(
    message: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onBack) {
            Text(stringResource(R.string.dvd_player_error_back))
        }
    }
}
