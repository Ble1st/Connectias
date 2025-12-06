package com.ble1st.connectias.feature.security.scanner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ble1st.connectias.common.ui.strings.getThemedString
import com.ble1st.connectias.feature.security.R
import com.ble1st.connectias.feature.security.scanner.models.PortRange
import com.ble1st.connectias.feature.security.scanner.models.ScanConfiguration
import com.ble1st.connectias.feature.security.scanner.models.ScanIntensity
import com.ble1st.connectias.feature.security.scanner.models.ScanType
import com.ble1st.connectias.feature.security.scanner.models.TargetType

/**
 * Material 3 Expressive Bottom Sheet for scan configuration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanConfigurationSheet(
    scanType: ScanType,
    initialConfig: ScanConfiguration? = null,
    onDismiss: () -> Unit,
    onConfirm: (ScanConfiguration) -> Unit
) {
    val config = remember {
        initialConfig ?: ScanConfiguration(
            scanType = scanType,
            target = "",
            intensity = ScanIntensity.BALANCED,
            enableServiceDetection = true,
            enableSecurityAssessment = true
        )
    }

    var intensity by remember { mutableStateOf(config.intensity) }
    var portRange by remember { mutableStateOf(config.portRange) }
    var customPortRange by remember { mutableStateOf(config.portRangeCustom ?: "") }
    var enableServiceDetection by remember { mutableStateOf(config.enableServiceDetection) }
    var enableSecurityAssessment by remember { mutableStateOf(config.enableSecurityAssessment) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(0.9f),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = getThemedString(stringResource(R.string.scan_configuration)),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Scan Intensity
            Text(
                text = getThemedString(stringResource(R.string.scan_intensity)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                SegmentedButton(
                    selected = intensity == ScanIntensity.FAST,
                    onClick = { intensity = ScanIntensity.FAST },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                ) {
                    Text(getThemedString(stringResource(R.string.fast)))
                }
                SegmentedButton(
                    selected = intensity == ScanIntensity.BALANCED,
                    onClick = { intensity = ScanIntensity.BALANCED },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                ) {
                    Text(getThemedString(stringResource(R.string.balanced)))
                }
                SegmentedButton(
                    selected = intensity == ScanIntensity.DEEP,
                    onClick = { intensity = ScanIntensity.DEEP },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                ) {
                    Text(getThemedString(stringResource(R.string.deep)))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Port Range (only for port scans)
            if (scanType == ScanType.PORT_SCAN) {
                Text(
                    text = getThemedString(stringResource(R.string.port_range)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SegmentedButton(
                        selected = portRange == PortRange.COMMON_PORTS,
                        onClick = { portRange = PortRange.COMMON_PORTS },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                    ) {
                        Text(getThemedString(stringResource(R.string.common)))
                    }
                    SegmentedButton(
                        selected = portRange == PortRange.STANDARD,
                        onClick = { portRange = PortRange.STANDARD },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                    ) {
                        Text(getThemedString(stringResource(R.string.standard)))
                    }
                    SegmentedButton(
                        selected = portRange == PortRange.FULL,
                        onClick = { portRange = PortRange.FULL },
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                    ) {
                        Text(getThemedString(stringResource(R.string.full)))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = customPortRange,
                    onValueChange = { customPortRange = it },
                    label = { Text(getThemedString(stringResource(R.string.custom_range_hint))) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    supportingText = { Text(getThemedString(stringResource(R.string.custom_range_supporting))) }
                )

                Spacer(modifier = Modifier.height(24.dp))
            }

            // Service Detection Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = getThemedString(stringResource(R.string.service_detection)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = getThemedString(stringResource(R.string.service_detection_description)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enableServiceDetection,
                    onCheckedChange = { enableServiceDetection = it }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Security Assessment Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = getThemedString(stringResource(R.string.security_assessment)),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = getThemedString(stringResource(R.string.security_assessment_description)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enableSecurityAssessment,
                    onCheckedChange = { enableSecurityAssessment = it }
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(getThemedString(stringResource(R.string.cancel)))
                }
                Button(
                    onClick = {
                        onConfirm(
                            config.copy(
                                intensity = intensity,
                                portRange = portRange,
                                portRangeCustom = customPortRange.takeIf { it.isNotBlank() },
                                enableServiceDetection = enableServiceDetection,
                                enableSecurityAssessment = enableSecurityAssessment
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(getThemedString(stringResource(R.string.start_scan)))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

