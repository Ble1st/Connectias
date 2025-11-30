package com.ble1st.connectias.feature.utilities.qrcode

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

enum class QrCodeType(val label: String) {
    TEXT("Text"),
    WIFI("WiFi"),
    CONTACT("Contact"),
    URL("URL")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrCodeScreen(
    state: QrCodeState,
    onGenerateText: (String) -> Unit,
    onGenerateWifi: (String, String, String) -> Unit,
    onGenerateContact: (String, String?, String?, String?) -> Unit,
    onGenerateUrl: (String) -> Unit
) {
    var selectedType by remember { mutableStateOf(QrCodeType.TEXT) }
    var expanded by remember { mutableStateOf(false) }
    var scannedResult by remember { mutableStateOf("") }
    
    // Input states
    var text by remember { mutableStateOf("") }
    var ssid by remember { mutableStateOf("") }
    var wifiPassword by remember { mutableStateOf("") }
    var securityType by remember { mutableStateOf("WPA") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var org by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    val context = LocalContext.current
    
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            scannedResult = result.contents
        }
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val options = ScanOptions()
            options.setDesiredBarcodeFormats(BarcodeFormat.QR_CODE.name)
            options.setPrompt("Scan a QR code")
            options.setCameraId(0)
            options.setBeepEnabled(false)
            options.setBarcodeImageEnabled(true)
            scanLauncher.launch(options)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "QR Code Tools",
                style = MaterialTheme.typography.headlineMedium
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            readOnly = true,
                            value = selectedType.label,
                            onValueChange = {},
                            label = { Text("Type") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            QrCodeType.values().forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.label) },
                                    onClick = {
                                        selectedType = type
                                        expanded = false
                                    },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    when (selectedType) {
                        QrCodeType.TEXT -> {
                            OutlinedTextField(
                                value = text,
                                onValueChange = { text = it },
                                label = { Text("Text") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3
                            )
                        }
                        QrCodeType.WIFI -> {
                            OutlinedTextField(value = ssid, onValueChange = { ssid = it }, label = { Text("SSID") }, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(value = wifiPassword, onValueChange = { wifiPassword = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(value = securityType, onValueChange = { securityType = it }, label = { Text("Security (WPA/WEP/None)") }, modifier = Modifier.fillMaxWidth())
                        }
                        QrCodeType.CONTACT -> {
                            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(value = org, onValueChange = { org = it }, label = { Text("Organization") }, modifier = Modifier.fillMaxWidth())
                        }
                        QrCodeType.URL -> {
                            OutlinedTextField(
                                value = url,
                                onValueChange = { url = it },
                                label = { Text("URL") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = {
                            when (selectedType) {
                                QrCodeType.TEXT -> onGenerateText(text)
                                QrCodeType.WIFI -> onGenerateWifi(ssid, wifiPassword, securityType)
                                QrCodeType.CONTACT -> onGenerateContact(name, phone.takeIf { it.isNotBlank() }, email.takeIf { it.isNotBlank() }, org.takeIf { it.isNotBlank() })
                                QrCodeType.URL -> onGenerateUrl(url)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Generate QR Code")
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        val options = ScanOptions()
                        options.setDesiredBarcodeFormats(BarcodeFormat.QR_CODE.name)
                        options.setPrompt("Scan a QR code")
                        options.setCameraId(0)
                        options.setBeepEnabled(false)
                        options.setBarcodeImageEnabled(true)
                        scanLauncher.launch(options)
                    } else {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan QR Code")
            }
        }

        if (scannedResult.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Scanned Result", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(scannedResult, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        if (state is QrCodeState.Success) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Image(
                            bitmap = state.bitmap.asImageBitmap(),
                            contentDescription = "Generated QR Code",
                            modifier = Modifier.size(256.dp)
                        )
                    }
                }
            }
        } else if (state is QrCodeState.Error) {
            item {
                Text(state.message, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
