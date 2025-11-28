package com.ble1st.connectias.feature.security.encryption

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for EncryptionProvider.
 */
class EncryptionProviderTest {

    private val encryptionProvider = EncryptionProvider()

    @Test
    fun `test encryption and decryption`() = runTest {
        val plaintext = "Secret Message"
        val password = "TestPassword123"
        
        val encrypted = encryptionProvider.encryptText(plaintext, password)
        
        assertTrue(encrypted.success)
        assertNotNull(encrypted.encryptedData)
        assertTrue(encrypted.encryptedData.isNotEmpty())
        assertNotNull(encrypted.iv)
        assertTrue(encrypted.iv.isNotEmpty())
        
        val decrypted = encryptionProvider.decryptText(
            encrypted.encryptedData,
            encrypted.iv,
            password
        )
        
        assertTrue(decrypted.success)
        assertEquals(plaintext, decrypted.plaintext)
    }

    @Test
    fun `test encryption with wrong password fails`() = runTest {
        val plaintext = "Secret Message"
        val password = "TestPassword123"
        val wrongPassword = "WrongPassword"
        
        val encrypted = encryptionProvider.encryptText(plaintext, password)
        assertTrue(encrypted.success)
        
        val decrypted = encryptionProvider.decryptText(
            encrypted.encryptedData,
            encrypted.iv,
            wrongPassword
        )
        
        assertFalse(decrypted.success)
    }

    @Test
    fun `test key generation`() = runTest {
        val key = encryptionProvider.generateKey()
        
        assertNotNull(key)
        assertTrue(key.isNotEmpty())
    }

    @Test
    fun `test empty text encryption`() = runTest {
        val result = encryptionProvider.encryptText("", "password")
        // Empty text encryption might succeed but produce empty encrypted data
        assertNotNull(result)
    }
}

