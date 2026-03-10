package com.sidekick.opt_pal.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.sidekick.opt_pal.data.model.SecureDocument
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

/**
 * The main Room database for OPT_Pal with SQLCipher encryption.
 * All local data is encrypted at rest.
 */
@Database(
    entities = [SecureDocument::class],
    version = 1,
    exportSchema = false
)
abstract class SecureVaultDatabase : RoomDatabase() {
    
    abstract fun documentDao(): DocumentDao
    
    companion object {
        private const val DATABASE_NAME = "opt_pal_secure.db"
        
        @Volatile
        private var INSTANCE: SecureVaultDatabase? = null
        
        /**
         * Get database instance with SQLCipher encryption.
         * The passphrase is derived from Android Keystore.
         * 
         * @param context Application context
         * @param passphrase Database encryption passphrase (from SecurityManager)
         */
        fun getInstance(context: Context, passphrase: ByteArray): SecureVaultDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = buildDatabase(context, passphrase)
                INSTANCE = instance
                instance
            }
        }
        
        private fun buildDatabase(context: Context, passphrase: ByteArray): SecureVaultDatabase {
            // Create SQLCipher support factory
            val supportFactory = SupportFactory(passphrase)
            
            return Room.databaseBuilder(
                context.applicationContext,
                SecureVaultDatabase::class.java,
                DATABASE_NAME
            )
                .openHelperFactory(supportFactory)
                .build()
        }
        
        /**
         * Clear the database instance (useful for testing or logout)
         */
        fun clearInstance() {
            INSTANCE = null
        }
    }
}
