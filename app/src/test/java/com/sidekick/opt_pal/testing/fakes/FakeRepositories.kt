package com.sidekick.opt_pal.testing.fakes

import android.content.ContentResolver
import android.net.Uri
import com.google.firebase.auth.FirebaseUser
import com.sidekick.opt_pal.core.session.UserSessionProvider
import com.sidekick.opt_pal.data.model.CompleteSetupRequest
import com.sidekick.opt_pal.data.model.DocumentProcessingMode
import com.sidekick.opt_pal.data.model.DocumentMetadata
import com.sidekick.opt_pal.data.model.DocumentUploadConsent
import com.sidekick.opt_pal.data.model.SecureDocumentContent
import com.sidekick.opt_pal.data.model.Employment
import com.sidekick.opt_pal.data.model.ReportingObligation
import com.sidekick.opt_pal.data.model.UserProfile
import com.sidekick.opt_pal.data.repository.AuthRepository
import com.sidekick.opt_pal.data.repository.DashboardRepository
import com.sidekick.opt_pal.data.repository.DocumentRepository
import com.sidekick.opt_pal.data.repository.ReportingRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAuthRepository : AuthRepository {
    private val authState = MutableStateFlow<FirebaseUser?>(null)
    private val profiles = mutableMapOf<String, MutableStateFlow<UserProfile?>>()
    var completeSetupResult: Result<Unit> = Result.success(Unit)
    val completedSetupRequests = mutableListOf<CompleteSetupRequest>()

    override fun getAuthState(): Flow<FirebaseUser?> = authState

    override fun getUserProfile(uid: String): Flow<UserProfile?> =
        profiles.getOrPut(uid) { MutableStateFlow(null) }

    override suspend fun signIn(email: String, password: String): Result<Unit> = Result.success(Unit)

    override suspend fun register(email: String, password: String): Result<Unit> = Result.success(Unit)

    override suspend fun completeSetup(request: CompleteSetupRequest): Result<Unit> {
        completedSetupRequests += request
        return completeSetupResult
    }

    override suspend fun signOut() = Unit

    fun emitUser(user: FirebaseUser?) {
        authState.value = user
    }

    fun emitProfile(uid: String, profile: UserProfile?) {
        profiles.getOrPut(uid) { MutableStateFlow(null) }.value = profile
    }
}

class FakeDashboardRepository : DashboardRepository {
    private val employments = MutableStateFlow<List<Employment>>(emptyList())
    val addedEmployments = mutableListOf<Employment>()
    val deletedEmploymentIds = mutableListOf<String>()

    override fun getEmployments(uid: String): Flow<List<Employment>> = employments

    override suspend fun addEmployment(uid: String, employment: Employment): Result<Unit> {
        addedEmployments += employment
        return Result.success(Unit)
    }

    override suspend fun deleteEmployment(uid: String, employmentId: String): Result<Unit> {
        deletedEmploymentIds += employmentId
        return Result.success(Unit)
    }

    override suspend fun getEmployment(uid: String, employmentId: String): Result<Employment?> {
        return Result.success(employments.value.firstOrNull { it.id == employmentId })
    }

    fun setEmployments(items: List<Employment>) {
        employments.value = items
    }
}

class FakeReportingRepository : ReportingRepository {
    private val obligations = MutableStateFlow<List<ReportingObligation>>(emptyList())
    val addedObligations = mutableListOf<ReportingObligation>()
    val toggledObligations = mutableListOf<Triple<String, String, Boolean>>()
    val deletedObligations = mutableListOf<String>()
    val updatedObligations = mutableListOf<ReportingObligation>()
    var toggleResult: Result<Unit> = Result.success(Unit)

    override fun getReportingObligations(uid: String): Flow<List<ReportingObligation>> = obligations

    override suspend fun addObligation(uid: String, obligation: ReportingObligation): Result<Unit> {
        addedObligations += obligation
        return Result.success(Unit)
    }

    override suspend fun toggleObligationStatus(uid: String, obligationId: String, isCompleted: Boolean): Result<Unit> {
        toggledObligations += Triple(uid, obligationId, isCompleted)
        return toggleResult
    }

    override suspend fun deleteObligation(uid: String, obligationId: String): Result<Unit> {
        deletedObligations += obligationId
        return Result.success(Unit)
    }

    override suspend fun updateObligation(uid: String, obligation: ReportingObligation): Result<Unit> {
        updatedObligations += obligation
        return Result.success(Unit)
    }

    override suspend fun getObligation(uid: String, obligationId: String): Result<ReportingObligation?> {
        return Result.success(obligations.value.firstOrNull { it.id == obligationId })
    }

    fun setObligations(items: List<ReportingObligation>) {
        obligations.value = items
    }
}

class FakeDocumentRepository : DocumentRepository {
    private val documents = MutableStateFlow<List<DocumentMetadata>>(emptyList())
    var uploadResult: Result<Unit> = Result.success(Unit)
    var deleteResult: Result<Unit> = Result.success(Unit)
    val uploadRequests = mutableListOf<UploadRequest>()
    val deleteRequests = mutableListOf<DocumentMetadata>()
    val renameRequests = mutableListOf<Pair<String, String>>()
    var progressEmissions: List<Pair<Long, Long>> = emptyList()
    var holdUploadCompletion: Boolean = false
    var uploadCompletion: CompletableDeferred<Unit>? = null
    var documentContentResult: Result<SecureDocumentContent> = Result.failure(IllegalStateException("No content stubbed"))

    override fun getDocuments(uid: String): Flow<List<DocumentMetadata>> = documents

    override suspend fun uploadDocument(
        uid: String,
        fileUri: Uri,
        fileName: String,
        userTag: String,
        consent: DocumentUploadConsent,
        contentResolver: ContentResolver,
        onProgress: (bytesSent: Long, totalBytes: Long) -> Unit
    ): Result<Unit> {
        uploadRequests += UploadRequest(
            uid = uid,
            fileName = fileName,
            userTag = userTag,
            processingMode = consent.processingMode
        )
        progressEmissions.forEach { (sent, total) -> onProgress(sent, total) }
        if (holdUploadCompletion) {
            val latch = CompletableDeferred<Unit>()
            uploadCompletion = latch
            latch.await()
            uploadCompletion = null
        }
        return uploadResult
    }

    override suspend fun deleteDocument(uid: String, document: DocumentMetadata): Result<Unit> {
        deleteRequests += document
        return deleteResult
    }

    override suspend fun renameDocument(uid: String, document: DocumentMetadata, newName: String): Result<Unit> {
        renameRequests += document.id to newName
        return Result.success(Unit)
    }

    override suspend fun getDocumentContent(document: DocumentMetadata): Result<SecureDocumentContent> {
        return documentContentResult
    }

    override suspend fun reprocessDocuments(): Result<String> {
        return Result.success("Reprocessing started")
    }

    fun setDocuments(items: List<DocumentMetadata>) {
        documents.value = items
    }

    data class UploadRequest(
        val uid: String,
        val fileName: String,
        val userTag: String,
        val processingMode: DocumentProcessingMode
    )
}

class FakeUserSessionProvider(initialUserId: String? = "user-123") : UserSessionProvider {
    var userId: String? = initialUserId

    override val currentUserId: String?
        get() = userId
}
