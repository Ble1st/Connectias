package com.ble1st.connectias.feature.securenotes

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ble1st.connectias.feature.securenotes.models.ExportFormat
import com.ble1st.connectias.feature.securenotes.models.NoteCategory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore
import javax.crypto.spec.SecretKeySpec

@RunWith(AndroidJUnit4::class)
class SecureNotesProviderInstrumentedTest {

    private lateinit var context: Context
    private lateinit var provider: SecureNotesProvider

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        provider = SecureNotesProvider(context)
        injectInMemoryKeystore()
        provider.unlock()
    }

    @Test
    fun encryptAndDecryptRoundTrip() = runBlocking {
        val cipherText = provider.encrypt("hello-world")
        val plain = provider.decrypt(cipherText)

        assertEquals("hello-world", plain)
    }

    @Test
    fun createAndExportPlainText() = runBlocking {
        val note = provider.createNote("Title", "Body", NoteCategory.PERSONAL, listOf("tag"))

        val content = provider.getNoteContent(note.id)
        assertEquals("Body", content)

        val exported = provider.exportNotes(listOf(note.id), ExportFormat.PLAIN_TEXT)
        assertTrue(exported.contains("Body"))
    }

    @Test
    fun deleteAndPermanentDelete() = runBlocking {
        val note = provider.createNote("Tmp", "To delete")

        val softDeleted = provider.deleteNote(note.id)
        assertEquals(true, softDeleted)
        val trashed = provider.getDeletedNotes()
        assertEquals(1, trashed.size)

        val removed = provider.permanentlyDeleteNote(note.id)
        assertEquals(true, removed)
        assertEquals(0, provider.getDeletedNotes().size)
    }

    private fun injectInMemoryKeystore() {
        val aliasField = SecureNotesProvider::class.java.getDeclaredField("keyAlias").apply {
            isAccessible = true
        }
        val alias = aliasField.get(provider) as String

        val keyStoreField = SecureNotesProvider::class.java.getDeclaredField("keyStore").apply {
            isAccessible = true
        }

        val memoryStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null) }
        val secretKey = SecretKeySpec(ByteArray(32) { 1 }, "AES")
        memoryStore.setEntry(alias, KeyStore.SecretKeyEntry(secretKey), null)

        keyStoreField.set(provider, memoryStore)
    }
}
