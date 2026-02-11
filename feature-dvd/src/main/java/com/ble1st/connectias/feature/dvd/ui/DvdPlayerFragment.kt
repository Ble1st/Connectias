package com.ble1st.connectias.feature.dvd.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.ble1st.connectias.common.ui.theme.ConnectiasTheme
import com.ble1st.connectias.feature.dvd.models.UsbDevice
import com.ble1st.connectias.feature.dvd.models.VideoStream
import com.ble1st.connectias.feature.dvd.storage.OpticalDriveProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Fragment for Video DVD player screen using LibVLC.
 */
@AndroidEntryPoint
@Suppress("DEPRECATION")
class DvdPlayerFragment : Fragment() {
    
    companion object {
        internal const val ARG_VIDEO_STREAM = "videoStream"
        internal const val ARG_USB_DEVICE = "usbDevice"
    }
    
    @Inject lateinit var opticalDriveProvider: OpticalDriveProvider
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Timber.d("DvdPlayerFragment: onCreateView")
        
        // Read arguments
        val videoStream = arguments?.getParcelable<VideoStream>(ARG_VIDEO_STREAM)
        val usbDevice = arguments?.getParcelable<UsbDevice>(ARG_USB_DEVICE)
        
        // Validate
        if (videoStream == null) {
            Timber.e("DvdPlayerFragment: VideoStream argument is missing")
            return createErrorComposeView("Video stream configuration missing")
        }
        
        if (usbDevice == null) {
            Timber.e("DvdPlayerFragment: UsbDevice argument is missing")
            return createErrorComposeView("USB device information missing")
        }
        
        // Get active driver session
        val driver = opticalDriveProvider.getActiveSession()
        if (driver == null) {
            Timber.w("DvdPlayerFragment: No active driver session found. Playback might fail or fall back to legacy mode.")
        }
        
        // Construct device path (legacy fallback)
        // Typically /dev/bus/usb/BBB/DDD
        val devicePath = String.format("/dev/bus/usb/%03d/%03d", 
            usbDevice.deviceProtocol, // This mapping is tricky, usually comes from UsbDevice.getDeviceName()
            // Actually, UsbDevice doesn't expose bus/addr directly easily without parsing name
            0 // Placeholder
        )
        
        // Better: Use the URI from VideoStream if it contains the path
        val path = videoStream.uri ?: devicePath
        
        Timber.d("DvdPlayerFragment: Starting playback for $path")
        
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ConnectiasTheme {
                    VlcPlayerScreen(
                        usbDevice = usbDevice,
                        devicePath = path,
                        driver = driver,
                        audioStreamId = videoStream.audioStreamId,
                        subtitleStreamId = videoStream.subtitleStreamId,
                        audioLanguage = videoStream.audioLanguage,
                        subtitleLanguage = videoStream.subtitleLanguage,
                        onBack = {
                            Timber.d("Back button clicked")
                            findNavController().popBackStack()
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch {
            opticalDriveProvider.closeSession()
        }
    }

    private fun createErrorComposeView(message: String): ComposeView {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ConnectiasTheme {
                    ErrorScreen(
                        message = message,
                        onBack = { findNavController().popBackStack() }
                    )
                }
            }
        }
    }
}

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
            Text("Back")
        }
    }
}