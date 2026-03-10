package com.sidekick.opt_pal.data.local

import androidx.room.*
import com.sidekick.opt_pal.data.model.SecureDocument
import com.sidekick.opt_pal.data.model.DocumentType
import com.sidekick.opt_pal.data.model.ExtractionStatus
import kotlinx.coroutines.flow.Flow

/**
 * DAO for accessing encrypted documents in the local database
 */
@Dao
interface DocumentDao {
    
    @Query("SELECT * FROM documents WHERE userId = :userId ORDER BY uploadedAt DESC")
    fun getAllDocuments(userId: String): Flow<List<SecureDocument>>
    
    @Query("SELECT * FROM documents WHERE userId = :userId AND documentType = :type ORDER BY uploadedAt DESC")
    fun getDocumentsByType(userId: String, type: DocumentType): Flow<List<SecureDocument>>
    
    @Query("SELECT * FROM documents WHERE id = :documentId LIMIT 1")
    suspend fun getDocumentById(documentId: String): SecureDocument?
    
    @Query("SELECT * FROM documents WHERE userId = :userId AND extractionStatus = :status")
    fun getDocumentsByStatus(userId: String, status: ExtractionStatus): Flow<List<SecureDocument>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: SecureDocument)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocuments(documents: List<SecureDocument>)
    
    @Update
    suspend fun updateDocument(document: SecureDocument)
    
    @Delete
    suspend fun deleteDocument(document: SecureDocument)
    
    @Query("DELETE FROM documents WHERE userId = :userId")
    suspend fun deleteAllUserDocuments(userId: String)
    
    @Query("SELECT COUNT(*) FROM documents WHERE userId = :userId")
    suspend fun getDocumentCount(userId: String): Int
    
    @Query("SELECT COUNT(*) FROM documents WHERE userId = :userId AND extractionStatus = 'PENDING'")
    suspend fun getPendingExtractionCount(userId: String): Int
}
