// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2025 Connectias

package com.ble1st.connectias.core.plugin.ui

import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.plugin.ui.UIComponentParcel
import com.ble1st.connectias.plugin.ui.UIStateParcel
import com.ble1st.connectias.plugin.ui.UserActionParcel
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)

/**
 * Main Composable for rendering plugin UI in the UI Process.
 *
 * Three-Process Architecture:
 * - Sandbox Process: Plugin sends UIStateParcel via IPluginUIController
 * - UI Process: This Composable renders the state using Jetpack Compose
 * - Main Process: Orchestrates lifecycle
 *
 * This is a state-based renderer - it receives UIStateParcel and renders
 * the UI declaratively. User interactions generate UserActionParcel objects
 * that are sent back to the Sandbox Process.
 *
 * @param pluginId Plugin identifier
 * @param uiState Current UI state from sandbox, or null if loading
 * @param dialogState Dialog state to display
 * @param loadingState Loading state indicator
 * @param onUserAction Callback for user actions (sent to sandbox)
 * @param onDismissDialog Callback when dialog is dismissed
 */
@Composable
fun PluginUIComposable(
    pluginId: String,
    uiState: UIStateParcel?,
    dialogState: com.ble1st.connectias.core.plugin.ui.PluginUIFragment.DialogState? = null,
    loadingState: com.ble1st.connectias.core.plugin.ui.PluginUIFragment.LoadingState? = null,
    onUserAction: (UserActionParcel) -> Unit,
    onDismissDialog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Show loading indicator if explicitly set or if UI state is null
    val showLoading = loadingState?.loading == true || uiState == null
    
    if (showLoading) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator()
                Text(
                    text = loadingState?.message ?: "Loading plugin UI...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Show dialog on top of loading if present
        dialogState?.let { dialog ->
            RenderDialog(
                dialog = dialog,
                onDismiss = onDismissDialog
            )
        }
        return
    }
    
    // Show dialog if present
    dialogState?.let { dialog ->
        RenderDialog(
            dialog = dialog,
            onDismiss = onDismissDialog
        )
    }

    Scaffold(
        topBar = {
            if (uiState.title.isNotEmpty()) {
                TopAppBar(
                    title = { Text(uiState.title) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Render all UI components
            uiState.components.forEach { component ->
                RenderComponent(
                    component = component,
                    onUserAction = onUserAction
                )
            }
        }
    }
}

/**
 * Renders a single UI component based on its type.
 */
@Composable
private fun RenderComponent(
    component: UIComponentParcel,
    onUserAction: (UserActionParcel) -> Unit,
    modifier: Modifier = Modifier
) {
    when (component.type) {
        "BUTTON" -> RenderButton(component, onUserAction, modifier)
        "TEXT_FIELD" -> RenderTextField(component, onUserAction, modifier)
        "TEXT_VIEW" -> RenderTextView(component, modifier)
        "LIST" -> RenderList(component, onUserAction, modifier)
        "IMAGE" -> RenderImage(component, modifier)
        "CHECKBOX" -> RenderCheckbox(component, onUserAction, modifier)
        "COLUMN" -> RenderColumn(component, onUserAction, modifier)
        "ROW" -> RenderRow(component, onUserAction, modifier)
        "SPACER" -> RenderSpacer(component, modifier)
        else -> {
            Timber.w("[UI_PROCESS] Unknown component type: ${component.type}")
            Text(
                text = "Unknown component: ${component.type}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun RenderButton(
    component: UIComponentParcel,
    onUserAction: (UserActionParcel) -> Unit,
    modifier: Modifier = Modifier
) {
    val text = component.properties.getString("text") ?: "Button"
    val enabled = component.properties.getBoolean("enabled", true)
    val variant = component.properties.getString("variant") ?: "PRIMARY"

    when (variant) {
        "PRIMARY" -> Button(
            onClick = {
                val action = UserActionParcel().apply {
                    actionType = "click"
                    targetId = component.id
                    data = Bundle()
                    timestamp = System.currentTimeMillis()
                }
                onUserAction(action)
            },
            enabled = enabled,
            modifier = modifier.fillMaxWidth()
        ) {
            Text(text)
        }
        "SECONDARY" -> OutlinedButton(
            onClick = {
                val action = UserActionParcel().apply {
                    actionType = "click"
                    targetId = component.id
                    data = Bundle()
                    timestamp = System.currentTimeMillis()
                }
                onUserAction(action)
            },
            enabled = enabled,
            modifier = modifier.fillMaxWidth()
        ) {
            Text(text)
        }
        "TEXT" -> TextButton(
            onClick = {
                val action = UserActionParcel().apply {
                    actionType = "click"
                    targetId = component.id
                    data = Bundle()
                    timestamp = System.currentTimeMillis()
                }
                onUserAction(action)
            },
            enabled = enabled,
            modifier = modifier.fillMaxWidth()
        ) {
            Text(text)
        }
        else -> Button(
            onClick = {
                val action = UserActionParcel().apply {
                    actionType = "click"
                    targetId = component.id
                    data = Bundle()
                    timestamp = System.currentTimeMillis()
                }
                onUserAction(action)
            },
            enabled = enabled,
            modifier = modifier.fillMaxWidth()
        ) {
            Text(text)
        }
    }
}

@Composable
private fun RenderTextField(
    component: UIComponentParcel,
    onUserAction: (UserActionParcel) -> Unit,
    modifier: Modifier = Modifier
) {
    val label = component.properties.getString("label") ?: ""
    val initialValue = component.properties.getString("value") ?: ""
    val hint = component.properties.getString("hint") ?: ""
    val enabled = component.properties.getBoolean("enabled", true)
    val multiline = component.properties.getBoolean("multiline", false)

    var text by remember(initialValue) { mutableStateOf(initialValue) }

    OutlinedTextField(
        value = text,
        onValueChange = { newValue ->
            text = newValue
            val action = UserActionParcel().apply {
                actionType = "text_changed"
                targetId = component.id
                data = Bundle().apply { putString("value", newValue) }
                timestamp = System.currentTimeMillis()
            }
            onUserAction(action)
        },
        label = { Text(label) },
        placeholder = { Text(hint) },
        enabled = enabled,
        singleLine = !multiline,
        maxLines = if (multiline) 5 else 1,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun RenderTextView(
    component: UIComponentParcel,
    modifier: Modifier = Modifier
) {
    val text = component.properties.getString("text") ?: ""
    val style = component.properties.getString("style") ?: "BODY"

    val textStyle = when (style) {
        "HEADLINE" -> MaterialTheme.typography.headlineMedium
        "TITLE" -> MaterialTheme.typography.titleLarge
        "BODY" -> MaterialTheme.typography.bodyLarge
        "CAPTION" -> MaterialTheme.typography.bodySmall
        else -> MaterialTheme.typography.bodyLarge
    }

    Text(
        text = text,
        style = textStyle,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun RenderList(
    component: UIComponentParcel,
    onUserAction: (UserActionParcel) -> Unit,
    modifier: Modifier = Modifier
) {
    val itemCount = component.properties.getInt("itemCount", 0)
    val titles = component.properties.getStringArray("item_titles") ?: emptyArray()
    val subtitles = component.properties.getStringArray("item_subtitles") ?: emptyArray()
    val itemIds = component.properties.getStringArray("item_ids") ?: emptyArray()

    if (itemCount == 0) {
        Text(
            text = "No items",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp)
        )
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(titles.toList()) { index, title ->
            if (index < itemCount) {
                val subtitle = subtitles.getOrNull(index) ?: ""
                val itemId = itemIds.getOrNull(index) ?: index.toString()

                Card(
                    onClick = {
                        val action = UserActionParcel().apply {
                            actionType = "item_selected"
                            targetId = component.id
                            data = Bundle().apply {
                                putInt("position", index)
                                putString("itemId", itemId)
                            }
                            timestamp = System.currentTimeMillis()
                        }
                        onUserAction(action)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (subtitle.isNotEmpty()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderImage(
    component: UIComponentParcel,
    modifier: Modifier = Modifier
) {
    val contentDescription = component.properties.getString("contentDescription") ?: "Image"
    val contentScale = component.properties.getString("contentScale") ?: "FIT"

    // Log all available properties for debugging
    Timber.d("[UI_PROCESS] RenderImage properties: ${component.properties.keySet().joinToString()}")

    // Try to get image data - check both String and ByteArray formats
    val imageBytes = remember(component.properties) {
        // CRITICAL: Try String first to avoid ClassCastException warnings from Bundle
        // Bundle.getByteArray() logs a warning before throwing ClassCastException
        // So we check for String first, then try ByteArray
        
        // First, try to get as Base64 string (most common format via IPC)
        val base64String = component.properties.getString("base64Data")
            ?: component.properties.getString("data")
            ?: component.properties.getString("base64")
            ?: component.properties.getString("imageData")
            ?: component.properties.getString("image")
            ?: component.properties.getString("url")
            ?: component.properties.getString("src")

        if (!base64String.isNullOrEmpty()) {
            try {
                // Remove data URI prefix if present
                val cleanBase64 = if (base64String.startsWith("data:")) {
                    base64String.substringAfter("base64,")
                } else {
                    base64String
                }
                Base64.decode(cleanBase64, Base64.DEFAULT)
            } catch (e: Exception) {
                Timber.e(e, "[UI_PROCESS] Failed to decode Base64 string")
                null
            }
        } else {
            // Fallback: try to get as byte array (if stored as ByteArray)
            // Use try-catch because Bundle.getByteArray() throws ClassCastException if value is String
            try {
                component.properties.getByteArray("base64Data")
            } catch (e: ClassCastException) {
                null // Value is stored as String, already tried above
            } ?: try {
                component.properties.getByteArray("data")
            } catch (e: ClassCastException) {
                null
            } ?: try {
                component.properties.getByteArray("imageData")
            } catch (e: ClassCastException) {
                null
            } ?: try {
                component.properties.getByteArray("image")
            } catch (e: ClassCastException) {
                null
            } ?: null
        }
    }

    if (imageBytes == null || imageBytes.isEmpty()) {
        // No image data provided
        Timber.w("[UI_PROCESS] No image data found in component properties")
        Card(
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No image data",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    Timber.d("[UI_PROCESS] Image data found: ${imageBytes.size} bytes")

    // Decode byte array to bitmap and rotate 90 degrees clockwise
    val bitmap = remember(imageBytes) {
        try {
            val originalBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            if (originalBitmap != null) {
                // Rotate 90 degrees clockwise
                val matrix = Matrix().apply {
                    postRotate(90f)
                }
                Bitmap.createBitmap(
                    originalBitmap,
                    0,
                    0,
                    originalBitmap.width,
                    originalBitmap.height,
                    matrix,
                    true
                ).also {
                    // Recycle original bitmap to free memory
                    if (it != originalBitmap) {
                        originalBitmap.recycle()
                    }
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "[UI_PROCESS] Failed to decode image bytes")
            null
        }
    }

    if (bitmap != null) {
        // Successfully decoded image - display it
        val scale = when (contentScale) {
            "FILL" -> ContentScale.FillBounds
            "FIT" -> ContentScale.Fit
            "CROP" -> ContentScale.Crop
            "INSIDE" -> ContentScale.Inside
            else -> ContentScale.Fit
        }

        Card(
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = contentDescription,
                contentScale = scale,
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
        // Failed to decode image
        Card(
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Failed to load image",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun RenderCheckbox(
    component: UIComponentParcel,
    onUserAction: (UserActionParcel) -> Unit,
    modifier: Modifier = Modifier
) {
    val label = component.properties.getString("label") ?: ""
    val initialChecked = component.properties.getBoolean("checked", false)
    val enabled = component.properties.getBoolean("enabled", true)

    var checked by remember(initialChecked) { mutableStateOf(initialChecked) }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { newValue ->
                checked = newValue
                val action = UserActionParcel().apply {
                    actionType = "checkbox_changed"
                    targetId = component.id
                    data = Bundle().apply { putBoolean("checked", newValue) }
                    timestamp = System.currentTimeMillis()
                }
                onUserAction(action)
            },
            enabled = enabled
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun RenderColumn(
    component: UIComponentParcel,
    onUserAction: (UserActionParcel) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        component.children.forEach { child ->
            RenderComponent(
                component = child,
                onUserAction = onUserAction
            )
        }
    }
}

@Composable
private fun RenderRow(
    component: UIComponentParcel,
    onUserAction: (UserActionParcel) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        component.children.forEach { child ->
            RenderComponent(
                component = child,
                onUserAction = onUserAction,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RenderSpacer(
    component: UIComponentParcel,
    modifier: Modifier = Modifier
) {
    val height = component.properties.getInt("height", 8)
    Spacer(modifier = modifier.height(height.dp))
}

/**
 * Renders a dialog based on dialog state.
 */
@Composable
private fun RenderDialog(
    dialog: com.ble1st.connectias.core.plugin.ui.PluginUIFragment.DialogState,
    onDismiss: () -> Unit
) {
    val icon = when (dialog.dialogType) {
        0 -> androidx.compose.material.icons.Icons.Default.Info // INFO
        1 -> androidx.compose.material.icons.Icons.Default.Warning // WARNING
        2 -> androidx.compose.material.icons.Icons.Default.Error // ERROR
        3 -> androidx.compose.material.icons.Icons.Default.QuestionMark // CONFIRM
        else -> androidx.compose.material.icons.Icons.Default.Info
    }
    
    val iconColor = when (dialog.dialogType) {
        0 -> MaterialTheme.colorScheme.primary // INFO
        1 -> MaterialTheme.colorScheme.error.copy(alpha = 0.8f) // WARNING
        2 -> MaterialTheme.colorScheme.error // ERROR
        3 -> MaterialTheme.colorScheme.primary // CONFIRM
        else -> MaterialTheme.colorScheme.primary
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor
            )
        },
        title = {
            Text(
                text = dialog.title,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = dialog.message,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = if (dialog.dialogType == 3) "OK" else "OK",
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = if (dialog.dialogType == 3) {
            {
                TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
            }
        } else null
    )
}
