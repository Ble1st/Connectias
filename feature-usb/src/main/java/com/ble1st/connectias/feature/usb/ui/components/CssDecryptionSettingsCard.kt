package com.ble1st.connectias.feature.usb.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.feature.usb.settings.DvdSettings
import timber.log.Timber

@Composable
fun CssDecryptionSettingsCard(
    dvdSettings: DvdSettings,
    disclaimerText: String,
    modifier: Modifier = Modifier
) {
    var cssEnabled by remember { mutableStateOf(dvdSettings.isCssDecryptionEnabled()) }
    var showDisclaimer by remember { mutableStateOf(false) }
    
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
                text = "CSS-Decryption",
                style = MaterialTheme.typography.titleMedium
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Ermöglicht Wiedergabe von kopiergeschützten DVDs. " +
                       "Rechtlich problematisch - siehe Haftungsausschluss.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Aktiviert")
                
                Switch(
                    checked = cssEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled && !dvdSettings.isDisclaimerAccepted()) {
                            Timber.d("User attempted to enable CSS decryption - showing disclaimer")
                            showDisclaimer = true
                        } else {
                            dvdSettings.setCssDecryptionEnabled(enabled, dvdSettings.isDisclaimerAccepted())
                            cssEnabled = enabled
                        }
                    }
                )
            }
            
            if (cssEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "⚠️ CSS-Decryption ist aktiviert. Sie haften für die rechtmäßige Nutzung.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
