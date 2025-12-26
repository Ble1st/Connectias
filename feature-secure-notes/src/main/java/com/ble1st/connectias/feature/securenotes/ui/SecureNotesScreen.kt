package com.ble1st.connectias.feature.securenotes.ui
import androidx.compose.material.icons.automirrored.filled.Note

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ble1st.connectias.feature.securenotes.models.NoteCategory
import com.ble1st.connectias.feature.securenotes.models.SecureNote
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Secure Notes screen for encrypted note management.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecureNotesScreen(
    viewModel: SecureNotesViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onRequestUnlock: () -> Unit = { viewModel.unlock() }
) {
    val uiState by viewModel.uiState.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val isUnlocked by viewModel.isUnlocked.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showNoteDetails by remember { mutableStateOf(false) }
    val noteDetailsSheetState = rememberModalBottomSheetState()

    // Show snackbar messages
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            scope.launch {
                snackbarHostState.showSnackbar(message)
                viewModel.clearSnackbarMessage()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Secure Notes") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isUnlocked) {
                        IconButton(onClick = { viewModel.lock() }) {
                            Icon(Icons.Default.Lock, contentDescription = "Lock")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (isUnlocked) {
                FloatingActionButton(
                    onClick = { viewModel.showNewNoteEditor() }
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Note")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (!isUnlocked) {
                LockedState(
                    isBiometricAvailable = uiState.isBiometricAvailable,
                    onUnlock = onRequestUnlock
                )
            } else {
                NotesContent(
                    notes = notes.filter { it.deletedAt == null },
                    searchQuery = uiState.searchQuery,
                    filterCategory = uiState.filterCategory,
                    onSearchChange = { viewModel.search(it) },
                    onFilterCategoryChange = { viewModel.setFilterCategory(it) },
                    onNoteClick = { note ->
                        viewModel.selectNote(note)
                        showNoteDetails = true
                    },
                    onTogglePin = { viewModel.togglePin(it) }
                )
            }
        }

        // Note details sheet
        if (showNoteDetails && uiState.selectedNote != null) {
            ModalBottomSheet(
                onDismissRequest = {
                    viewModel.clearSelectedNote()
                },
                sheetState = noteDetailsSheetState
            ) {
                NoteDetailsSheet(
                    note = uiState.selectedNote!!,
                    decryptedContent = uiState.decryptedContent,
                    isDecrypting = uiState.isDecrypting,
                    onEdit = {
                        viewModel.showEditNoteEditor(uiState.selectedNote!!)
                    },
                    onDelete = {
                        viewModel.deleteNote(uiState.selectedNote!!.id)
                    },
                    onTogglePin = { viewModel.togglePin(uiState.selectedNote!!) }
                )
            }
        }

        // Editor dialog
        if (uiState.showEditor) {
            NoteEditorDialog(
                note = uiState.editingNote,
                initialContent = uiState.editingContent,
                onDismiss = { viewModel.hideEditor() },
                onSave = { title, content, category, tags ->
                    if (uiState.editingNote != null) {
                        viewModel.updateNote(
                            uiState.editingNote!!.id,
                            title = title,
                            content = content,
                            category = category,
                            tags = tags
                        )
                    } else {
                        viewModel.createNote(title, content, category, tags)
                    }
                }
            )
        }
    }
}

@Composable
private fun LockedState(
    isBiometricAvailable: Boolean,
    onUnlock: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Secure Notes Vault",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Your notes are protected with AES-256-GCM encryption",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onUnlock,
            modifier = Modifier.fillMaxWidth(0.7f)
        ) {
            Icon(
                if (isBiometricAvailable) Icons.Default.Fingerprint else Icons.Default.LockOpen,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isBiometricAvailable) "Unlock with Biometrics" else "Unlock")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotesContent(
    notes: List<SecureNote>,
    searchQuery: String,
    filterCategory: NoteCategory?,
    onSearchChange: (String) -> Unit,
    onFilterCategoryChange: (NoteCategory?) -> Unit,
    onNoteClick: (SecureNote) -> Unit,
    onTogglePin: (SecureNote) -> Unit
) {
    val filteredNotes = notes.filter { note ->
        (filterCategory == null || note.category == filterCategory) &&
        (searchQuery.isBlank() || note.title.contains(searchQuery, ignoreCase = true))
    }

    val pinnedNotes = filteredNotes.filter { it.isPinned }
    val unpinnedNotes = filteredNotes.filter { !it.isPinned }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Search bar
        item {
            SearchBar(
                query = searchQuery,
                onQueryChange = onSearchChange
            )
        }

        // Category filter
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        onClick = { onFilterCategoryChange(null) },
                        label = { Text("All") },
                        selected = filterCategory == null
                    )
                }
                items(NoteCategory.entries) { category ->
                    FilterChip(
                        onClick = { onFilterCategoryChange(category) },
                        label = { Text(category.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        selected = filterCategory == category
                    )
                }
            }
        }

        // Pinned notes section
        if (pinnedNotes.isNotEmpty()) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Pinned",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            items(pinnedNotes) { note ->
                NoteCard(
                    note = note,
                    onClick = { onNoteClick(note) },
                    onTogglePin = { onTogglePin(note) }
                )
            }
        }

        // Other notes
        if (unpinnedNotes.isNotEmpty()) {
            if (pinnedNotes.isNotEmpty()) {
                item {
                    Text(
                        text = "Other Notes",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            items(unpinnedNotes.sortedByDescending { it.modifiedAt }) { note ->
                NoteCard(
                    note = note,
                    onClick = { onNoteClick(note) },
                    onTogglePin = { onTogglePin(note) }
                )
            }
        }

        // Empty state
        if (filteredNotes.isEmpty()) {
            item {
                EmptyNotesState(
                    hasFilter = filterCategory != null || searchQuery.isNotBlank()
                )
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search notes...") },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotBlank()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear")
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large, // Expressive 'Large' is 28dp, fitting for search bars
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        singleLine = true
    )
}

@Composable
private fun NoteCard(
    note: SecureNote,
    onClick: () -> Unit,
    onTogglePin: () -> Unit
) {
    val backgroundColor = try {
        Color(note.color.hex.toColorInt())
    } catch (e: Exception) {
        MaterialTheme.colorScheme.surfaceContainer
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = MaterialTheme.shapes.medium // Expressive 'Medium' is 16dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = onTogglePin,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        if (note.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (note.isPinned) "Unpin" else "Pin",
                        modifier = Modifier.size(18.dp),
                        tint = if (note.isPinned) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = { },
                    label = {
                        Text(
                            text = note.category.name.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                )

                Text(
                    text = formatDate(note.modifiedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (note.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    note.tags.take(3).forEach { tag ->
                        Text(
                            text = "#$tag",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (note.tags.size > 3) {
                        Text(
                            text = "+${note.tags.size - 3}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyNotesState(hasFilter: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Note,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (hasFilter) "No Notes Found" else "No Notes Yet",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (hasFilter) "Try adjusting your search or filters"
                   else "Tap the + button to create your first secure note",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NoteDetailsSheet(
    note: SecureNote,
    decryptedContent: String?,
    isDecrypting: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = note.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            Row {
                IconButton(onClick = onTogglePin) {
                    Icon(
                        if (note.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (note.isPinned) "Unpin" else "Pin"
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Metadata
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = note.category.name.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Modified: ${formatDate(note.modifiedAt)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (note.tags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                note.tags.forEach { tag ->
                    AssistChip(
                        onClick = { },
                        label = { Text("#$tag") }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Content
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                when {
                    isDecrypting -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Decrypting...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    decryptedContent != null -> {
                        Text(
                            text = decryptedContent,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    else -> {
                        Text(
                            text = "Unable to decrypt content",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Delete Note?") },
            text = { Text("This note will be moved to trash. You can restore it later.") },
            confirmButton = {
                Button(onClick = {
                    onDelete()
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteEditorDialog(
    note: SecureNote?,
    initialContent: String?,
    onDismiss: () -> Unit,
    onSave: (String, String, NoteCategory, List<String>) -> Unit
) {
    var title by remember { mutableStateOf(note?.title ?: "") }
    var content by remember { mutableStateOf(initialContent ?: "") }
    var category by remember { mutableStateOf(note?.category ?: NoteCategory.GENERAL) }
    var tagsText by remember { mutableStateOf(note?.tags?.joinToString(", ") ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (note != null) "Edit Note" else "New Note") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    minLines = 5
                )

                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    NoteCategory.entries.take(6).forEach { cat ->
                        FilterChip(
                            onClick = { category = cat },
                            label = { Text(cat.name.lowercase().replaceFirstChar { it.uppercase() }) },
                            selected = category == cat
                        )
                    }
                }

                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    label = { Text("Tags (comma separated)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val tags = tagsText.split(",")
                        .map { it.trim() }
                        .filter { it.isNotBlank() }
                    onSave(title.ifBlank { "Untitled" }, content, category, tags)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
