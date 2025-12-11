package com.ble1st.connectias.feature.ssh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ble1st.connectias.feature.ssh.data.SshProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SshTerminalScreen(
    viewModel: SshTerminalViewModel,
    profile: SshProfile,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.connect(profile)
    }
    
    // Auto-scroll to bottom
    LaunchedEffect(state.output) {
        if (state.output.isNotEmpty()) {
             // A simple way to scroll to bottom is tricky with just a Text block.
             // Using LazyColumn with lines is better for large output.
             // For now, let's just assume the text isn't massive or user handles scroll.
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(profile.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black)
        ) {
            // Output Area
            Box(modifier = Modifier.weight(1f).padding(8.dp)) {
                // Using LazyColumn to render lines is more efficient than one huge Text
                val lines = remember(state.output) { state.output.lines() }
                
                LazyColumn(state = listState) {
                    items(lines.size) { index ->
                        Text(
                            text = lines[index],
                            color = Color.Green,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        )
                    }
                }
                
                LaunchedEffect(lines.size) {
                    if (lines.isNotEmpty()) {
                        listState.animateScrollToItem(lines.lastIndex)
                    }
                }
            }
            
            // Input Area
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.DarkGray)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 16.sp),
                    cursorBrush = SolidColor(Color.Green),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        viewModel.sendCommand(input)
                        input = ""
                    })
                )
                
                IconButton(onClick = {
                    viewModel.sendCommand(input)
                    input = ""
                }) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White)
                }
            }
        }
    }
}
