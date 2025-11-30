package com.ble1st.connectias.feature.usb.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import timber.log.Timber

@Composable
fun CssDecryptionDisclaimerDialog(
    disclaimerText: String,
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    var disclaimerAccepted by remember { mutableStateOf(false) }
    
    AlertDialog(
        onDismissRequest = {
            Timber.d("CSS decryption disclaimer dialog dismissed")
            onDismiss()
        },
        title = {
            Text(
                text = "Wichtiger rechtlicher Hinweis - Haftungsausschluss",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = disclaimerText,
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = disclaimerAccepted,
                        onCheckedChange = { disclaimerAccepted = it }
                    )
                    Text(
                        text = "Ich verstehe die rechtlichen Risiken und übernehme die volle Verantwortung",
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (disclaimerAccepted) {
                        Timber.i("User accepted CSS decryption disclaimer")
                        onAccept()
                    }
                },
                enabled = disclaimerAccepted,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("Akzeptieren")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                Timber.d("User dismissed CSS decryption disclaimer")
                onDismiss()
            }) {
                Text("Abbrechen")
            }
        }
    )
}
