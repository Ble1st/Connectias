package com.ble1st.connectias.feature.securenotes.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ble1st.connectias.feature.securenotes.SecureNotesProvider
import com.ble1st.connectias.feature.securenotes.models.NoteCategory
import com.ble1st.connectias.feature.securenotes.models.NoteSearchResult
import com.ble1st.connectias.feature.securenotes.models.SecureNote
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for Secure Notes screen.
 */
@HiltViewModel
class SecureNotesViewModel @Inject constructor(
    private val secureNotesProvider: SecureNotesProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(SecureNotesUiState())
    val uiState: StateFlow<SecureNotesUiState> = _uiState.asStateFlow()

    val notes = secureNotesProvider.notes
    val isUnlocked = secureNotesProvider.isUnlocked

    private var searchJob: Job? = null

    init {
        checkBiometricAvailability()
    }

    private fun checkBiometricAvailability() {
        _uiState.update { state ->
            state.copy(isBiometricAvailable = secureNotesProvider.isBiometricAvailable())
        }
    }

    /**
     * Unlocks the secure notes vault.
     */
    fun unlock() {
        secureNotesProvider.unlock()
    }

    /**
     * Handles successful biometric/device credential authentication.
     */
    fun onAuthenticationSucceeded() {
        secureNotesProvider.unlock()
        _uiState.update { it.copy(snackbarMessage = "Vault unlocked") }
    }

    /**
     * Handles authentication errors.
     */
    fun onAuthenticationError(message: String) {
        _uiState.update { it.copy(snackbarMessage = message) }
    }

    /**
     * Locks the vault.
     */
    fun lock() {
        secureNotesProvider.lock()
        _uiState.update { it.copy(
            selectedNote = null,
            decryptedContent = null
        ) }
    }

    /**
     * Creates a new note.
     */
    fun createNote(
        title: String,
        content: String,
        category: NoteCategory = NoteCategory.GENERAL,
        tags: List<String> = emptyList()
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                secureNotesProvider.createNote(title, content, category, tags)
                _uiState.update { it.copy(
                    isLoading = false,
                    showEditor = false,
                    snackbarMessage = "Note created successfully"
                ) }
            } catch (e: Exception) {
                Timber.e(e, "Error creating note")
                _uiState.update { it.copy(
                    isLoading = false,
                    snackbarMessage = "Error creating note: ${e.message}"
                ) }
            }
        }
    }

    /**
     * Updates an existing note.
     */
    fun updateNote(
        id: String,
        title: String? = null,
        content: String? = null,
        category: NoteCategory? = null,
        tags: List<String>? = null,
        isPinned: Boolean? = null
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                secureNotesProvider.updateNote(id, title, content, category, tags, isPinned)
                _uiState.update { it.copy(
                    isLoading = false,
                    showEditor = false,
                    snackbarMessage = "Note updated successfully"
                ) }
            } catch (e: Exception) {
                Timber.e(e, "Error updating note")
                _uiState.update { it.copy(
                    isLoading = false,
                    snackbarMessage = "Error updating note: ${e.message}"
                ) }
            }
        }
    }

    /**
     * Deletes a note (soft delete).
     */
    fun deleteNote(id: String) {
        viewModelScope.launch {
            try {
                secureNotesProvider.deleteNote(id)
                _uiState.update { it.copy(
                    selectedNote = null,
                    decryptedContent = null,
                    snackbarMessage = "Note moved to trash"
                ) }
            } catch (e: Exception) {
                Timber.e(e, "Error deleting note")
                _uiState.update { it.copy(snackbarMessage = "Error deleting note: ${e.message}") }
            }
        }
    }

    /**
     * Selects a note and decrypts its content.
     */
    fun selectNote(note: SecureNote) {
        viewModelScope.launch {
            _uiState.update { it.copy(
                selectedNote = note,
                isDecrypting = true
            ) }
            try {
                val content = secureNotesProvider.getNoteContent(note.id)
                _uiState.update { it.copy(
                    decryptedContent = content,
                    isDecrypting = false
                ) }
            } catch (e: Exception) {
                Timber.e(e, "Error decrypting note")
                _uiState.update { it.copy(
                    isDecrypting = false,
                    snackbarMessage = "Error decrypting note: ${e.message}"
                ) }
            }
        }
    }

    /**
     * Clears the selected note.
     */
    fun clearSelectedNote() {
        _uiState.update { it.copy(
            selectedNote = null,
            decryptedContent = null
        ) }
    }

    /**
     * Toggles pin status of a note.
     */
    fun togglePin(note: SecureNote) {
        viewModelScope.launch {
            secureNotesProvider.updateNote(note.id, isPinned = !note.isPinned)
        }
    }

    /**
     * Searches notes with debouncing.
     */
    fun search(query: String) {
        _uiState.update { it.copy(searchQuery = query) }

        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }

        searchJob = viewModelScope.launch {
            delay(300) // Debounce
            try {
                val results = secureNotesProvider.searchNotes(query)
                _uiState.update { it.copy(searchResults = results) }
            } catch (e: Exception) {
                Timber.e(e, "Error searching notes")
            }
        }
    }

    /**
     * Sets the category filter.
     */
    fun setFilterCategory(category: NoteCategory?) {
        _uiState.update { it.copy(filterCategory = category) }
    }

    /**
     * Shows the editor for creating a new note.
     */
    fun showNewNoteEditor() {
        _uiState.update { it.copy(
            showEditor = true,
            editingNote = null
        ) }
    }

    /**
     * Shows the editor for editing an existing note.
     */
    fun showEditNoteEditor(note: SecureNote) {
        viewModelScope.launch {
            val content = secureNotesProvider.getNoteContent(note.id)
            _uiState.update { it.copy(
                showEditor = true,
                editingNote = note,
                editingContent = content
            ) }
        }
    }

    /**
     * Hides the editor.
     */
    fun hideEditor() {
        _uiState.update { it.copy(
            showEditor = false,
            editingNote = null,
            editingContent = null
        ) }
    }

    /**
     * Clears the snackbar message.
     */
    fun clearSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

}

/**
 * UI state for Secure Notes screen.
 */
data class SecureNotesUiState(
    val isLoading: Boolean = false,
    val isDecrypting: Boolean = false,
    val isBiometricAvailable: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<NoteSearchResult> = emptyList(),
    val filterCategory: NoteCategory? = null,
    val selectedNote: SecureNote? = null,
    val decryptedContent: String? = null,
    val showEditor: Boolean = false,
    val editingNote: SecureNote? = null,
    val editingContent: String? = null,
    val showTrash: Boolean = false,
    val exportedContent: String? = null,
    val snackbarMessage: String? = null
)

