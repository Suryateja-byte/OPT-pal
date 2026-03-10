package com.sidekick.opt_pal.core.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DocumentCryptoServiceTest {

    private val service = DocumentCryptoService()

    @Test
    fun encryptThenDecryptRoundTrips() {
        val original = "OPT document bytes".encodeToByteArray()

        val encrypted = service.encryptDocument(original)
        val decrypted = service.decryptDocument(encrypted.encryptedBytes, encrypted.keyBytes)

        assertArrayEquals(original, decrypted)
        assertNotEquals(original.toList(), encrypted.encryptedBytes.toList())
    }
}
