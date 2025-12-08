package com.ble1st.connectias.feature.usb.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.crypto.spec.SecretKeySpec

class UsbCryptoProviderTest {

    private val provider = UsbCryptoProvider()

    @Test
    fun `encrypt and decrypt roundtrip`() {
        val key = provider.generateKey()
        val data = "usb-secret-data".toByteArray(Charsets.UTF_8)

        val encrypted = provider.encrypt(data, key)
        val decrypted = provider.decrypt(encrypted, key)

        assertArrayEquals(data, decrypted)
    }

    @Test
    fun `createKey enforces 32 byte length`() {
        val valid = provider.createKey(ByteArray(32) { 1 })
        assertEquals(32, valid.encoded.size)

        assertThrows(IllegalArgumentException::class.java) {
            provider.createKey(ByteArray(16) { 1 })
        }
    }

    @Test
    fun `decrypt fails with wrong key`() {
        val key = provider.generateKey()
        val data = "usb-secret-data".toByteArray(Charsets.UTF_8)
        val encrypted = provider.encrypt(data, key)

        val wrongKey = SecretKeySpec(ByteArray(32) { 2 }, "AES")

        assertThrows(Exception::class.java) {
            provider.decrypt(encrypted, wrongKey)
        }
    }
}
