package com.ble1st.connectias.ui.plugin.streaming

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.plugin.streaming.PluginLoadingState

/**
 * Plugin loading progress UI component
 */
@Composable
fun PluginLoadingProgress(
    state: PluginLoadingState,
    modifier: Modifier = Modifier,
    showPercentage: Boolean = true,
    showSpeed: Boolean = true
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusIcon(state = state)
                
                if (showPercentage && state.progress > 0) {
                    Text(
                        text = "${(state.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            // Progress indicator
            when (state) {
                is PluginLoadingState.Downloading -> {
                    LinearProgressIndicator(
                        progress = state.progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                    
                    // Speed and size info
                    if (showSpeed) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = state.stage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            if (state.downloadSpeed > 0) {
                                Text(
                                    text = formatBytes(state.downloadSpeed.toLong()) + "/s",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        Text(
                            text = "${formatBytes(state.bytesDownloaded)} / ${formatBytes(state.totalBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                is PluginLoadingState.Installing -> {
                    LinearProgressIndicator(
                        progress = state.progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                    
                    Text(
                        text = "${state.currentOperation} (${state.currentStep}/${state.totalSteps})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                is PluginLoadingState.Verifying -> {
                    VerificationProgressIndicator(
                        progress = state.progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                    )
                    
                    Text(
                        text = "${state.verificationStep}: ${state.currentCheck}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                is PluginLoadingState.Extracting -> {
                    LinearProgressIndicator(
                        progress = state.progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                    
                    Text(
                        text = "Extracting: ${state.currentFile}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Text(
                        text = "Files: ${state.filesExtracted}/${state.totalFiles}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                is PluginLoadingState.Completed -> {
                    LinearProgressIndicator(
                        progress = 1f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Text(
                        text = "Plugin installed successfully!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                is PluginLoadingState.Failed -> {
                    Text(
                        text = state.statusMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    if (state.canRetry) {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedButton(
                            onClick = { /* Handle retry */ },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Retry")
                        }
                    }
                }
                
                is PluginLoadingState.Paused -> {
                    LinearProgressIndicator(
                        progress = state.progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                    
                    Text(
                        text = "Download paused",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                is PluginLoadingState.Cancelled -> {
                    Text(
                        text = state.statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Compact version for use in lists
 */
@Composable
fun PluginLoadingProgressCompact(
    state: PluginLoadingState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatusIcon(state = state, size = 20.dp)
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.statusMessage,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
            
            if (state.progress > 0 && state !is PluginLoadingState.Completed) {
                LinearProgressIndicator(
                    progress = state.progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                )
            }
        }
        
        if (state is PluginLoadingState.Downloading && state.downloadSpeed > 0) {
            Text(
                text = formatBytes(state.downloadSpeed.toLong()) + "/s",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Status icon for different loading states
 */
@Composable
private fun StatusIcon(
    state: PluginLoadingState,
    size: androidx.compose.ui.unit.Dp = 24.dp
) {
    val (icon, tint) = when (state) {
        is PluginLoadingState.Downloading -> Icons.Default.Downloading to MaterialTheme.colorScheme.primary
        is PluginLoadingState.Installing -> Icons.Default.Settings to MaterialTheme.colorScheme.primary
        is PluginLoadingState.Verifying -> Icons.Default.Security to MaterialTheme.colorScheme.primary
        is PluginLoadingState.Extracting -> Icons.Default.Unarchive to MaterialTheme.colorScheme.primary
        is PluginLoadingState.Completed -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.primary
        is PluginLoadingState.Failed -> Icons.Default.Error to MaterialTheme.colorScheme.error
        is PluginLoadingState.Paused -> Icons.Default.Pause to MaterialTheme.colorScheme.onSurfaceVariant
        is PluginLoadingState.Cancelled -> Icons.Default.Cancel to MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(size),
        tint = tint
    )
}

/**
 * Custom verification progress indicator
 */
@Composable
private fun VerificationProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )
    
    val primaryColor = MaterialTheme.colorScheme.primary
    
    Canvas(modifier = modifier) {
        val strokeWidth = 4.dp.toPx()
        val center = Offset(size.width / 2, size.height / 2)
        val radius = minOf(size.width, size.height) / 2 - strokeWidth
        
        // Background circle
        drawCircle(
            color = Color.Gray.copy(alpha = 0.3f),
            radius = radius,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round
            )
        )
        
        // Progress arc
        drawArc(
            color = primaryColor,
            startAngle = -90f,
            sweepAngle = progress * 360f,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2, radius * 2),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = strokeWidth,
                cap = StrokeCap.Round
            )
        )
        
        // Moving indicator
        val indicatorAngle = rotation - 90f
        val indicatorX = center.x + radius * kotlin.math.cos(Math.toRadians(indicatorAngle.toDouble())).toFloat()
        val indicatorY = center.y + radius * kotlin.math.sin(Math.toRadians(indicatorAngle.toDouble())).toFloat()
        
        drawCircle(
            color = primaryColor,
            radius = strokeWidth / 2,
            center = Offset(indicatorX, indicatorY)
        )
    }
}

/**
 * Format bytes to human readable format
 */
private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    
    val mb = kb / 1024
    if (mb < 1024) return "%.1f MB".format(mb)
    
    val gb = mb / 1024
    return "%.1f GB".format(gb)
}
