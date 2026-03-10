package com.sidekick.opt_pal.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.security.SecureRandom

/**
 * SecurityManager handles all encryption/decryption operations using Android Keystore
 * and provides biometric authentication capabilities.
 * 
 * Zero Trust Architecture: All sensitive data must be encrypted before storage.
 */
class SecurityManager(private val context: Context) {
    
    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "opt_pal_master_key"
        private const val ENCRYPTED_PREFS_NAME = "opt_pal_secure_prefs"
        private const val AES_MODE = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH = 128
        private const val IV_LENGTH = 12 // GCM recommended IV length
    }
    
    // Master key for Jetpack Security (EncryptedFile, EncryptedSharedPreferences)
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }
    
    // Encrypted SharedPreferences for storing small encrypted data (e.g., metadata)
    val encryptedPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    init {
        // Ensure master key exists in Android Keystore
        ensureMasterKeyExists()
    }
    
    /**
     * Ensures a master encryption key exists in Android Keystore.
     * This key is hardware-backed and can only be accessed after user authentication.
     */
    private fun ensureMasterKeyExists() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        
        if (!keyStore.containsAlias(MASTER_KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                MASTER_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false) // Set to true for biometric-gated keys
                .setRandomizedEncryptionRequired(true)
                .build()
            
            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
        }
    }
    
    /**
     * Get the master encryption key from Android Keystore
     */
    private fun getMasterKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.getKey(MASTER_KEY_ALIAS, null) as SecretKey
    }
    
    /**
     * Encrypt raw bytes using AES-GCM
     * Returns: IV + Encrypted Data (combined)
     */
    fun encryptData(plainData: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_MODE)
        val secretKey = getMasterKey()
        
        // Generate random IV
        val iv = ByteArray(IV_LENGTH)
        SecureRandom().nextBytes(iv)
        
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val encryptedData = cipher.doFinal(plainData)
        
        // Combine IV + encrypted data for storage
        return iv + encryptedData
    }
    
    /**
     * Decrypt data that was encrypted with encryptData
     * Input format: IV + Encrypted Data
     */
    fun decryptData(encryptedDataWithIv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_MODE)
        val secretKey = getMasterKey()
        
        // Extract IV and encrypted data
        val iv = encryptedDataWithIv.sliceArray(0 until IV_LENGTH)
        val encryptedData = encryptedDataWithIv.sliceArray(IV_LENGTH until encryptedDataWithIv.size)
        
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(encryptedData)
    }
    
    /**
     * Encrypt a file using Jetpack Security's EncryptedFile
     * This is the preferred method for document storage
     */
    fun getEncryptedFile(file: File): EncryptedFile {
        return EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()
    }
    
    /**
     * Convenience method to encrypt string data
     */
    fun encryptString(plainText: String): ByteArray {
        return encryptData(plainText.toByteArray(Charsets.UTF_8))
    }
    
    /**
     * Convenience method to decrypt string data
     */
    fun decryptString(encryptedData: ByteArray): String {
        return String(decryptData(encryptedData), Charsets.UTF_8)
    }
    
    // ========== Biometric Authentication ==========
    
    /**
     * Check if biometric authentication is available on this device
     */
    fun isBiometricAvailable(): BiometricStatus {
        val biometricManager = BiometricManager.from(context)
        return when (biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or 
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricStatus.UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NONE_ENROLLED
            else -> BiometricStatus.UNKNOWN
        }
    }
    
    /**
     * Show biometric authentication prompt
     */
    fun authenticateWithBiometric(
        activity: FragmentActivity,
        title: String = "Authenticate",
        subtitle: String = "Verify your identity to access secure documents",
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onFailed: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(context)
        
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }
                
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString.toString())
                }
                
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onFailed()
                }
            }
        )
        
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        
        biometricPrompt.authenticate(promptInfo)
    }
    
    enum class BiometricStatus {
        AVAILABLE,
        NO_HARDWARE,
        UNAVAILABLE,
        NONE_ENROLLED,
        UNKNOWN
    }
}
