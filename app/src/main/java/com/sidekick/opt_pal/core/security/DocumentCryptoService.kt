package com.sidekick.opt_pal.core.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
private const val GCM_TAG_BITS = 128
private const val GCM_IV_LENGTH_BYTES = 12
private const val DOCUMENT_KEY_LENGTH_BYTES = 32

data class EncryptedDocumentPayload(
    val encryptedBytes: ByteArray,
    val keyBytes: ByteArray,
    val encryptionVersion: Int = 1
)

class DocumentCryptoService(
    private val secureRandom: SecureRandom = SecureRandom()
) {

    fun encryptDocument(plainBytes: ByteArray): EncryptedDocumentPayload {
        val keyBytes = ByteArray(DOCUMENT_KEY_LENGTH_BYTES).also(secureRandom::nextBytes)
        val iv = ByteArray(GCM_IV_LENGTH_BYTES).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyBytes.toSecretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        val encryptedBytes = iv + cipher.doFinal(plainBytes)
        return EncryptedDocumentPayload(
            encryptedBytes = encryptedBytes,
            keyBytes = keyBytes
        )
    }

    fun decryptDocument(encryptedBytes: ByteArray, keyBytes: ByteArray): ByteArray {
        require(encryptedBytes.size > GCM_IV_LENGTH_BYTES) { "Encrypted payload is malformed." }
        val iv = encryptedBytes.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val ciphertext = encryptedBytes.copyOfRange(GCM_IV_LENGTH_BYTES, encryptedBytes.size)
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keyBytes.toSecretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun ByteArray.toSecretKey(): SecretKey = SecretKeySpec(this, "AES")
}
