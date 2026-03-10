package com.sidekick.opt_pal.di

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.sidekick.opt_pal.BuildConfig
import com.sidekick.opt_pal.core.security.SecurityManager
import com.sidekick.opt_pal.data.local.DocumentDao
import com.sidekick.opt_pal.data.local.SecureVaultDatabase
import com.sidekick.opt_pal.data.ml.MLKitOCRService
import com.sidekick.opt_pal.data.repository.SecureDocumentRepository
import java.security.SecureRandom

/**
 * Simple Dependency Injection container
 * For production, consider using Hilt or Koin
 */
object AppDependencies {
    
    private lateinit var securityManager: SecurityManager
    private lateinit var database: SecureVaultDatabase
    private lateinit var ocrService: MLKitOCRService
    private lateinit var documentRepository: SecureDocumentRepository
    
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    
    /**
     * Initialize all dependencies
     * Call this in Application.onCreate()
     */
    fun initialize(context: Context, geminiApiKey: String) {
        securityManager = SecurityManager(context)
        
        // Generate a secure passphrase for SQLCipher
        // In production, this should be derived from user credentials or stored securely
        val dbPassphrase = getOrCreateDatabasePassphrase()
        database = SecureVaultDatabase.getInstance(context, dbPassphrase)
        
        ocrService = MLKitOCRService(context)
        
        documentRepository = SecureDocumentRepository(
            context = context,
            documentDao = database.documentDao(),
            securityManager = securityManager,
            ocrService = ocrService,
            firestore = firestore,
            storage = storage,
            auth = auth
        )
    }
    
    fun getSecurityManager(): SecurityManager = securityManager
    fun getDatabase(): SecureVaultDatabase = database
    fun getDocumentDao(): DocumentDao = database.documentDao()
    fun getOCRService(): MLKitOCRService = ocrService
    fun getDocumentRepository(): SecureDocumentRepository = documentRepository
    
    // Firebase instances are accessed directly as properties (firestore, storage, auth)
    
    /**
     * Get or create a database passphrase
     * Stored in encrypted SharedPreferences
     */
    private fun getOrCreateDatabasePassphrase(): ByteArray {
        val prefs = securityManager.encryptedPrefs
        val key = "db_passphrase"
        
        val existingPassphrase = prefs.getString(key, null)
        
        return if (existingPassphrase != null) {
            android.util.Base64.decode(existingPassphrase, android.util.Base64.DEFAULT)
        } else {
            // Generate new secure passphrase
            val newPassphrase = ByteArray(32)
            SecureRandom().nextBytes(newPassphrase)
            
            val encoded = android.util.Base64.encodeToString(newPassphrase, android.util.Base64.DEFAULT)
            prefs.edit().putString(key, encoded).apply()
            
            newPassphrase
        }
    }
}
