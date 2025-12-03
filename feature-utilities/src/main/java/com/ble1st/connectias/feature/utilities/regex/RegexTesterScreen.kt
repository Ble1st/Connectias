package com.ble1st.connectias.feature.utilities.regex

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * Regex Tester screen.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RegexTesterScreen(
    viewModel: RegexTesterViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    var pattern by remember { mutableStateOf("") }
    var testInput by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<RegexResult?>(null) }
    var replacedText by remember { mutableStateOf<String?>(null) }
    var selectedFlags by remember { mutableStateOf(setOf<RegexOption>()) }

    val regexTesterProvider = remember { RegexTesterProvider() }
    val clipboardManager = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Regex Tester") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero section
            item {
                RegexHeroSection()
            }

            // Pattern input
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        OutlinedTextField(
                            value = pattern,
                            onValueChange = { pattern = it },
                            label = { Text("Regex Pattern") },
                            placeholder = { Text("Enter your regex pattern...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Monospace
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Flags
                        Text(
                            text = "Flags",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            RegexFlag("Ignore Case", RegexOption.IGNORE_CASE, selectedFlags) {
                                selectedFlags = if (it in selectedFlags) {
                                    selectedFlags - it
                                } else {
                                    selectedFlags + it
                                }
                            }
                            RegexFlag("Multiline", RegexOption.MULTILINE, selectedFlags) {
                                selectedFlags = if (it in selectedFlags) {
                                    selectedFlags - it
                                } else {
                                    selectedFlags + it
                                }
                            }
                            RegexFlag("Dot Matches All", RegexOption.DOT_MATCHES_ALL, selectedFlags) {
                                selectedFlags = if (it in selectedFlags) {
                                    selectedFlags - it
                                } else {
                                    selectedFlags + it
                                }
                            }
                        }
                    }
                }
            }

            // Test input
            item {
                OutlinedTextField(
                    value = testInput,
                    onValueChange = { testInput = it },
                    label = { Text("Test String") },
                    placeholder = { Text("Enter text to test against...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6
                )
            }

            // Test button
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            if (pattern.isNotBlank()) {
                                result = regexTesterProvider.testPattern(pattern, testInput, selectedFlags)
                            }
                        },
                        enabled = pattern.isNotBlank()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test")
                    }
                }
            }

            // Results
            result?.let { res ->
                item {
                    ResultCard(result = res)
                }

                // Matches
                if (res.matches.isNotEmpty()) {
                    item {
                        Text(
                            text = "Matches (${res.matchCount})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    items(res.matches) { match ->
                        MatchCard(match = match)
                    }
                }
            }

            // Replacement section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Replace",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = replacement,
                            onValueChange = { replacement = it },
                            label = { Text("Replacement") },
                            placeholder = { Text("Enter replacement text...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        FilledTonalButton(
                            onClick = {
                                if (pattern.isNotBlank()) {
                                    replacedText = regexTesterProvider.replace(pattern, testInput, replacement)
                                }
                            },
                            enabled = pattern.isNotBlank() && replacement.isNotBlank()
                        ) {
                            Text("Replace All")
                        }

                        replacedText?.let { text ->
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(text))
                                        }
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Common patterns
            item {
                CommonPatternsSection(
                    patterns = regexTesterProvider.getCommonPatterns(),
                    onPatternSelected = { pattern = it.pattern }
                )
            }
        }
    }
}

@Composable
private fun RegexHeroSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF8B5CF6).copy(alpha = 0.2f),
                            Color(0xFFEC4899).copy(alpha = 0.2f)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF8B5CF6), Color(0xFFEC4899))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Code,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = "Regex Tester",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Test and debug regular expressions",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun RegexFlag(
    label: String,
    option: RegexOption,
    selectedFlags: Set<RegexOption>,
    onToggle: (RegexOption) -> Unit
) {
    FilterChip(
        onClick = { onToggle(option) },
        label = { Text(label) },
        selected = option in selectedFlags
    )
}

@Composable
private fun ResultCard(result: RegexResult) {
    val backgroundColor = if (result.isValid) {
        if (result.matchCount > 0) {
            Color(0xFF10B981).copy(alpha = 0.15f)
        } else {
            Color(0xFFF59E0B).copy(alpha = 0.15f)
        }
    } else {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (result.isValid && result.matchCount > 0) Icons.Default.Check 
                else if (!result.isValid) Icons.Default.Close 
                else Icons.Default.Check,
                contentDescription = null,
                tint = if (result.isValid && result.matchCount > 0) Color(0xFF10B981)
                       else if (!result.isValid) MaterialTheme.colorScheme.error
                       else Color(0xFFF59E0B)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        !result.isValid -> "Invalid Pattern"
                        result.matchCount > 0 -> "${result.matchCount} Match${if (result.matchCount > 1) "es" else ""}"
                        else -> "No Matches"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                result.error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (result.fullMatch) {
                    Text(
                        text = "Full match",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF10B981)
                    )
                }
            }
        }
    }
}

@Composable
private fun MatchCard(match: MatchInfo) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "\"${match.value}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Position: ${match.range.first}-${match.range.last}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (match.groups.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Groups: ${match.groups.joinToString(", ") { "\"$it\"" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun CommonPatternsSection(
    patterns: List<CommonPattern>,
    onPatternSelected: (CommonPattern) -> Unit
) {
    Column {
        Text(
            text = "Common Patterns",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(patterns) { pattern ->
                AssistChip(
                    onClick = { onPatternSelected(pattern) },
                    label = { Text(pattern.name) }
                )
            }
        }
    }
}

