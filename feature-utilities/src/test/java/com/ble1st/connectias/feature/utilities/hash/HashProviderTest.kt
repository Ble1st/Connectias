package com.ble1st.connectias.feature.utilities.hash

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for HashProvider.
 */
class HashProviderTest {

    private val hashProvider = HashProvider()

    @Test
    fun `test MD5 hash generation`() = runTest {
        val text = "Hello World"
        val hash = hashProvider.calculateTextHash(text, HashProvider.HashAlgorithm.MD5)
        
        assertNotNull(hash)
        assertEquals("b10a8db164e0754105b7a99be72e3fe5", hash.lowercase())
    }

    @Test
    fun `test SHA-256 hash generation`() = runTest {
        val text = "Hello World"
        val hash = hashProvider.calculateTextHash(text, HashProvider.HashAlgorithm.SHA256)
        
        assertNotNull(hash)
        assertEquals("a591a6d40bf420404a011733cfb7b190d62c65bf0bcda32b57b277d9ad9f146e", hash.lowercase())
    }

    @Test
    fun `test SHA-512 hash generation`() = runTest {
        val text = "test"
        val hash = hashProvider.calculateTextHash(text, HashProvider.HashAlgorithm.SHA512)
        
        assertNotNull(hash)
        assertEquals(128, hash.length) // SHA-512 produces 128 hex characters
    }

    @Test
    fun `test empty string hash`() = runTest {
        val hash = hashProvider.calculateTextHash("", HashProvider.HashAlgorithm.SHA256)
        assertNotNull(hash)
        assertTrue(hash.isNotEmpty())
    }

    @Test
    fun `test hash consistency`() = runTest {
        val text = "Consistent Test"
        val hash1 = hashProvider.calculateTextHash(text, HashProvider.HashAlgorithm.SHA256)
        val hash2 = hashProvider.calculateTextHash(text, HashProvider.HashAlgorithm.SHA256)
        
        assertEquals(hash1, hash2)
    }
}

