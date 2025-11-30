package com.ble1st.connectias.feature.utilities.encoding

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for EncodingProvider.
 */
class EncodingProviderTest {

    private val encodingProvider = EncodingProvider()

    @Test
    fun `test Base64 encoding and decoding`() = runTest {
        val text = "Hello World"
        val encoded = encodingProvider.encode(text, EncodingProvider.EncodingType.BASE64)
        val decoded = encodingProvider.decode(encoded!!, EncodingProvider.EncodingType.BASE64)
        
        assertNotNull(encoded)
        assertNotNull(decoded)
        assertEquals(text, decoded)
    }

    @Test
    fun `test URL encoding and decoding`() = runTest {
        val text = "Hello World & More"
        val encoded = encodingProvider.encode(text, EncodingProvider.EncodingType.URL)
        val decoded = encodingProvider.decode(encoded!!, EncodingProvider.EncodingType.URL)
        
        assertNotNull(encoded)
        assertNotNull(decoded)
        assertEquals(text, decoded)
    }

    @Test
    fun `test Hex encoding and decoding`() = runTest {
        val text = "Hello"
        val encoded = encodingProvider.encode(text, EncodingProvider.EncodingType.HEX)
        val decoded = encodingProvider.decode(encoded!!, EncodingProvider.EncodingType.HEX)
        
        assertNotNull(encoded)
        assertNotNull(decoded)
        assertEquals(text, decoded)
    }

    @Test
    fun `test empty string encoding`() = runTest {
        val encoded = encodingProvider.encode("", EncodingProvider.EncodingType.BASE64)
        assertNotNull(encoded)
    }
}

