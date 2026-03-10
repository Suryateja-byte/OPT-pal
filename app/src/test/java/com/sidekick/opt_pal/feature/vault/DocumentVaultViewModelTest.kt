package com.sidekick.opt_pal.feature.vault

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import com.sidekick.opt_pal.core.documents.SecureDocumentIntakeUseCase
import com.sidekick.opt_pal.data.model.DocumentMetadata
import com.sidekick.opt_pal.data.model.DocumentProcessingMode
import com.sidekick.opt_pal.data.model.DocumentUploadConsent
import com.sidekick.opt_pal.testing.fakes.FakeDocumentRepository
import com.sidekick.opt_pal.testing.fakes.FakeUserSessionProvider
import com.sidekick.opt_pal.testing.rules.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DocumentVaultViewModelTest {

    @get:Rule
    val dispatcherRule = MainDispatcherRule()

    private val userProvider = FakeUserSessionProvider("user-1")
    private val repository = FakeDocumentRepository()
    private val resolver: ContentResolver = mockk(relaxed = true)

    @Test
    fun observesDocumentsFromRepository() = runTest {
        val viewModel = DocumentVaultViewModel(repository, userProvider, intake())
        repository.setDocuments(
            listOf(
                DocumentMetadata(id = "doc-1", fileName = "EAD.pdf", userTag = "EAD Card")
            )
        )

        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.documents.size)
        assertEquals("doc-1", viewModel.uiState.value.documents.first().id)
    }

    @Test
    fun confirmUploadInvokesRepositoryWithResolvedName() = runTest {
        val viewModel = DocumentVaultViewModel(repository, userProvider, intake(FakeFileNameResolver("Offer.pdf")))
        val uri = mockk<Uri>()
        mockMimeAndSize(uri, mimeType = "application/pdf", sizeBytes = 2_000L)
        repository.holdUploadCompletion = true

        viewModel.onFileSelected(uri)
        viewModel.confirmUpload(
            tag = "Offer Letter",
            consent = DocumentUploadConsent(DocumentProcessingMode.ANALYZE),
            contentResolver = resolver
        )
        runCurrent()
        assertTrue(viewModel.uiState.value.uploadsInProgress.isNotEmpty())
        repository.uploadCompletion?.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            FakeDocumentRepository.UploadRequest(
                uid = "user-1",
                fileName = "Offer.pdf",
                userTag = "Offer Letter",
                processingMode = DocumentProcessingMode.ANALYZE
            ),
            repository.uploadRequests.single()
        )
        assertTrue(viewModel.uiState.value.uploadError == null)
    }

    @Test
    fun deleteDocumentPassesThrough() = runTest {
        val viewModel = DocumentVaultViewModel(repository, userProvider, intake())
        val metadata = DocumentMetadata(id = "doc-9", fileName = "file.pdf", userTag = "test")

        viewModel.deleteDocument(metadata)
        advanceUntilIdle()

        assertTrue(repository.deleteRequests.contains(metadata))
    }

    @Test
    fun blocksUnsupportedMimeType() = runTest {
        val viewModel = DocumentVaultViewModel(repository, userProvider, intake())
        val uri = mockk<Uri>()
        mockMimeAndSize(uri, mimeType = "text/plain", sizeBytes = 1_000L)

        viewModel.onFileSelected(uri)
        viewModel.confirmUpload(
            tag = "Notes",
            consent = DocumentUploadConsent(DocumentProcessingMode.STORAGE_ONLY),
            contentResolver = resolver
        )

        assertEquals("Only PDF or image files are supported.", viewModel.uiState.value.uploadError)
        assertTrue(repository.uploadRequests.isEmpty())
    }

    @Test
    fun blocksLargeFiles() = runTest {
        val viewModel = DocumentVaultViewModel(repository, userProvider, intake())
        val uri = mockk<Uri>()
        mockMimeAndSize(uri, mimeType = "application/pdf", sizeBytes = 11 * 1024 * 1024L)

        viewModel.onFileSelected(uri)
        viewModel.confirmUpload(
            tag = "Huge",
            consent = DocumentUploadConsent(DocumentProcessingMode.STORAGE_ONLY),
            contentResolver = resolver
        )

        assertEquals("File is larger than 10 MB.", viewModel.uiState.value.uploadError)
        assertTrue(repository.uploadRequests.isEmpty())
    }

    @Test
    fun updatesProgressDuringUpload() = runTest {
        repository.progressEmissions = listOf(512L to 1_024L)
        repository.holdUploadCompletion = true
        val viewModel = DocumentVaultViewModel(repository, userProvider, intake(FakeFileNameResolver("Proof.pdf")))
        val uri = mockk<Uri>()
        mockMimeAndSize(uri, mimeType = "application/pdf", sizeBytes = 2_000L)

        viewModel.onFileSelected(uri)
        viewModel.confirmUpload(
            tag = "Proof",
            consent = DocumentUploadConsent(DocumentProcessingMode.ANALYZE),
            contentResolver = resolver
        )
        runCurrent()
        val inProgress = viewModel.uiState.value.uploadsInProgress
        assertTrue(inProgress.isNotEmpty())
        assertEquals(50, inProgress.first().progress)
        repository.uploadCompletion?.complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.uploadsInProgress.isEmpty())
    }

    private fun intake(fileNameResolver: FileNameResolver = FakeFileNameResolver()): SecureDocumentIntakeUseCase {
        return SecureDocumentIntakeUseCase(repository, userProvider, fileNameResolver)
    }

    private fun mockMimeAndSize(uri: Uri, mimeType: String, sizeBytes: Long) {
        every { resolver.getType(uri) } returns mimeType
        val cursor = mockk<Cursor>()
        every { cursor.getColumnIndex(OpenableColumns.SIZE) } returns 0
        every { cursor.moveToFirst() } returns true
        every { cursor.getLong(0) } returns sizeBytes
        every { cursor.close() } returns Unit
        every { resolver.query(uri, any(), null, null, null) } returns cursor
    }

    private class FakeFileNameResolver(private val name: String = "test.pdf") : FileNameResolver {
        override fun resolve(uri: Uri, contentResolver: ContentResolver): String = name
    }
}
