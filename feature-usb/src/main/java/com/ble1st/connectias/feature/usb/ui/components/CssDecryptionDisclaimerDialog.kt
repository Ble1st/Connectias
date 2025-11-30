package com.ble1st.connectias.feature.usb.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.feature.usb.R
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
                text = stringResource(R.string.css_decryption_disclaimer_title),
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = disclaimerAccepted,
                            role = Role.Checkbox,
                            onValueChange = { disclaimerAccepted = it }
                        )
                ) {
                    Checkbox(
                        checked = disclaimerAccepted,
                        onCheckedChange = {}
                    )
                    Text(
                        text = stringResource(R.string.css_decryption_disclaimer_checkbox),
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    Timber.i("User accepted CSS decryption disclaimer")
                    onAccept()
                },
                enabled = disclaimerAccepted,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(stringResource(R.string.accept))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                Timber.d("User dismissed CSS decryption disclaimer")
                onDismiss()
            }) {
                Text(stringResource(R.string.css_decryption_disclaimer_cancel))
            }
        }
    )
}
