package com.ble1st.connectias.feature.utilities.color

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

enum class ColorOperation(val label: String) {
    RGB_TO_HEX("RGB to HEX"),
    HEX_TO_RGB("HEX to RGB"),
    RGB_TO_HSL("RGB to HSL"),
    HSL_TO_RGB("HSL to RGB"),
    RGB_TO_HSV("RGB to HSV"),
    CONTRAST_CHECKER("Contrast Checker")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorScreen(
    state: ColorState,
    onRgbToHex: (Int, Int, Int) -> Unit,
    onHexToRgb: (String) -> Unit,
    onRgbToHsl: (Int, Int, Int) -> Unit,
    onHslToRgb: (Int, Int, Int) -> Unit,
    onRgbToHsv: (Int, Int, Int) -> Unit,
    onContrast: (String, String) -> Unit
) {
    var selectedOperation by remember { mutableStateOf(ColorOperation.RGB_TO_HEX) }
    var expanded by remember { mutableStateOf(false) }
    
    // Input states
    var r by remember { mutableStateOf("0") }
    var g by remember { mutableStateOf("0") }
    var b by remember { mutableStateOf("0") }
    var hex by remember { mutableStateOf("#000000") }
    var h by remember { mutableStateOf("0") }
    var s by remember { mutableStateOf("0") }
    var l by remember { mutableStateOf("0") }
    var color1 by remember { mutableStateOf("#FFFFFF") }
    var color2 by remember { mutableStateOf("#000000") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Color Tools",
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
                            value = selectedOperation.label,
                            onValueChange = {},
                            label = { Text("Operation") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            ColorOperation.values().forEach { op ->
                                DropdownMenuItem(
                                    text = { Text(op.label) },
                                    onClick = {
                                        selectedOperation = op
                                        expanded = false
                                    },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    when (selectedOperation) {
                        ColorOperation.RGB_TO_HEX, ColorOperation.RGB_TO_HSL, ColorOperation.RGB_TO_HSV -> {
                            RgbInputs(r, g, b, { r = it }, { g = it }, { b = it })
                        }
                        ColorOperation.HEX_TO_RGB -> {
                            OutlinedTextField(
                                value = hex,
                                onValueChange = { hex = it },
                                label = { Text("HEX Color") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        ColorOperation.HSL_TO_RGB -> {
                            HslInputs(h, s, l, { h = it }, { s = it }, { l = it })
                        }
                        ColorOperation.CONTRAST_CHECKER -> {
                            ContrastInputs(color1, color2, { color1 = it }, { color2 = it })
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = {
                            when (selectedOperation) {
                                ColorOperation.RGB_TO_HEX -> onRgbToHex(r.toIntOrNull() ?: 0, g.toIntOrNull() ?: 0, b.toIntOrNull() ?: 0)
                                ColorOperation.HEX_TO_RGB -> onHexToRgb(hex)
                                ColorOperation.RGB_TO_HSL -> onRgbToHsl(r.toIntOrNull() ?: 0, g.toIntOrNull() ?: 0, b.toIntOrNull() ?: 0)
                                ColorOperation.HSL_TO_RGB -> onHslToRgb(h.toIntOrNull() ?: 0, s.toIntOrNull() ?: 0, l.toIntOrNull() ?: 0)
                                ColorOperation.RGB_TO_HSV -> onRgbToHsv(r.toIntOrNull() ?: 0, g.toIntOrNull() ?: 0, b.toIntOrNull() ?: 0)
                                ColorOperation.CONTRAST_CHECKER -> onContrast(color1, color2)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Convert")
                    }
                }
            }
        }

        if (state !is ColorState.Idle) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Result", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        when (state) {
                            is ColorState.HexResult -> {
                                ResultRow("HEX:", state.hex)
                                ColorPreview(state.hex)
                            }
                            is ColorState.RgbResult -> {
                                ResultRow("RGB:", "(${state.rgb.r}, ${state.rgb.g}, ${state.rgb.b})")
                                val hexPreview = String.format("#%02X%02X%02X", state.rgb.r, state.rgb.g, state.rgb.b)
                                ColorPreview(hexPreview)
                            }
                            is ColorState.HslResult -> {
                                ResultRow("HSL:", "(${state.hsl.h}°, ${state.hsl.s}%, ${state.hsl.l}%)")
                            }
                            is ColorState.HsvResult -> {
                                ResultRow("HSV:", "(${state.hsv.h}°, ${state.hsv.s}%, ${state.hsv.v}%)")
                            }
                            is ColorState.ContrastResult -> {
                                ResultRow("Contrast Ratio:", String.format("%.2f:1", state.ratio))
                                ResultRow("WCAG AA (4.5:1):", if (state.meetsAA) "Pass" else "Fail")
                                ResultRow("WCAG AAA (7:1):", if (state.meetsAAA) "Pass" else "Fail")
                            }
                            is ColorState.Error -> {
                                Text(state.message, color = MaterialTheme.colorScheme.error)
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RgbInputs(r: String, g: String, b: String, onRChange: (String) -> Unit, onGChange: (String) -> Unit, onBChange: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = r, onValueChange = onRChange, label = { Text("R") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        OutlinedTextField(value = g, onValueChange = onGChange, label = { Text("G") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        OutlinedTextField(value = b, onValueChange = onBChange, label = { Text("B") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    }
}

@Composable
private fun HslInputs(h: String, s: String, l: String, onHChange: (String) -> Unit, onSChange: (String) -> Unit, onLChange: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = h, onValueChange = onHChange, label = { Text("H") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        OutlinedTextField(value = s, onValueChange = onSChange, label = { Text("S") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        OutlinedTextField(value = l, onValueChange = onLChange, label = { Text("L") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    }
}

@Composable
private fun ContrastInputs(c1: String, c2: String, onC1Change: (String) -> Unit, onC2Change: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(value = c1, onValueChange = onC1Change, label = { Text("Foreground") }, modifier = Modifier.weight(1f))
        OutlinedTextField(value = c2, onValueChange = onC2Change, label = { Text("Background") }, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ColorPreview(hex: String) {
    Spacer(modifier = Modifier.height(16.dp))
    val color = try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        null
    }
    
    if (color != null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(color)
                .border(1.dp, MaterialTheme.colorScheme.outline)
        )
    }
}
