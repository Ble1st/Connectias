package com.ble1st.connectias.feature.securenotes.ui

import com.ble1st.connectias.feature.securenotes.SecureNotesProvider
import com.ble1st.connectias.feature.securenotes.models.NoteCategory
import com.ble1st.connectias.feature.securenotes.models.NoteSearchResult
import com.ble1st.connectias.feature.securenotes.models.SecureNote
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SecureNotesViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var notesFlow: MutableStateFlow<List<SecureNote>>
    private lateinit var isUnlockedFlow: MutableStateFlow<Boolean>
    private lateinit var provider: SecureNotesProvider
    private lateinit var viewModel: SecureNotesViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        notesFlow = MutableStateFlow(emptyList())
        isUnlockedFlow = MutableStateFlow(true)
        provider = mockk(relaxed = true)

        every { provider.notes } returns notesFlow
        every { provider.isUnlocked } returns isUnlockedFlow
        every { provider.isBiometricAvailable() } returns false
        every { provider.unlock() } answers { isUnlockedFlow.value = true }
        every { provider.lock() } answers { isUnlockedFlow.value = false }

        coEvery { provider.createNote(any(), any(), any(), any()) } coAnswers {
            val note = SecureNote(
                title = arg(0),
                content = arg(1),
                category = arg(2),
                tags = arg(3)
            )
            notesFlow.value += note
            note
        }
        coEvery { provider.updateNote(any(), any(), any(), any(), any(), any()) } coAnswers {
            val id = arg<String>(0)
            val existing = notesFlow.value.find { it.id == id } ?: return@coAnswers null
            val updated = existing.copy(
                title = arg<String?>(1) ?: existing.title,
                content = arg<String?>(2) ?: existing.content,
                category = arg<NoteCategory?>(3) ?: existing.category,
                tags = arg<List<String>?>(4) ?: existing.tags,
                isPinned = arg<Boolean?>(5) ?: existing.isPinned
            )
            notesFlow.value = notesFlow.value.map { if (it.id == id) updated else it }
            updated
        }
        coEvery { provider.deleteNote(any()) } coAnswers {
            val id = arg<String>(0)
            val existing = notesFlow.value.find { it.id == id } ?: return@coAnswers false
            val deleted = existing.copy(deletedAt = System.currentTimeMillis())
            notesFlow.value = notesFlow.value.map { if (it.id == id) deleted else it }
            true
        }
        coEvery { provider.permanentlyDeleteNote(any()) } coAnswers {
            val id = arg<String>(0)
            val exists = notesFlow.value.any { it.id == id }
            if (exists) {
                notesFlow.value = notesFlow.value.filterNot { it.id == id }
            }
            exists
        }
        coEvery { provider.getNoteContent(any()) } coAnswers {
            val id = arg<String>(0)
            notesFlow.value.find { it.id == id }?.content
        }
        coEvery { provider.searchNotes(any()) } coAnswers {
            val query = arg<String>(0).lowercase()
            notesFlow.value.filter { it.deletedAt == null }
                .filter {
                    it.title.lowercase().contains(query) ||
                        it.content.lowercase().contains(query) ||
                        it.tags.any { tag -> tag.lowercase().contains(query) }
                }
                .map { note ->
                    val location = when {
                        note.title.lowercase().contains(query) -> com.ble1st.connectias.feature.securenotes.models.MatchLocation.TITLE
                        note.tags.any { tag -> tag.lowercase().contains(query) } -> com.ble1st.connectias.feature.securenotes.models.MatchLocation.TAGS
                        else -> com.ble1st.connectias.feature.securenotes.models.MatchLocation.CONTENT
                    }
                    NoteSearchResult(note, location, note.title)
                }
        }
        coEvery { provider.exportNotes(any(), any()) } coAnswers {
            val ids = arg<List<String>>(0)
            notesFlow.value.filter { it.id in ids }.joinToString("\n") { it.title }
        }

        viewModel = SecureNotesViewModel(provider)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `createNote updates snackbar and stores note`() = runTest(dispatcher) {
        viewModel.createNote(
            title = "Title",
            content = "Secret",
            category = NoteCategory.PERSONAL,
            tags = listOf("tag1")
        )
        advanceUntilIdle()

        assertEquals(1, notesFlow.value.size)
        assertEquals("Note created successfully", viewModel.uiState.value.snackbarMessage)
        assertTrue(viewModel.uiState.value.isLoading.not())
        assertTrue(viewModel.uiState.value.showEditor.not())
    }

    @Test
    fun `selectNote decrypts content into uiState`() = runTest(dispatcher) {
        val note = SecureNote(title = "Hello", content = "Decrypted body")
        notesFlow.value = listOf(note)
        viewModel.selectNote(note)
        advanceUntilIdle()

        assertEquals("Decrypted body", viewModel.uiState.value.decryptedContent)
        assertEquals(note.id, viewModel.uiState.value.selectedNote?.id)
    }

    @Test
    fun `lock clears selection and decrypted content`() = runTest(dispatcher) {
        val note = SecureNote(title = "Hello", content = "Body")
        notesFlow.value = listOf(note)
        viewModel.selectNote(note)
        advanceUntilIdle()

        viewModel.lock()

        assertEquals(null, viewModel.uiState.value.selectedNote)
        assertEquals(null, viewModel.uiState.value.decryptedContent)
    }

    @Test
    fun `search uses debounce and updates results`() = runTest(dispatcher) {
        val first = SecureNote(title = "First", content = "contains secret")
        val second = SecureNote(title = "Second", content = "nothing here")
        notesFlow.value = listOf(first, second)

        viewModel.search("secret")
        advanceTimeBy(400)
        advanceUntilIdle()

        val results = viewModel.uiState.value.searchResults
        assertEquals(1, results.size)
        assertEquals("First", results.first().note.title)
    }
}
