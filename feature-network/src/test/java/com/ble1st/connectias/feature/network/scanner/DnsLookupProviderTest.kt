package com.ble1st.connectias.feature.network.scanner

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for DnsLookupProvider.
 */
class DnsLookupProviderTest {

    private val dnsLookupProvider = DnsLookupProvider()

    @Test
    fun `test DNS lookup for valid domain`() = runTest {
        val result = dnsLookupProvider.lookup("example.com", DnsLookupProvider.RecordType.A)
        
        assertNotNull(result)
        // Note: This test may fail if network is unavailable
        // In a real scenario, use a mock DNS resolver
    }

    @Test
    fun `test invalid domain lookup`() = runTest {
        val result = dnsLookupProvider.lookup("invalid-domain-that-does-not-exist-12345.com", DnsLookupProvider.RecordType.A)
        
        // Should handle gracefully
        assertNotNull(result)
    }

    @Test
    fun `test DNS server response time`() = runTest {
        val responseTime = dnsLookupProvider.testDnsServer("8.8.8.8")
        
        // Response time should be reasonable (less than 5 seconds)
        assertNotNull(responseTime)
        assertTrue(responseTime!! >= 0)
        assertTrue(responseTime!! < 5000)
    }
}

