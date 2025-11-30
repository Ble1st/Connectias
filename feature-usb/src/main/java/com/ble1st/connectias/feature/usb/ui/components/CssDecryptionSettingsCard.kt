package com.ble1st.connectias.feature.usb.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.feature.usb.R
import com.ble1st.connectias.feature.usb.settings.DvdSettings
import timber.log.Timber

@Composable
fun CssDecryptionSettingsCard(
    dvdSettings: DvdSettings,
    disclaimerText: String,
    modifier: Modifier = Modifier
) {
    // Read initial state and sync with external changes
    val initialCssEnabled = remember(dvdSettings) { dvdSettings.isCssDecryptionEnabled() }
    var cssEnabled by remember { mutableStateOf(initialCssEnabled) }
    var showDisclaimer by remember { mutableStateOf(false) }
    
    // Sync with external changes to DvdSettings
    LaunchedEffect(dvdSettings) {
        val currentEnabled = dvdSettings.isCssDecryptionEnabled()
        if (currentEnabled != cssEnabled) {
            cssEnabled = currentEnabled
        }
    }
    
    if (showDisclaimer) {
        CssDecryptionDisclaimerDialog(
            disclaimerText = disclaimerText,
            onAccept = {
                dvdSettings.setCssDecryptionEnabled(true, true)
                cssEnabled = true
                showDisclaimer = false
                Timber.i("CSS decryption enabled after disclaimer acceptance")
            },
            onDismiss = {
                showDisclaimer = false
            }
        )
    }
    
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.css_decryption_title),
                style = MaterialTheme.typography.titleMedium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = stringResource(R.string.css_decryption_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.css_decryption_enabled))
                
                Switch(
                    checked = cssEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && !dvdSettings.isDisclaimerAccepted()) {
                            Timber.d("User attempted to enable CSS decryption - showing disclaimer")
                            showDisclaimer = true
                        } else {
                            // When disabling, disclaimer state doesn't matter
                            // When enabling here, disclaimer is already accepted
                            dvdSettings.setCssDecryptionEnabled(enabled, enabled && dvdSettings.isDisclaimerAccepted())
                            cssEnabled = enabled
                        }
                    }
                )
            }
            
            if (cssEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.css_decryption_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
